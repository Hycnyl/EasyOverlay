package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
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
    private data class Window(val manager: WindowManager, val view: ImageView,
        val params: WindowManager.LayoutParams, var target: OverlayTarget, var image: AndroidPrepared)
    private val windows = mutableMapOf<Int, Window>()
    private val displayManager = context.getSystemService(DisplayManager::class.java)

    fun maximumAlpha(): Float = context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch

    private fun windowContext(id: Int): Context {
        val display = displayManager.getDisplay(id)?.takeIf { it.isValid } ?: error("目标显示屏已断开")
        return context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    override suspend fun prepare(asset: OverlayAsset, displayId: Int): PreparedOverlay {
        val manager = windowContext(displayId).getSystemService(WindowManager::class.java)
        val bounds = manager.maximumWindowMetrics.bounds
        var prepared: AndroidPrepared? = null
        try {
            return withContext(Dispatchers.IO) {
                AndroidPrepared(AssetDecoder.decode(library.assetFile(asset), bounds.width(), bounds.height())).also { prepared = it }
            }
        } catch (e: Exception) { prepared?.close(); throw e }
    }

    override fun mount(target: OverlayTarget, image: PreparedOverlay) {
        check(Permissions.overlay(context)) { "悬浮窗权限未授予" }
        val prepared = image as AndroidPrepared
        val safe = target.copy(opacityPercent = OpacityPolicy.clamp(target.opacityPercent, maximumAlpha()))
        val existing = windows[target.displayId]
        if (existing != null) {
            val previous = existing.image
            val oldParams = WindowManager.LayoutParams().also { it.copyFrom(existing.params) }
            try {
                // One window stays mounted. Decoding has already finished; no overlapping replacement windows.
                existing.view.setImageDrawable(prepared.drawable)
                applyLayout(existing.manager, existing.params, safe.opacityPercent)
                existing.manager.updateViewLayout(existing.view, existing.params)
                existing.image = prepared; existing.target = safe
                (prepared.drawable as? AnimatedImageDrawable)?.start()
                previous.close()
            } catch (e: Exception) {
                existing.view.setImageDrawable(previous.drawable)
                existing.params.copyFrom(oldParams)
                runCatching { existing.manager.updateViewLayout(existing.view, existing.params) }
                if (!existing.view.isAttachedToWindow) { windows.remove(target.displayId); previous.close() }
                throw e
            }
        } else {
            val localContext = windowContext(target.displayId)
            val manager = localContext.getSystemService(WindowManager::class.java)
            val view = ImageView(localContext).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                isClickable = false; isFocusable = false
                importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
                setImageDrawable(prepared.drawable)
            }
            val params = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                title = "EasyOverlay"
            }
            applyLayout(manager, params, safe.opacityPercent)
            try {
                manager.addView(view, params)
                windows[target.displayId] = Window(manager, view, params, safe, prepared)
                (prepared.drawable as? AnimatedImageDrawable)?.start()
            } catch (e: Exception) { runCatching { manager.removeViewImmediate(view) }; throw e }
        }
    }

    private fun applyLayout(manager: WindowManager, params: WindowManager.LayoutParams, opacity: Int) {
        val bounds = manager.maximumWindowMetrics.bounds
        params.width = bounds.width(); params.height = bounds.height()
        params.x = bounds.left; params.y = bounds.top; params.gravity = Gravity.TOP or Gravity.START
        params.setFitInsetsTypes(0); params.setFitInsetsSides(0)
        params.layoutInDisplayCutoutMode = if (coverCutout()) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        params.alpha = OpacityPolicy.clamp(opacity, maximumAlpha()) / 100f
    }

    override fun remove(displayId: Int) {
        val window = windows[displayId] ?: return
        try { window.manager.removeViewImmediate(window.view) }
        finally {
            if (!window.view.isAttachedToWindow) { window.image.close(); windows.remove(displayId) }
        }
    }
    override fun targets(): Map<Int, OverlayTarget> = windows.mapValues { it.value.target }
    override fun resize() {
        windows.toMap().forEach { (id, window) ->
            if (displayManager.getDisplay(id)?.isValid != true) remove(id)
            else {
                applyLayout(window.manager, window.params, window.target.opacityPercent)
                window.manager.updateViewLayout(window.view, window.params)
            }
        }
    }
    override fun close() { windows.keys.toList().forEach { runCatching { remove(it) } }; windows.values.forEach { it.image.close() }; windows.clear() }
}
