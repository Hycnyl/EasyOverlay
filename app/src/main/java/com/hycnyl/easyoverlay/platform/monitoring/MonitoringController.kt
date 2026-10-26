package com.hycnyl.easyoverlay.platform.monitoring

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.hycnyl.easyoverlay.platform.Permissions
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Owns provider arbitration, not overlay ownership. Only this controller emits effective foreground observations. */
class MonitoringController(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private data class Probe(val available: () -> Boolean, val snapshot: suspend () -> PrivilegedSnapshot)
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val gate = ObservationGate()
    private val session = AtomicLong()
    private val _availability = MutableStateFlow(MonitoringAvailability())
    val availability = _availability.asStateFlow()
    private val _state = MutableStateFlow(MonitoringState())
    val state = _state.asStateFlow()
    @Volatile private var mode = MonitoringMode.AUTO
    @Volatile private var shizukuProbe: Probe? = null
    @Volatile private var accessibilitySnapshot: SourceSnapshot? = null
    @Volatile private var shizukuSnapshot: SourceSnapshot? = null
    @Volatile private var usageSnapshot: SourceSnapshot? = null
    @Volatile private var callback: ((String?, Long, String?) -> Unit)? = null
    private val accessibility = AccessibilityForegroundTracker(this.context, scope,
        { snapshot -> accessibilitySnapshot = snapshot; signals.trySend(Unit) },
        { accessibilitySnapshot = null; accessibilitySamplingSince = 0L; refreshAvailability(); signals.trySend(Unit) })
    private val usage = UsageForegroundTracker(this.context)
    private var monitor: Job? = null
    private var usageJob: Job? = null
    private var privilegedJob: Job? = null
    private var lastUsageStart = Long.MIN_VALUE
    @Volatile private var lastPrivilegedStart = Long.MIN_VALUE
    private var lastPermissionCheck = Long.MIN_VALUE
    @Volatile private var accessibilitySamplingSince = 0L

    init { refreshAvailability() }

    fun accessibilityConnected(service: AccessibilityService) { accessibility.onConnected(service) }
    fun accessibilityDisconnected(service: AccessibilityService) { accessibility.onDisconnected(service) }
    fun accessibilityEvent(service: AccessibilityService, event: AccessibilityEvent) { accessibility.onEvent(service, event) }

    @Synchronized fun setMode(value: MonitoringMode) {
        if (value == mode) return
        mode = value
        gate.newEpoch(SystemClock.elapsedRealtime())
        accessibilitySnapshot = null; shizukuSnapshot = null; usageSnapshot = null
        usageJob?.cancel(); privilegedJob?.cancel()
        lastUsageStart = Long.MIN_VALUE; lastPrivilegedStart = Long.MIN_VALUE
        _state.value = _state.value.copy(requestedMode = value)
        accessibility.requestSnapshot()
        signals.trySend(Unit)
    }

    @Synchronized fun setShizukuProbe(available: () -> Boolean, snapshot: suspend () -> PrivilegedSnapshot) {
        shizukuProbe = Probe(available, snapshot)
        privilegedJob?.cancel()
        shizukuSnapshot = null; lastPrivilegedStart = Long.MIN_VALUE
        refreshAvailability(); signals.trySend(Unit)
    }

    @Synchronized fun clearShizukuProbe() {
        shizukuProbe = null; shizukuSnapshot = null; privilegedJob?.cancel()
        refreshAvailability(); signals.trySend(Unit)
    }

    /** Wake arbitration after an externally polled adapter updates its latest snapshot. */
    fun notifyPrivilegedChanged() {
        lastPrivilegedStart = Long.MIN_VALUE
        signals.trySend(Unit)
    }

    fun refreshAvailability(): MonitoringAvailability {
        val current = MonitoringAvailability(accessibility.connected,
            runCatching { shizukuProbe?.available?.invoke() == true }.getOrDefault(false), Permissions.usage(context))
        _availability.value = current
        return current
    }

    /** Authorization/capability gate for enabling auto mode; Usage Access is not mandatory for enhanced providers. */
    fun canMonitor(): Boolean = MonitoringRouter.select(mode, refreshAvailability()).source != MonitoringSource.NONE

    @Synchronized fun start(onObservation: (String?, Long, String?) -> Unit) {
        callback = onObservation
        if (monitor?.isActive == true) { signals.trySend(Unit); return }
        gate.newEpoch(SystemClock.elapsedRealtime())
        accessibilitySnapshot = null; shizukuSnapshot = null; usageSnapshot = null
        lastUsageStart = Long.MIN_VALUE; lastPrivilegedStart = Long.MIN_VALUE
        usage.recalibrate()
        val expectedSession = session.incrementAndGet()
        monitor = scope.launch(Dispatchers.Default) {
            var previous: MonitoringState? = null
            var previouslyLocked = false
            while (isActive && expectedSession == session.get()) {
                val requestedMode = mode
                val nowElapsed = SystemClock.elapsedRealtime()
                if (lastPermissionCheck == Long.MIN_VALUE || nowElapsed - lastPermissionCheck >= 1000) {
                    refreshAvailability(); lastPermissionCheck = nowElapsed
                }
                val permitted = _availability.value
                val priorities = MonitoringRouter.priority(requestedMode)
                if (MonitoringSource.ACCESSIBILITY in priorities && permitted.accessibilityConnected) {
                    if (accessibilitySamplingSince == 0L) accessibilitySamplingSince = nowElapsed
                    accessibility.start()
                } else { accessibility.stop(); accessibilitySamplingSince = 0L }
                val locked = !ScreenAvailability.unlocked(context)
                if (locked) {
                    if (!previouslyLocked) {
                        usage.recalibrate(); usageJob?.cancel(); privilegedJob?.cancel()
                        gate.newEpoch(nowElapsed)
                        usageSnapshot = null; shizukuSnapshot = null
                    }
                } else if (previouslyLocked) {
                    gate.newEpoch(nowElapsed)
                    usage.recalibrate(); usageSnapshot = null; accessibilitySnapshot = null; shizukuSnapshot = null
                    lastUsageStart = Long.MIN_VALUE; lastPrivilegedStart = Long.MIN_VALUE
                    accessibility.requestSnapshot()
                }
                previouslyLocked = locked

                val epoch = gate.currentEpoch()
                val unhealthy = buildSet {
                    if (permitted.accessibilityConnected && accessibilitySnapshot == null &&
                        accessibilitySamplingSince > 0 && nowElapsed - accessibilitySamplingSince > 3000)
                        add(MonitoringSource.ACCESSIBILITY)
                    accessibilitySnapshot?.let {
                        if (!gate.canAccept(epoch, it.capturedAtElapsed) || nowElapsed - it.capturedAtElapsed > 3000 ||
                            it.problem in setOf("ACCESSIBILITY_QUERY_FAILED", "ACCESSIBILITY_WINDOW_ACCESS_DENIED", "ACCESSIBILITY_NO_FOREGROUND"))
                            add(MonitoringSource.ACCESSIBILITY)
                    }
                    shizukuSnapshot?.let { if (it.problem != null || !gate.canAccept(epoch, it.capturedAtElapsed) || nowElapsed - it.capturedAtElapsed > 3000) add(MonitoringSource.SHIZUKU) }
                    usageSnapshot?.let { if (it.problem != null || !gate.canAccept(epoch, it.capturedAtElapsed)) add(MonitoringSource.USAGE) }
                }
                val decision = MonitoringRouter.select(requestedMode, permitted, unhealthy)
                if (!locked) {
                    val healthySourceBeforeUsage = priorities.takeWhile { it != MonitoringSource.USAGE }
                        .any { permitted.available(it) && it !in unhealthy }
                    if (MonitoringSource.USAGE in priorities && permitted.usageGranted &&
                        (decision.source == MonitoringSource.USAGE || !healthySourceBeforeUsage)) sampleUsage(nowElapsed, epoch, expectedSession)
                    // A failed privileged source is retried without replacing a healthy fallback until its query succeeds.
                    if (MonitoringSource.SHIZUKU in priorities && permitted.shizukuAvailable &&
                        (decision.source == MonitoringSource.SHIZUKU || MonitoringSource.ACCESSIBILITY !in priorities || !permitted.accessibilityConnected || MonitoringSource.ACCESSIBILITY in unhealthy)) {
                        samplePrivileged(nowElapsed, if (decision.source == MonitoringSource.SHIZUKU) 500 else 2000, epoch, expectedSession)
                    }
                }

                val snapshot = when (decision.source) {
                    MonitoringSource.ACCESSIBILITY -> accessibilitySnapshot
                    MonitoringSource.SHIZUKU -> shizukuSnapshot
                    MonitoringSource.USAGE -> usageSnapshot
                    MonitoringSource.NONE -> null
                }
                val effective = snapshot?.takeIf { !locked && gate.accept(epoch, it.capturedAtElapsed) }
                val problem = when {
                    locked -> "SCREEN_LOCKED"
                    decision.source == MonitoringSource.NONE -> when (requestedMode) {
                        MonitoringMode.ACCESSIBILITY -> "ACCESSIBILITY_NOT_CONNECTED"
                        MonitoringMode.SHIZUKU -> "SHIZUKU_UNAVAILABLE"
                        MonitoringMode.USAGE -> "USAGE_PERMISSION_MISSING"
                        MonitoringMode.AUTO -> "MONITOR_PERMISSION_MISSING"
                    }
                    effective == null -> "WAITING_FOREGROUND_OBSERVATION"
                    else -> effective.problem
                }
                val observationAt = effective?.observedAtMillis ?: previous?.takeIf {
                    it.selectedSource == decision.source && it.problem == problem
                }?.lastEventAt ?: System.currentTimeMillis()
                val next = MonitoringState(requestedMode, decision.source, true, effective?.packageName,
                    observationAt, effective?.packagesByDisplay.orEmpty(), problem, decision.fallbackReason)
                synchronized(this@MonitoringController) {
                    if (isActive && expectedSession == session.get() && epoch == gate.currentEpoch() && requestedMode == mode) {
                        _state.value = next
                        if (next != previous) {
                            previous = next
                            callback?.invoke(next.lastPackage, next.lastEventAt, next.problem)
                        }
                    }
                }
                withTimeoutOrNull(250) { signals.receive() }
            }
        }
    }

    private fun sampleUsage(now: Long, epoch: Long, expectedSession: Long) {
        if (usageJob?.isActive == true || (lastUsageStart != Long.MIN_VALUE && now - lastUsageStart < 400)) return
        lastUsageStart = now
        usageJob = scope.launch(Dispatchers.IO) {
            if (expectedSession != session.get() || epoch != gate.currentEpoch()) return@launch
            val result = try { usage.sample() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: SecurityException) { SourceSnapshot(null, System.currentTimeMillis(), now, problem = "USAGE_PERMISSION_MISSING") }
            catch (_: Exception) { SourceSnapshot(null, System.currentTimeMillis(), now, problem = "USAGE_QUERY_FAILED") }
            if (isActive && expectedSession == session.get() && gate.isCurrent(epoch, result.capturedAtElapsed)) { usageSnapshot = result; signals.trySend(Unit) }
        }
    }

    private fun samplePrivileged(now: Long, interval: Long, epoch: Long, expectedSession: Long) {
        if (privilegedJob?.isActive == true || (lastPrivilegedStart != Long.MIN_VALUE && now - lastPrivilegedStart < interval)) return
        val probe = shizukuProbe ?: return
        lastPrivilegedStart = now
        privilegedJob = scope.launch(Dispatchers.IO) {
            if (expectedSession != session.get() || epoch != gate.currentEpoch()) return@launch
            val result = try {
                val snapshot = withTimeout(1500) { probe.snapshot() }
                val stale = !PrivilegedSnapshotFreshness.isFresh(snapshot.observedAtMillis, System.currentTimeMillis())
                SourceSnapshot(snapshot.packageName, snapshot.observedAtMillis, now, snapshot.packagesByDisplay,
                    snapshot.problem ?: if (stale) "SHIZUKU_STALE_OBSERVATION" else if (snapshot.packageName == null) "SHIZUKU_NO_FOREGROUND" else null)
            } catch (_: TimeoutCancellationException) { SourceSnapshot(null, System.currentTimeMillis(), now, problem = "SHIZUKU_QUERY_TIMEOUT") }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { SourceSnapshot(null, System.currentTimeMillis(), now, problem = "SHIZUKU_QUERY_FAILED") }
            if (isActive && expectedSession == session.get() && shizukuProbe === probe && gate.isCurrent(epoch, result.capturedAtElapsed)) {
                shizukuSnapshot = result; signals.trySend(Unit)
            }
        }
    }

    @Synchronized fun stop() {
        session.incrementAndGet()
        monitor?.cancel(); monitor = null
        usageJob?.cancel(); privilegedJob?.cancel(); accessibility.stop()
        gate.newEpoch(SystemClock.elapsedRealtime())
        callback = null; accessibilitySnapshot = null; shizukuSnapshot = null; usageSnapshot = null
        accessibilitySamplingSince = 0L
        _state.value = MonitoringState(requestedMode = mode)
    }
}
