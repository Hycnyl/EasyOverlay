package com.hycnyl.easyoverlay.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.annotation.SuppressLint
import android.content.Context
import com.hycnyl.easyoverlay.domain.RestoreDecision
import com.hycnyl.easyoverlay.domain.SessionRestorePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Durable user intent, never a claim that an overlay window still exists. */
@SuppressLint("ApplySharedPref") // Intent must reach disk before service delivery; every commit runs on Dispatchers.IO.
class SessionRecoveryStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("automatic_session_v2", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var processRestoreDecision: RestoreDecision? = null
    val autoWanted: Boolean get() = preferences.getBoolean("wanted", false)

    private fun mostRecentUserStop(): ApplicationExitInfo? = runCatching {
        context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName,0,32)
            .filter {it.processName==context.packageName && (it.reason==ApplicationExitInfo.REASON_USER_REQUESTED || it.reason==ApplicationExitInfo.REASON_USER_STOPPED)}
            .maxByOrNull {it.timestamp}
    }.getOrNull()

    /** A provider-only wake must also persist an earlier user stop before a later system exit hides it. */
    suspend fun honorUserStop(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if(!autoWanted)return@withLock false
            val stopped=mostRecentUserStop() ?: return@withLock false
            if(stopped.timestamp<preferences.getLong("user_start_at",0))return@withLock false
            preferences.edit().putBoolean("wanted",false).commit()
            processRestoreDecision=RestoreDecision.USER_STOPPED
            true
        }
    }

    suspend fun userRequested(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            processRestoreDecision = null
            val edit = preferences.edit().putBoolean("wanted", enabled)
                .putLong(if (enabled) "user_start_at" else "user_stop_at", System.currentTimeMillis())
                .putInt("restore_attempts", 0).putLong("restore_window", 0)
            if (!edit.commit()) throw IOException("SESSION_INTENT_SAVE_FAILED")
        }
    }

    suspend fun beginRestore(): RestoreDecision = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Sticky restart and accessibility reconnect can arrive together in one new process.
            // Count that as one recovery start, not two independent crashes.
            processRestoreDecision?.let { return@withLock it }
            val now = System.currentTimeMillis()
            val stopped = mostRecentUserStop()
            val window = preferences.getLong("restore_window", 0)
            val attempts = preferences.getInt("restore_attempts", 0)
            val decision = SessionRestorePolicy.decide(autoWanted,
                preferences.getLong("user_start_at", 0), stopped?.timestamp ?: -1, stopped!=null,
                window, attempts, now)
            when (decision) {
                RestoreDecision.USER_STOPPED -> preferences.edit().putBoolean("wanted", false).commit()
                RestoreDecision.RESTORE -> {
                    val inWindow = now >= window && now - window < SessionRestorePolicy.WINDOW_MS
                    val saved = preferences.edit().putLong("restore_window", if (inWindow) window else now)
                        .putInt("restore_attempts", if (inWindow) attempts + 1 else 1).commit()
                    if (!saved) return@withLock RestoreDecision.NOT_REQUESTED
                }
                else -> Unit
            }
            decision.also { processRestoreDecision = it }
        }
    }
}
