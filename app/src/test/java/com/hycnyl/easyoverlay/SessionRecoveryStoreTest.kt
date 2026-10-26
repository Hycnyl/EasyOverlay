package com.hycnyl.easyoverlay

import android.app.Application
import android.app.ActivityManager
import android.app.ApplicationExitInfo
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class SessionRecoveryStoreTest {
    @Test fun laterSystemExitCannotEraseAnEarlierUserStopAndExplicitStartRearmsRecovery()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val store=SessionRecoveryStore(context);store.userRequested(true)
        val now=System.currentTimeMillis()
        context.getSharedPreferences("automatic_session_v2",0).edit().putLong("user_start_at",now-10000).commit()
        val history=shadowOf(context.getSystemService(ActivityManager::class.java))
        history.addApplicationExitInfo(ApplicationExitInfoBuilder.newBuilder().setProcessName(context.packageName)
            .setTimestamp(now-5000).setReason(ApplicationExitInfo.REASON_USER_REQUESTED).build())
        history.addApplicationExitInfo(ApplicationExitInfoBuilder.newBuilder().setProcessName(context.packageName)
            .setTimestamp(now-1000).setReason(ApplicationExitInfo.REASON_PACKAGE_UPDATED).build())
        assertEquals(RestoreDecision.USER_STOPPED,store.beginRestore());assertFalse(store.autoWanted)
        store.userRequested(true)
        assertEquals(RestoreDecision.RESTORE,store.beginRestore())
    }
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
