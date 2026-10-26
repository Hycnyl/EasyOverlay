package com.hycnyl.easyoverlay.platform.monitoring

import com.hycnyl.easyoverlay.domain.ForegroundEvent
import com.hycnyl.easyoverlay.domain.ForegroundKind
import com.hycnyl.easyoverlay.domain.ForegroundReducer

/**
 * UsageEvents describes activity lifecycle, not input focus. Its best available fallback is the
 * most recently resumed application. A pause may mean split screen, an IME/system window or a
 * lock-screen transition, so it cannot establish that a different application is foreground.
 * The controller independently suppresses every result while the device is locked.
 */
internal class UsageResumeHistory {
    private val reducer = ForegroundReducer()
    val lastObservationAt: Long get() = reducer.lastObservationAt

    fun accept(events: List<ForegroundEvent>, now: Long): String? =
        reducer.accept(events.filter { it.kind == ForegroundKind.RESUMED && it.packageName.isNotBlank() }, now)
}
