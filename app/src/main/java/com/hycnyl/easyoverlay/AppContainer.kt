package com.hycnyl.easyoverlay

import android.content.Context
import android.hardware.input.InputManager
import com.hycnyl.easyoverlay.data.*
import com.hycnyl.easyoverlay.domain.*
import com.hycnyl.easyoverlay.platform.DisplayCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppContainer(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val library = LibraryRepository(context)
    val preferences = SettingsRepository(context)
    val settings = preferences.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val images = ImageImporter(context, library)
    val backups = BackupManager(context, library, images)
    val legacy = LegacyConfigImporter(context, library, images)
    val events = EventLog(context, scope)
    val displays = DisplayCatalog(context)
    val runtime = MutableStateFlow(RuntimeState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    var appVisible: Boolean = false
    fun maximumAlpha(): Float = context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch
    val maximumPercent: Int get() = OpacityPolicy.maxPercent(maximumAlpha())
    init { scope.launch { library.load(); events.record("APP_READY") } }
}
