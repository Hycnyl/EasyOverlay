package com.hycnyl.easyoverlay

import android.content.Context
import android.hardware.input.InputManager
import com.hycnyl.easyoverlay.data.*
import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.DisplayCatalog
import com.hycnyl.easyoverlay.platform.OverlayCapabilities
import com.hycnyl.easyoverlay.platform.monitoring.*
import com.hycnyl.easyoverlay.platform.privileged.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val library = LibraryRepository(context)
    val preferences = SettingsRepository(context)
    val settings = preferences.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val images = ImageImporter(context, library)
    val backups = BackupManager(context, library, images)
    val events = EventLog(context, scope)
    val displays = DisplayCatalog(context)
    val overlayCapabilities = OverlayCapabilities(context, scope)
    val monitoring = MonitoringController(context, scope)
    val privileged = PrivilegedAccessController(context, scope)
    val dhizuku = DhizukuAccessController(context, scope)
    val recovery = SessionRecoveryStore(context)
    val runtime = MutableStateFlow(RuntimeState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    var appVisible: Boolean = false
    fun maximumAlpha(): Float = overlayCapabilities.maximumAlpha()
    val maximumPercent: Int get() = overlayCapabilities.maximumPercent()
    init {
        scope.launch { if(recovery.honorUserStop()) {
            runCatching {preferences.setAutoRequested(false)}
            events.record("RECOVERY_USER_STOPPED")
        } }
        scope.launch { library.load(); events.record("APP_READY") }
        scope.launch { settings.map { it.monitoringMode }.distinctUntilChanged().collect { mode ->
            monitoring.setMode(runCatching { MonitoringMode.valueOf(mode) }.getOrDefault(MonitoringMode.AUTO))
        } }
        monitoring.setShizukuProbe({ privileged.state.value.available }) {
            val value = privileged.foregroundSnapshot()
            PrivilegedSnapshot(value.packageName, value.displayId, value.observedAt,
                value.problem?.let { "SHIZUKU_${it.name}" })
        }
        scope.launch { privileged.state.collect { monitoring.refreshAvailability() } }
    }
}
