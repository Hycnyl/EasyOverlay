package com.hycnyl.easyoverlay.platform

import android.app.KeyguardManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.*

/** Single observation source. No background-exit callback is allowed to stop a window. */
class ForegroundAppObserver(private val context: Context, private val scope: CoroutineScope,
    private val onObservation: (String?, Long, String?) -> Unit) {
    private var job: Job? = null
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) {
            val reducer = ForegroundReducer()
            val usage = context.getSystemService(UsageStatsManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            var cursor = System.currentTimeMillis() - 30_000
            var previous: String? = null
            var lastReportedAt = 0L
            var locked = false
            while (isActive) {
                val now = System.currentTimeMillis()
                try {
                    if (!Permissions.usage(context)) {
                        reducer.reset(); onObservation(null, now, "USAGE_PERMISSION_MISSING")
                        delay(1000); continue
                    }
                    if (!power.isInteractive || keyguard.isKeyguardLocked) {
                        reducer.reset(); cursor = now; locked = true
                        onObservation(null, now, "SCREEN_LOCKED")
                        delay(500); continue
                    }
                    if (locked) { locked = false; cursor = now - 2000; reducer.reset() }
                    if (cursor > now) { cursor = now - 2000; reducer.reset() }
                    val events = usage.queryEvents((cursor - 1500).coerceAtLeast(now - 30_000), now)
                    val event = UsageEvents.Event()
                    val batch = mutableListOf<ForegroundEvent>()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val kind = when (event.eventType) {
                            UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundKind.RESUMED
                            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundKind.PAUSED
                            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundKind.SCREEN_OFF
                            else -> null
                        } ?: continue
                        batch += ForegroundEvent(event.timeStamp, event.packageName.orEmpty(), event.className.orEmpty(), kind)
                    }
                    cursor = now
                    val current = reducer.accept(batch, now)
                    if (current != previous || now - lastReportedAt >= 2000) {
                        previous = current; lastReportedAt = now
                        onObservation(current, reducer.lastObservationAt, null)
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { reducer.reset(); onObservation(null, now, "USAGE_QUERY_FAILED") }
                delay(350)
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}
