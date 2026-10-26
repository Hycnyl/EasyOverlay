package com.hycnyl.easyoverlay.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SessionRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_USER_UNLOCKED)) {
            val pending = goAsync()
            val job = OverlaySessionService.restoreIfWanted(context, "SYSTEM_EVENT")
            if (job == null) pending.finish() else job.invokeOnCompletion { pending.finish() }
        }
    }
}
