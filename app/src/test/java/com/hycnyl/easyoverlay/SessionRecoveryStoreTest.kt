package com.hycnyl.easyoverlay

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hycnyl.easyoverlay.data.SessionRecoveryStore
import com.hycnyl.easyoverlay.domain.RestoreDecision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class SessionRecoveryStoreTest {
    @Test fun simultaneousRestartTriggersConsumeOneBudgetSlotPerProcess()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val first=SessionRecoveryStore(context);assertFalse(first.autoWanted)
        first.userRequested(true)
        assertEquals(RestoreDecision.RESTORE,first.beginRestore())
        assertEquals(RestoreDecision.RESTORE,first.beginRestore())
        assertEquals(1,context.getSharedPreferences("automatic_session_v2",0).getInt("restore_attempts",0))
        assertEquals(RestoreDecision.RESTORE,SessionRecoveryStore(context).beginRestore())
        assertEquals(RestoreDecision.RESTORE,SessionRecoveryStore(context).beginRestore())
        assertEquals(RestoreDecision.RESTART_BACKOFF,SessionRecoveryStore(context).beginRestore())
        first.userRequested(false)
        assertEquals(RestoreDecision.NOT_REQUESTED,first.beginRestore())
        assertFalse(first.autoWanted)
    }
}
