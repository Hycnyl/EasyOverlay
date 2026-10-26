package com.hycnyl.easyoverlay.platform

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import com.hycnyl.easyoverlay.EasyOverlayApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HardwareCapture {
    private val _keys = MutableStateFlow<Set<Int>>(emptySet())
    val keys = _keys.asStateFlow()
    var active = false
        private set
    fun begin() { _keys.value = emptySet(); active = true }
    fun end() { active = false }
    fun handle(event: KeyEvent): Boolean {
        if (!active || event.keyCode in setOf(KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_POWER)) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && _keys.value.size < 3) _keys.value = _keys.value + event.keyCode
        return true
    }
}

class HardwareKeyService : AccessibilityService() {
    private val pressed = mutableSetOf<Int>()
    private var fired = false
    private val container get() = (application as EasyOverlayApplication).container
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityOverlayBridge.onConnected(this)
        container.monitoring.accessibilityConnected(this)
        OverlaySessionService.restoreIfWanted(this, "ACCESSIBILITY_CONNECTED")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) container.monitoring.accessibilityEvent(this, event)
    }
    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityOverlayBridge.onDisconnected(this)
        container.monitoring.accessibilityDisconnected(this)
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        AccessibilityOverlayBridge.onDisconnected(this)
        container.monitoring.accessibilityDisconnected(this)
        pressed.clear()
        super.onDestroy()
    }
    override fun onInterrupt() { pressed.clear(); fired = false }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (HardwareCapture.active) return HardwareCapture.handle(event)
        if (container.appVisible) { pressed.clear(); fired=false; return false }
        if (event.action == KeyEvent.ACTION_UP) {
            val consume = fired && event.keyCode in pressed
            pressed.remove(event.keyCode)
            if (pressed.isEmpty()) fired = false
            return consume
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        pressed += event.keyCode
        if (fired) return true
        val matched = container.settings.value.hardwareBindings.entries.firstOrNull { (_, keys) -> keys.isNotEmpty() && pressed.containsAll(keys) } ?: return false
        fired = true
        when (matched.key) {
            "toggle_main" -> OverlaySessionService.request(this, OverlaySessionService.TOGGLE, 0)
            "toggle_secondary" -> container.displays.displays.value.firstOrNull { it.id != 0 }?.let {
                OverlaySessionService.request(this, OverlaySessionService.TOGGLE, it.id)
            }
            "toggle_all" -> OverlaySessionService.request(this, OverlaySessionService.TOGGLE_ALL)
            "toggle_ball" -> OverlaySessionService.request(this, OverlaySessionService.TOGGLE_BALL)
        }
        return true
    }
}
