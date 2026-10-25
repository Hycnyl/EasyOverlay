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
            observer = ForegroundAppObserver(this@OverlaySessionService, scope) { pkg, time, problem ->
                coordinator?.offer(OverlayCommand.Observe(pkg, time, problem))
            }
            control = FloatingControl(this@OverlaySessionService,
                { coordinator?.offer(OverlayCommand.Toggle(0)) }, { coordinator?.offer(OverlayCommand.StopAll) })
            coordinator = OverlayCoordinator(scope, container.library.state, host, container.runtime, packageName,
                host::maximumAlpha, { Permissions.overlay(this@OverlaySessionService) }, { Permissions.usage(this@OverlaySessionService) },
                { enabled -> if (enabled) observer?.start() else observer?.stop() },
                { enabled -> control?.show(enabled) ?: false },
                { code, display -> container.events.record(code, display) }, { stopSelf() })
            ready.complete(Unit)
            launch { container.displays.displays.collect { coordinator?.offer(OverlayCommand.Displays(it.map { display -> display.id }.toSet())) } }
            launch { container.library.state.drop(1).collect { coordinator?.offer(OverlayCommand.Refresh()) } }
            launch { container.settings.map { it.coverCutout }.distinctUntilChanged().drop(1).collect { runCatching { host.resize() } } }
            launch { container.runtime.map { Triple(it.autoMonitoring, it.sessions.size, it.pendingDisplays.size) }.distinctUntilChanged().collect { ensureForeground() } }
            launch { while (isActive) { delay(1000); coordinator?.offer(OverlayCommand.Refresh(false)) } }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        accept(intent)
        return START_NOT_STICKY
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
                SET_BALL -> OverlayCommand.SetBall(intent.getBooleanExtra(EXTRA_ENABLED, false))
                TOGGLE_BALL -> OverlayCommand.ToggleBall
                REFRESH -> OverlayCommand.Refresh()
                else -> OverlayCommand.StopAll
            }
            if (command is OverlayCommand.SetAuto) runCatching { container.preferences.setAutoRequested(command.enabled) }
            if (command == OverlayCommand.StopAll) runCatching { container.preferences.setAutoRequested(false) }
            coordinator?.offer(command)
        }
    }

    private fun ensureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "EasyOverlay 遮罩会话", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示正在运行的遮罩或自动监测会话，并提供停止入口"
            setShowBadge(false)
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlaySessionService::class.java).setAction(STOP_ALL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val state = if (::container.isInitialized) container.runtime.value else RuntimeState()
        val text = when {
            state.sessions.isNotEmpty() -> "${state.sessions.size} 块屏幕正在显示遮罩${if (state.autoMonitoring) " · 自动监测已开启" else ""}"
            state.autoMonitoring -> "自动监测已开启 · 等待匹配应用"
            else -> "用户控制的图片遮罩会话"
        }
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("EasyOverlay").setContentText(text).setContentIntent(open)
            .addAction(R.drawable.ic_notification, "停止全部", stop).setOngoing(true).setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runCatching { engine?.resize() }.onFailure { container.events.record("DISPLAY_RESIZE_FAILED") }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (live === this) live = null
        observer?.stop(); coordinator?.close(); control?.close(); scope.cancel()
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
        const val SET_BALL = PREFIX + "SET_BALL"
        const val TOGGLE_BALL = PREFIX + "TOGGLE_BALL"
        const val REFRESH = PREFIX + "REFRESH"
        const val EXTRA_DISPLAY = "display_id"
        const val EXTRA_ENABLED = "enabled"
        @Volatile private var live: OverlaySessionService? = null

        fun request(context: Context, action: String, displayId: Int = 0, enabled: Boolean = false) {
            val intent = Intent(context, OverlaySessionService::class.java).setAction(action)
                .putExtra(EXTRA_DISPLAY, displayId).putExtra(EXTRA_ENABLED, enabled)
            live?.let { it.accept(intent); return }
            if (action in setOf(STOP_QUICK, STOP_ALL, REFRESH) || (action in setOf(SET_AUTO, SET_BALL) && !enabled)) return
            val container = (context.applicationContext as EasyOverlayApplication).container
            try { ContextCompat.startForegroundService(context, intent) }
            catch (_: ForegroundServiceStartNotAllowedException) {
                container.runtime.value = RuntimeState(monitorProblem = "BACKGROUND_START_NOT_ALLOWED")
                container.events.record("BACKGROUND_START_NOT_ALLOWED")
                container.messages.tryEmit("系统暂不允许从后台启动。请打开 EasyOverlay，重新点击启动。")
            } catch (_: SecurityException) {
                container.events.record("SERVICE_PERMISSION_MISSING")
                container.messages.tryEmit("启动权限不足，请在设置中检查悬浮窗权限后重试。")
            } catch (_: Exception) {
                container.events.record("SERVICE_START_FAILED")
                container.messages.tryEmit("会话未能启动，请打开应用后重试，并查看诊断。")
            }
        }
    }
}
