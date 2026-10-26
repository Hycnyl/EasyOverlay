package com.hycnyl.easyoverlay.platform

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hycnyl.easyoverlay.*
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One visible session; only explicitly enabled automatic monitoring has durable restart intent. */
class OverlaySessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ready = CompletableDeferred<Unit>()
    private lateinit var container: AppContainer
    private var coordinator: OverlayCoordinator? = null
    private var observer: ForegroundAppObserver? = null
    private var engine: WindowOverlayEngine? = null
    private var control: FloatingControl? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as EasyOverlayApplication).container
        live = this
        try { ensureForeground() }
        catch (_: Exception) { container.events.record("FOREGROUND_START_FAILED"); stopSelf(); return }
        container.runtime.value = RuntimeState(serviceRunning = true)
        container.events.record("SERVICE_STARTED")
        scope.launch {
            container.library.ready.first { it }
            if (container.library.problem.value != null) {
                container.messages.tryEmit(container.library.problem.value.orEmpty()); stopSelf(); return@launch
            }
            val host = WindowOverlayEngine(this@OverlaySessionService, container.library) { container.settings.value.coverCutout }
            engine = host
            observer = ForegroundAppObserver(this@OverlaySessionService, scope, container.monitoring) { pkg, time, problem ->
                coordinator?.offer(OverlayCommand.Observe(pkg, time, problem))
            }
            control = FloatingControl(this@OverlaySessionService,
                { request(this@OverlaySessionService, TOGGLE) }, { request(this@OverlaySessionService, STOP_ALL) })
            coordinator = OverlayCoordinator(scope, container.library.state, host, container.runtime, packageName,
                { host.maximumAlpha() }, { container.overlayCapabilities.canOverlay() }, container.monitoring::canMonitor,
                { enabled -> if (enabled) observer?.start() else { observer?.stop(); container.privileged.stopForegroundObservation() } },
                { enabled -> control?.show(enabled) ?: false },
                { code, display -> container.events.record(code, display) }, { stopSelf() },
                host::maximumAlpha, container.overlayCapabilities::canOverlay,
                { container.monitoring.state.value.selectedSource.name },
                { container.monitoring.state.value.problem ?: "MONITOR_PERMISSION_MISSING" })
            ready.complete(Unit)
            launch { container.displays.displays.collect { coordinator?.offer(OverlayCommand.Displays(it.map { display -> display.id }.toSet())) } }
            launch { container.library.state.drop(1).collect { coordinator?.offer(OverlayCommand.Refresh()) } }
            launch { container.settings.map { it.coverCutout }.distinctUntilChanged().drop(1).collect { runCatching { host.resize() } } }
            launch { container.overlayCapabilities.state.collect {
                runCatching { host.resize() }.onFailure { container.events.record("DISPLAY_RESIZE_FAILED") }
                coordinator?.offer(OverlayCommand.Refresh(false))
            } }
            launch { container.runtime.map { listOf(it.autoMonitoring, it.sessions.size, it.pendingDisplays.size, it.monitorProblem) }.distinctUntilChanged().collect { ensureForeground() } }
            launch { while (isActive) { delay(1000); coordinator?.offer(OverlayCommand.Refresh(false)) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            scope.launch {
                if (container.preferences.settings.first().recoverAutomaticSession && container.recovery.beginRestore() == RestoreDecision.RESTORE)
                    accept(Intent().setAction(RECOVER_AUTO))
                else stopSelf()
            }
        } else accept(intent)
        return when {
            intent?.action == STOP_ALL || (intent?.action == SET_AUTO && !intent.getBooleanExtra(EXTRA_ENABLED, false)) -> START_NOT_STICKY
            intent?.action == RECOVER_AUTO || (intent?.action == SET_AUTO && intent.getBooleanExtra(EXTRA_ENABLED, false) && container.settings.value.recoverAutomaticSession) -> START_STICKY
            container.recovery.autoWanted && container.settings.value.recoverAutomaticSession -> START_STICKY
            else -> START_NOT_STICKY
        }
    }

    private fun accept(intent: Intent) {
        scope.launch {
            ready.await()
            val display = intent.getIntExtra(EXTRA_DISPLAY, 0)
            val command = when (intent.action) {
                START_QUICK -> OverlayCommand.StartQuick(display)
                STOP_QUICK -> OverlayCommand.StopQuick(display)
                TOGGLE -> OverlayCommand.Toggle(display)
                TOGGLE_ALL -> OverlayCommand.ToggleAll
                SET_AUTO -> OverlayCommand.SetAuto(intent.getBooleanExtra(EXTRA_ENABLED, false))
                RECOVER_AUTO -> OverlayCommand.SetAuto(true, restoring = true)
                SET_BALL -> OverlayCommand.SetBall(intent.getBooleanExtra(EXTRA_ENABLED, false))
                TOGGLE_BALL -> OverlayCommand.ToggleBall
                REFRESH -> OverlayCommand.Refresh()
                else -> OverlayCommand.StopAll
            }
            try {
                if (intent.action != RECOVER_AUTO && !intent.getBooleanExtra(EXTRA_SAVED, false)) saveIntent(container, intent.action, intent.getBooleanExtra(EXTRA_ENABLED, false))
            } catch (_: Exception) { container.messages.tryEmit(getString(R.string.error_session_save)); return@launch }
            coordinator?.offer(command)
            if (intent.action == RECOVER_AUTO) container.events.record("AUTOMATIC_SESSION_RESTORED")
        }
    }

    private fun ensureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_help); setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlaySessionService::class.java).setAction(STOP_ALL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val state = container.runtime.value
        val text = when {
            state.sessions.isNotEmpty() -> getString(R.string.notification_active, state.sessions.size)
            state.autoMonitoring && state.monitorProblem != null -> getString(R.string.notification_paused)
            state.autoMonitoring -> getString(R.string.notification_auto)
            else -> getString(R.string.notification_session)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name)).setContentText(text).setContentIntent(open)
            .addAction(R.drawable.ic_notification, getString(R.string.stop_all), stop).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching { engine?.resize(); control?.refreshText(); ensureForeground() }.onFailure { container.events.record("DISPLAY_RESIZE_FAILED") }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (live === this) live = null
        observer?.stop(); coordinator?.close(); control?.close(); container.privileged.stopForegroundObservation(); scope.cancel()
        if (::container.isInitialized) { container.runtime.value = RuntimeState(); container.events.record("SERVICE_STOPPED") }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "easyoverlay_session_v1"
        private const val NOTIFICATION_ID = 601
        private const val PREFIX = "com.hycnyl.easyoverlay."
        const val START_QUICK = PREFIX + "START_QUICK"
        const val STOP_QUICK = PREFIX + "STOP_QUICK"
        const val STOP_ALL = PREFIX + "STOP_ALL"
        const val TOGGLE = PREFIX + "TOGGLE"
        const val TOGGLE_ALL = PREFIX + "TOGGLE_ALL"
        const val SET_AUTO = PREFIX + "SET_AUTO"
        private const val RECOVER_AUTO = PREFIX + "RECOVER_AUTO"
        const val SET_BALL = PREFIX + "SET_BALL"
        const val TOGGLE_BALL = PREFIX + "TOGGLE_BALL"
        const val REFRESH = PREFIX + "REFRESH"
        const val EXTRA_DISPLAY = "display_id"
        const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_SAVED = "intent_saved"
        @Volatile private var live: OverlaySessionService? = null
        private var restoring = false

        private suspend fun saveIntent(container: AppContainer, action: String?, enabled: Boolean) {
            if (action == SET_AUTO || action == STOP_ALL) {
                val wanted = action == SET_AUTO && enabled
                container.recovery.userRequested(wanted)
                container.preferences.setAutoRequested(wanted)
            }
        }

        fun request(context: Context, action: String, displayId: Int = 0, enabled: Boolean = false) {
            val app = context.applicationContext
            val container = (app as EasyOverlayApplication).container
            container.scope.launch {
                try { saveIntent(container, action, enabled) }
                catch (_: Exception) { container.messages.tryEmit(app.getString(R.string.error_session_save)); return@launch }
                val intent = Intent(app, OverlaySessionService::class.java).setAction(action)
                    .putExtra(EXTRA_DISPLAY, displayId).putExtra(EXTRA_ENABLED, enabled).putExtra(EXTRA_SAVED, true)
                if (live == null && (action in setOf(STOP_QUICK, STOP_ALL, REFRESH) || (action in setOf(SET_AUTO, SET_BALL) && !enabled))) return@launch
                dispatch(app, container, intent, false)
            }
        }

        /** Manual windows never reappear after process death; honor Task Manager Stop and restart backoff. */
        fun restoreIfWanted(context: Context, reason: String): Job? {
            val app = context.applicationContext
            val container = (app as EasyOverlayApplication).container
            if (live != null || restoring || !container.recovery.autoWanted) return null
            restoring = true
            return container.scope.launch {
                try {
                    if (!container.preferences.settings.first().recoverAutomaticSession) return@launch
                    when (container.recovery.beginRestore()) {
                        RestoreDecision.RESTORE -> { container.events.record("RECOVERY_$reason"); dispatch(app, container, Intent(app, OverlaySessionService::class.java).setAction(RECOVER_AUTO), true) }
                        RestoreDecision.USER_STOPPED -> { container.preferences.setAutoRequested(false); container.events.record("RECOVERY_USER_STOPPED") }
                        RestoreDecision.RESTART_BACKOFF -> recoveryNotice(app, container, "RECOVERY_BACKOFF")
                        RestoreDecision.NOT_REQUESTED -> Unit
                    }
                } finally { restoring = false }
            }
        }

        private fun dispatch(context: Context, container: AppContainer, intent: Intent, recovery: Boolean) {
            try {
                // ActivityManager must receive the command to update the sticky restart policy of a live service.
                if (live != null) context.startService(intent) else ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                val code = if (recovery) "RECOVERY_OPEN_APP_REQUIRED" else "BACKGROUND_START_NOT_ALLOWED"
                container.events.record(code)
                if (recovery) recoveryNotice(context, container, code)
                else { container.runtime.value = RuntimeState(monitorProblem = code); container.messages.tryEmit(context.getString(R.string.recovery_needed_body)) }
            }
        }

        private fun recoveryNotice(context: Context, container: AppContainer, code: String) {
            container.runtime.value = container.runtime.value.copy(restoreProblem = code)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("easyoverlay_recovery", context.getString(R.string.recovery_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching { manager.notify(602, NotificationCompat.Builder(context, "easyoverlay_recovery").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.recovery_needed_title))
                .setContentText(context.getString(if (code == "RECOVERY_BACKOFF") R.string.recovery_backoff else R.string.recovery_needed_body))
                .setContentIntent(open).setAutoCancel(true).build()) }
        }
    }
}
