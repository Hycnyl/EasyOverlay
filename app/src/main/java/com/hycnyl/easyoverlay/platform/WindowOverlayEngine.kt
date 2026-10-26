package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.view.View
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.hycnyl.easyoverlay.data.AssetDecoder
import com.hycnyl.easyoverlay.data.LibraryRepository
import com.hycnyl.easyoverlay.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AndroidPrepared(val drawable: Drawable) : PreparedOverlay {
    override fun close() { (drawable as? AnimatedImageDrawable)?.stop() }
}

class WindowOverlayEngine(private val context: Context, private val library: LibraryRepository,
    private val coverCutout: () -> Boolean) : OverlayEngine {
    private data class Backend(val context: Context, val manager: WindowManager, val type: Int,
        val connectionGeneration: Long = 0)
    private data class Window(val backend: Backend, val view: ImageView,
        val params: WindowManager.LayoutParams, var target: OverlayTarget, var image: AndroidPrepared)

    private val windows = mutableMapOf<Int, Window>()
    private val ordinaryContexts = mutableMapOf<Int, Context>()
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    private fun ordinaryMaximumAlpha(): Float =
        context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch

    /** Capability is per display. A trusted primary window never unlocks a secondary SAW window. */
    fun maximumAlpha(displayId: Int = 0): Float =
        if (AccessibilityOverlayBridge.windowContext(displayId) != null) 1f else ordinaryMaximumAlpha()

    private fun ordinaryContext(displayId: Int): Context {
        val display = displayManager.getDisplay(displayId)?.takeIf { it.isValid }
            ?: error("DISPLAY_UNAVAILABLE")
        return ordinaryContexts.getOrPut(displayId) {
            context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        }
    }

    private fun ordinaryBackend(displayId: Int): Backend {
        check(Permissions.overlay(context)) { "OVERLAY_PERMISSION_MISSING" }
        val local = ordinaryContext(displayId)
        return Backend(local, local.getSystemService(WindowManager::class.java),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
    }

    private fun backendFor(displayId: Int, requestedOpacity: Int): Backend {
        check(displayManager.getDisplay(displayId)?.isValid == true) { "DISPLAY_UNAVAILABLE" }
        val trusted = AccessibilityOverlayBridge.windowContext(displayId)
        // Retain ordinary windows at safe values when possible; granting a11y does not by itself
        // replace every existing window. High values, or absence of SAW permission, need its token.
        val needsTrusted = requestedOpacity > OpacityPolicy.maxPercent(ordinaryMaximumAlpha()) ||
            !Permissions.overlay(context)
        if (trusted != null && needsTrusted) {
            return Backend(trusted, trusted.getSystemService(WindowManager::class.java),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityOverlayBridge.state.value.generation)
        }
        return ordinaryBackend(displayId)
    }

    private fun maximumAlpha(backend: Backend): Float =
        if (backend.type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) 1f
        else ordinaryMaximumAlpha()

    private fun safeTarget(target: OverlayTarget, backend: Backend): OverlayTarget =
        target.copy(opacityPercent = OpacityPolicy.clamp(target.opacityPercent, maximumAlpha(backend)))

    private fun sameBackend(left: Backend, right: Backend): Boolean =
        left.type == right.type && left.context === right.context &&
            left.connectionGeneration == right.connectionGeneration

    override suspend fun prepare(asset: OverlayAsset, displayId: Int): PreparedOverlay {
        val backend = backendFor(displayId, 0)
        val bounds = backend.manager.maximumWindowMetrics.bounds
        var prepared: AndroidPrepared? = null
        try {
            return withContext(Dispatchers.IO) {
                AndroidPrepared(AssetDecoder.decode(library.assetFile(asset), bounds.width(), bounds.height()))
                    .also { prepared = it }
            }
        } catch (e: Exception) { prepared?.close(); throw e }
    }

    override fun mount(target: OverlayTarget, image: PreparedOverlay) {
        val prepared = image as AndroidPrepared
        val backend = backendFor(target.displayId, target.opacityPercent)
        try {
            mountOnBackend(target, prepared, backend)
        } catch (failure: Exception) {
            if (backend.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) throw failure
            // Enabled permission is not a proof that an OEM accepts an accessibility window token.
            // Invalidate its advertised 100% capability, and try a safely clamped ordinary window.
            AccessibilityOverlayBridge.onWindowRejected(backend.connectionGeneration)
            if (!Permissions.overlay(context)) {
                makeExistingWindowSafe(target.displayId)
                throw failure
            }
            try {
                mountOnBackend(target, prepared, ordinaryBackend(target.displayId))
            } catch (fallbackFailure: Exception) {
                makeExistingWindowSafe(target.displayId)
                throw fallbackFailure
            }
        }
    }

    private fun mountOnBackend(target: OverlayTarget, prepared: AndroidPrepared, backend: Backend) {
        val safe = safeTarget(target, backend)
        val existing = windows[target.displayId]
        if (existing != null && sameBackend(existing.backend, backend)) {
            updateExisting(existing, safe, prepared)
        } else if (existing != null) {
            replaceBackend(existing, safe, prepared, backend)
        } else {
            val window = createWindow(safe, prepared, backend)
            try {
                backend.manager.addView(window.view, window.params)
                windows[target.displayId] = window
                start(prepared)
            } catch (e: Exception) {
                runCatching { backend.manager.removeViewImmediate(window.view) }
                windows.remove(target.displayId)
                throw e
            }
        }
    }

    private fun updateExisting(window: Window, target: OverlayTarget, prepared: AndroidPrepared) {
        val previous = window.image
        val oldParams = WindowManager.LayoutParams().also { it.copyFrom(window.params) }
        try {
            if (previous !== prepared) window.view.setImageDrawable(prepared.drawable)
            applyLayout(window.backend, window.params, target.opacityPercent)
            window.backend.manager.updateViewLayout(window.view, window.params)
            window.image = prepared; window.target = target
            start(prepared)
            if (previous !== prepared) previous.close()
        } catch (e: Exception) {
            window.view.setImageDrawable(previous.drawable)
            window.params.copyFrom(oldParams)
            runCatching { window.backend.manager.updateViewLayout(window.view, window.params) }
            if (!window.view.isAttachedToWindow) {
                windows.remove(target.displayId)
                previous.close()
            }
            throw e
        }
    }

    /**
     * Window type/token cannot be changed through updateViewLayout. The replacement is added
     * with window alpha zero; the old window is removed before the new alpha becomes nonzero.
     * There are therefore never two visible full-screen SAW windows with cumulative obscuring
     * alpha. A failed add keeps the previous image; later failures attempt to restore its window.
     */
    private fun replaceBackend(existing: Window, target: OverlayTarget,
        prepared: AndroidPrepared, backend: Backend) {
        val replacement = createWindow(target, prepared, backend)
        val intendedAlpha = replacement.params.alpha
        replacement.params.alpha = 0f
        var added = false
        var oldRemoved = false
        try {
            backend.manager.addView(replacement.view, replacement.params)
            added = true
            detach(existing)
            oldRemoved = true
            replacement.params.alpha = intendedAlpha
            backend.manager.updateViewLayout(replacement.view, replacement.params)
            windows[target.displayId] = replacement
            start(prepared)
            if (existing.image !== prepared) existing.image.close()
        } catch (failure: Exception) {
            if (added) runCatching { backend.manager.removeViewImmediate(replacement.view) }
            if (oldRemoved) {
                val restored = runCatching {
                    // A trusted service may have disconnected during replacement. Its old image
                    // must not remain above the ordinary safety threshold while recovery runs.
                    if (!trustedConnectionStillValid(existing.backend)) {
                        existing.target = existing.target.copy(opacityPercent =
                            OpacityPolicy.clamp(existing.target.opacityPercent, ordinaryMaximumAlpha()))
                    }
                    applyLayout(existing.backend, existing.params, existing.target.opacityPercent)
                    existing.backend.manager.addView(existing.view, existing.params)
                    start(existing.image)
                }.isSuccess
                if (!restored) {
                    windows.remove(target.displayId)
                    if (existing.image !== prepared) existing.image.close()
                } else windows[target.displayId] = existing
            } else {
                // Reattach the Drawable callback if the same decoded image was temporarily used
                // by the transparent replacement (backend-only migration during resize).
                if (existing.image === prepared) {
                    existing.view.setImageDrawable(null)
                    existing.view.setImageDrawable(existing.image.drawable)
                }
                windows[target.displayId] = existing
            }
            throw failure
        }
    }

    private fun trustedConnectionStillValid(backend: Backend): Boolean =
        backend.type != WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY ||
            (AccessibilityOverlayBridge.state.value.trustedPrimaryAvailable &&
                backend.connectionGeneration == AccessibilityOverlayBridge.state.value.generation)

    private fun makeExistingWindowSafe(displayId: Int) {
        val window = windows[displayId] ?: return
        val limited = window.target.copy(opacityPercent =
            OpacityPolicy.clamp(window.target.opacityPercent, ordinaryMaximumAlpha()))
        try {
            window.params.alpha = limited.opacityPercent / 100f
            window.backend.manager.updateViewLayout(window.view, window.params)
            window.target = limited
        } catch (_: Exception) {
            window.view.visibility = View.INVISIBLE
            runCatching { remove(displayId) }
        }
    }

    private fun createWindow(target: OverlayTarget, prepared: AndroidPrepared, backend: Backend): Window {
        val view = ImageView(backend.context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false; isFocusable = false
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            setImageDrawable(prepared.drawable)
        }
        val params = WindowManager.LayoutParams().apply {
            type = backend.type
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            title = "EasyOverlay"
        }
        applyLayout(backend, params, target.opacityPercent)
        return Window(backend, view, params, target, prepared)
    }

    private fun applyLayout(backend: Backend, params: WindowManager.LayoutParams, opacity: Int) {
        val bounds = backend.manager.maximumWindowMetrics.bounds
        params.width = bounds.width(); params.height = bounds.height()
        params.x = bounds.left; params.y = bounds.top; params.gravity = Gravity.TOP or Gravity.START
        params.setFitInsetsTypes(0); params.setFitInsetsSides(0)
        params.layoutInDisplayCutoutMode = if (coverCutout()) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        params.alpha = OpacityPolicy.clamp(opacity, maximumAlpha(backend)) / 100f
    }

    private fun start(prepared: AndroidPrepared) { (prepared.drawable as? AnimatedImageDrawable)?.start() }

    private fun detach(window: Window) {
        try { window.backend.manager.removeViewImmediate(window.view) }
        catch (e: Exception) { if (window.view.isAttachedToWindow) throw e }
    }

    override fun remove(displayId: Int) {
        val window = windows[displayId] ?: return
        try { detach(window) }
        finally {
            if (!window.view.isAttachedToWindow) { window.image.close(); windows.remove(displayId) }
        }
    }

    override fun targets(): Map<Int, OverlayTarget> = windows.mapValues { it.value.target }

    override fun resize() {
        var failure: Exception? = null
        windows.toMap().forEach { (id, window) ->
            try {
                if (displayManager.getDisplay(id)?.isValid != true) {
                    remove(id)
                } else {
                    // This also migrates a disconnected trusted window to a safe ordinary one,
                    // or changes its token after the accessibility service reconnects.
                    mount(window.target, window.image)
                }
            } catch (e: Exception) {
                if (!trustedConnectionStillValid(window.backend) ||
                    (!Permissions.overlay(context) && AccessibilityOverlayBridge.windowContext(id) == null)) {
                    window.view.visibility = View.INVISIBLE
                    runCatching { remove(id) }
                }
                if (failure == null) failure = e
            }
        }
        ordinaryContexts.keys.toList().filter { displayManager.getDisplay(it)?.isValid != true }
            .forEach { ordinaryContexts.remove(it) }
        failure?.let { throw it }
    }

    override fun close() {
        windows.values.forEach { it.view.visibility = View.GONE }
        windows.keys.toList().forEach { runCatching { remove(it) } }
        windows.values.forEach { it.image.close() }
        windows.clear(); ordinaryContexts.clear()
    }
}
