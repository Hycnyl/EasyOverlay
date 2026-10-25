package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DisplayInfo(val id: Int, val label: String)

class DisplayCatalog(context: Context) : DisplayManager.DisplayListener {
    private val manager = context.getSystemService(DisplayManager::class.java)
    private val _displays = MutableStateFlow(read())
    val displays = _displays.asStateFlow()
    init { manager.registerDisplayListener(this, Handler(Looper.getMainLooper())) }
    private fun read() = manager.displays.filter { it.isValid &&
        (it.displayId == Display.DEFAULT_DISPLAY || it.flags and Display.FLAG_PRIVATE == 0) }
        .map { DisplayInfo(it.displayId, if (it.displayId == 0) "主屏" else "副屏 ${it.displayId}") }.sortedBy { it.id }
    override fun onDisplayAdded(displayId: Int) { _displays.value = read() }
    override fun onDisplayRemoved(displayId: Int) { _displays.value = read() }
    override fun onDisplayChanged(displayId: Int) { _displays.value = read() }
}
