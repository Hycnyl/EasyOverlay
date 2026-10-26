package com.hycnyl.easyoverlay.platform.monitoring

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong

internal object ScreenAvailability {
    fun unlocked(context: Context): Boolean = runCatching {
        context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
            context.getSystemService(UserManager::class.java).isUserUnlocked
    }.getOrDefault(false)
}

/** Reads only window type/focus and root packageName. No text, node trees or event contents are retained. */
internal class AccessibilityForegroundTracker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val publish: (SourceSnapshot) -> Unit,
    private val connectionChanged: () -> Unit
) {
    private data class Hint(val windowId: Int, val packageName: String, val atElapsed: Long)
    @Volatile private var service = WeakReference<AccessibilityService>(null)
    @Volatile private var hint: Hint? = null
    private val signals = Channel<Unit>(Channel.CONFLATED)
    private val generation = AtomicLong()
    private var reader: Job? = null
    val connected: Boolean get() = service.get() != null

    fun onConnected(value: AccessibilityService) {
        service = WeakReference(value)
        hint = null
        generation.incrementAndGet()
        reader?.cancel(); reader = null
        connectionChanged()
        signals.trySend(Unit)
    }

    fun onDisconnected(value: AccessibilityService) {
        if (service.get() !== value) return
        service.clear(); hint = null; generation.incrementAndGet()
        reader?.cancel(); reader = null
        connectionChanged()
        signals.trySend(Unit)
    }

    fun onEvent(value: AccessibilityService, event: AccessibilityEvent) {
        if (service.get() !== value) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.takeIf { it.isNotBlank() }?.let {
                hint = Hint(event.windowId, it, SystemClock.elapsedRealtime())
            }
        }
        signals.trySend(Unit)
    }

    fun requestSnapshot() { signals.trySend(Unit) }

    fun start() {
        if (reader?.isActive == true) return
        val expected = generation.get()
        reader = scope.launch(Dispatchers.IO) {
            var previous = emptyMap<Int, String>()
            while (isActive && expected == generation.get()) {
                val current = service.get() ?: break
                val captured = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val result = if (!ScreenAvailability.unlocked(context)) {
                    previous = emptyMap()
                    SourceSnapshot(null, wall, captured, problem = "SCREEN_LOCKED")
                } else try {
                    val windows = current.windowsOnAllDisplays
                    val candidates = mutableListOf<ForegroundWindow>()
                    val eventHint = hint?.takeIf { captured - it.atElapsed in 0..1500 }
                    for (index in 0 until windows.size()) {
                        val displayId = windows.keyAt(index)
                        for (window in windows.valueAt(index).orEmpty()) {
                            val type = window.type
                            val pkg = if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                // API 33+ overload avoids expensive descendant prefetching.
                                window.getRoot(0)?.packageName?.toString()
                                    ?: eventHint?.takeIf { it.windowId == window.id }?.packageName
                            } else null
                            candidates += ForegroundWindow(displayId, window.id, type,
                                window.isFocused, window.isActive, window.layer, pkg)
                        }
                    }
                    val selected = AccessibilityWindowSelector.select(candidates, previous)
                    if (selected.isNotEmpty()) previous = selected
                    SourceSnapshot(selected[0], wall, captured, selected,
                        if (selected[0] == null) "ACCESSIBILITY_NO_FOREGROUND" else null)
                } catch (_: SecurityException) {
                    SourceSnapshot(null, wall, captured, problem = "ACCESSIBILITY_WINDOW_ACCESS_DENIED")
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) {
                    SourceSnapshot(null, wall, captured, problem = "ACCESSIBILITY_QUERY_FAILED")
                }
                if (isActive && expected == generation.get() && service.get() === current) publish(result)
                // Events wake this single reader immediately; timeout supplies calibration for missed OEM events.
                val signalled = withTimeoutOrNull(1000) { signals.receive() }
                if (signalled != null) {
                    delay(60)
                    while (signals.tryReceive().isSuccess) { /* coalesce bursts */ }
                }
            }
        }
    }

    fun stop() { generation.incrementAndGet(); reader?.cancel(); reader = null }
}
