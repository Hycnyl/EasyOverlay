package com.hycnyl.easyoverlay.platform

import android.content.Context
import com.hycnyl.easyoverlay.platform.monitoring.MonitoringController
import kotlinx.coroutines.CoroutineScope

/** Compatibility facade: one effective observation source enters the existing serialized coordinator. */
class ForegroundAppObserver(context: Context, scope: CoroutineScope,
    private val controller: MonitoringController = MonitoringController(context, scope),
    private val onObservation: (String?, Long, String?) -> Unit) {
    fun start() = controller.start(onObservation)
    fun stop() = controller.stop()
}
