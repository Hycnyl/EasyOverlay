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
    val autoWanted: Boolean get() = preferences.getBoolean("wanted", false)

    suspend fun userRequested(enabled: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val edit = preferences.edit().putBoolean("wanted", enabled)
                .putLong(if (enabled) "user_start_at" else "user_stop_at", System.currentTimeMillis())
                .putInt("restore_attempts", 0).putLong("restore_window", 0)
            if (!edit.commit()) throw IOException("SESSION_INTENT_SAVE_FAILED")
        }
    }

    suspend fun beginRestore(): RestoreDecision = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val latest = runCatching {
                context.getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 8)
                    .filter { it.processName == context.packageName }.maxByOrNull { it.timestamp }
            }.getOrNull()
            val userExit = latest?.reason == ApplicationExitInfo.REASON_USER_REQUESTED ||
                latest?.reason == ApplicationExitInfo.REASON_USER_STOPPED
            val window = preferences.getLong("restore_window", 0)
            val attempts = preferences.getInt("restore_attempts", 0)
            val decision = SessionRestorePolicy.decide(autoWanted,
                preferences.getLong("user_start_at", 0), latest?.timestamp ?: -1, userExit,
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
            decision
        }
    }
}
