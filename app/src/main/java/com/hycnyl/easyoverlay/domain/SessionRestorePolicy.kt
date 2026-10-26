package com.hycnyl.easyoverlay.domain

enum class RestoreDecision { RESTORE, NOT_REQUESTED, USER_STOPPED, RESTART_BACKOFF }

object SessionRestorePolicy {
    const val WINDOW_MS = 10 * 60 * 1000L
    const val MAX_RESTORES = 3
    fun decide(wanted: Boolean, userStartAt: Long, lastExitAt: Long, userRequestedExit: Boolean,
        windowStart: Long, attempts: Int, now: Long): RestoreDecision = when {
        !wanted -> RestoreDecision.NOT_REQUESTED
        userRequestedExit && lastExitAt >= userStartAt -> RestoreDecision.USER_STOPPED
        now >= windowStart && now - windowStart < WINDOW_MS && attempts >= MAX_RESTORES -> RestoreDecision.RESTART_BACKOFF
        else -> RestoreDecision.RESTORE
    }
}
