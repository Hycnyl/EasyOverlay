package com.hycnyl.easyoverlay.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.view.Display
import android.view.WindowManager
import androidx.annotation.MainThread
import com.hycnyl.easyoverlay.domain.OpacityPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AccessibilityOverlayState(
    val connected: Boolean = false,
    val trustedPrimaryAvailable: Boolean = false,
    val generation: Long = 0,
    val problemCode: String? = null
)

/**
 * Owns the connected service's window token, never a Secure Settings enabled bit.
 * AccessibilityService.createWindowContext supplies AccessibilityContext and its
 * per-display token on supported Android versions. An application/FGS Context does not.
 *
 * Access and all lifecycle methods are confined to the main thread, like WindowManager.
 */
object AccessibilityOverlayBridge {
    private val _state = MutableStateFlow(AccessibilityOverlayState())
    val state = _state.asStateFlow()
    private var service: AccessibilityService? = null
    private var primaryContext: Context? = null

    @MainThread
    fun onConnected(connectedService: AccessibilityService) {
        service = connectedService
        primaryContext = null
        probePrimaryWindowContext()
    }

    @MainThread
    fun onDisconnected(disconnectedService: AccessibilityService) {
        // A delayed onDestroy from an old instance must not invalidate its replacement.
        if (service !== disconnectedService) return
        service = null
        primaryContext = null
        _state.value = AccessibilityOverlayState(generation = _state.value.generation + 1)
    }

    /** Explicit permission/backend retry, e.g. after the user returns from system settings. */
    @MainThread
    fun refresh() {
        if (service == null || primaryContext != null) return
        probePrimaryWindowContext()
    }

    @MainThread
    private fun probePrimaryWindowContext() {
        val connected = service ?: return
        val previous = _state.value
        try {
            val display = connected.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)?.takeIf { it.isValid }
                ?: error("PRIMARY_DISPLAY_UNAVAILABLE")
            val windowContext = connected.createWindowContext(
                display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null
            )
            // Context creation can fail on an OEM; do not advertise 100% from enabled status.
            val bounds = windowContext.getSystemService(WindowManager::class.java)
                .maximumWindowMetrics.bounds
            check(bounds.width() > 0 && bounds.height() > 0) { "PRIMARY_DISPLAY_UNAVAILABLE" }
            primaryContext = windowContext
            _state.value = AccessibilityOverlayState(true, true, previous.generation + 1)
        } catch (_: Exception) {
            primaryContext = null
            _state.value = AccessibilityOverlayState(true, false, previous.generation + 1,
                "ACCESSIBILITY_OVERLAY_UNAVAILABLE")
        }
    }

    /** Only the default display is validated in this release. Other displays stay ordinary. */
    @MainThread
    fun windowContext(displayId: Int): Context? =
        primaryContext.takeIf { displayId == Display.DEFAULT_DISPLAY && _state.value.connected &&
            _state.value.trustedPrimaryAvailable }

    /** A rejected addView invalidates advertised capability until the next explicit retry. */
    @MainThread
    fun onWindowRejected(generation: Long) {
        if (generation != _state.value.generation || !_state.value.connected) return
        primaryContext = null
        _state.value = _state.value.copy(trustedPrimaryAvailable = false,
            generation = generation + 1, problemCode = "ACCESSIBILITY_OVERLAY_UNAVAILABLE")
    }
}

data class OverlayCapabilityState(
    val ordinaryMaxPercent: Int,
    val accessibilityConnected: Boolean,
    val trustedPrimaryAvailable: Boolean,
    val generation: Long,
    val problemCode: String? = null
) {
    fun trusted(displayId: Int): Boolean = displayId == Display.DEFAULT_DISPLAY &&
        accessibilityConnected && trustedPrimaryAvailable
    fun maximumPercent(displayId: Int): Int = if (trusted(displayId)) 100 else ordinaryMaxPercent
    fun maximumAlpha(displayId: Int): Float = maximumPercent(displayId) / 100f

    fun reasonCode(displayId: Int): String? = when {
        displayId != Display.DEFAULT_DISPLAY -> "TRUSTED_SECONDARY_UNVERIFIED"
        !accessibilityConnected -> "ACCESSIBILITY_NOT_CONNECTED"
        !trustedPrimaryAvailable -> problemCode ?: "ACCESSIBILITY_OVERLAY_UNAVAILABLE"
        else -> null
    }
}

/** Read by UI and the service; a connection generation also signals window-backend changes. */
class OverlayCapabilities(private val context: Context, scope: CoroutineScope) {
    private fun snapshot(accessibility: AccessibilityOverlayState): OverlayCapabilityState =
        OverlayCapabilityState(
            OpacityPolicy.maxPercent(context.getSystemService(InputManager::class.java)
                .maximumObscuringOpacityForTouch),
            accessibility.connected, accessibility.trustedPrimaryAvailable,
            accessibility.generation, accessibility.problemCode
        )

    val state: StateFlow<OverlayCapabilityState> = AccessibilityOverlayBridge.state
        .map(::snapshot)
        .stateIn(scope, SharingStarted.Eagerly, snapshot(AccessibilityOverlayBridge.state.value))

    fun maximumPercent(displayId: Int = Display.DEFAULT_DISPLAY): Int = state.value.maximumPercent(displayId)
    fun maximumAlpha(displayId: Int = Display.DEFAULT_DISPLAY): Float = state.value.maximumAlpha(displayId)
    fun canOverlay(displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        state.value.trusted(displayId) || Permissions.overlay(context)
}
