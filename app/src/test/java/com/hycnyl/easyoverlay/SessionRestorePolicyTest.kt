package com.hycnyl.easyoverlay

import com.hycnyl.easyoverlay.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionRestorePolicyTest {
    @Test fun systemReclaimCanResumeButNoOptInCannot() {
        assertEquals(RestoreDecision.RESTORE, SessionRestorePolicy.decide(true, 10, 20, false, 0, 0, 30))
        assertEquals(RestoreDecision.NOT_REQUESTED, SessionRestorePolicy.decide(false, 10, 20, false, 0, 0, 30))
    }
    @Test fun userStopIsRespectedUntilANewExplicitStart() {
        assertEquals(RestoreDecision.USER_STOPPED, SessionRestorePolicy.decide(true, 10, 20, true, 0, 0, 30))
        assertEquals(RestoreDecision.RESTORE, SessionRestorePolicy.decide(true, 25, 20, true, 0, 0, 30))
    }
    @Test fun restartBudgetPreventsCrashLoopAndResetsAfterWindow() {
        assertEquals(RestoreDecision.RESTART_BACKOFF, SessionRestorePolicy.decide(true, 10, 20, false, 100, 3, 1000))
        assertEquals(RestoreDecision.RESTORE, SessionRestorePolicy.decide(true, 10, 20, false, 100, 3, 700000))
    }
}
