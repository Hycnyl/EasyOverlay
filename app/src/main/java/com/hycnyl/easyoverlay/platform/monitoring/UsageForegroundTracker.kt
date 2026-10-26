package com.hycnyl.easyoverlay.platform.monitoring

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import com.hycnyl.easyoverlay.domain.ForegroundEvent
import com.hycnyl.easyoverlay.domain.ForegroundKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** Lower-privilege fallback. It reconstructs history after startup/unlock, rather than assuming a 2s resume event. */
internal class UsageForegroundTracker(context: Context) {
    private val manager = context.getSystemService(UsageStatsManager::class.java)
    private var history = UsageResumeHistory()
    private var cursor: Long? = null
    private val recalibration = AtomicLong(1)
    private var completedRecalibration = 0L
    private val lock = Mutex()

    fun recalibrate() { recalibration.incrementAndGet() }

    suspend fun sample(): SourceSnapshot = lock.withLock {
        val now = System.currentTimeMillis()
        val captured = SystemClock.elapsedRealtime()
        val requestedRecalibration = recalibration.get()
        val reset = requestedRecalibration != completedRecalibration || (cursor ?: 0) > now
        if (reset) history = UsageResumeHistory()
        val start = UsageQueryWindow.start(now, cursor, reset)
        val events = manager.queryEvents(start, now)
            ?: return@withLock SourceSnapshot(null, now, captured, problem = "USAGE_DATA_UNAVAILABLE")
        val event = UsageEvents.Event()
        val batch = mutableListOf<ForegroundEvent>()
        var count = 0
        while (events.hasNextEvent()) {
            if (++count % 128 == 0) currentCoroutineContext().ensureActive()
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundKind.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundKind.SCREEN_OFF
                else -> continue
            }
            batch += ForegroundEvent(event.timeStamp, event.packageName.orEmpty(), event.className.orEmpty(), kind)
        }
        currentCoroutineContext().ensureActive()
        cursor = now; completedRecalibration = requestedRecalibration
        val current = history.accept(batch, now)
        SourceSnapshot(current, history.lastObservationAt.takeIf { it > 0 } ?: now, captured,
            problem = if (current == null) "USAGE_NO_FOREGROUND_EVENT" else null)
    }
}
