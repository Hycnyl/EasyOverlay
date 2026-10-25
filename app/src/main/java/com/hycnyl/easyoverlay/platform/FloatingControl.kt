package com.hycnyl.easyoverlay.platform

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/** A small optional control window, default off. It never owns an overlay session. */
class FloatingControl(private val context: Context, private val toggle: () -> Unit, private val stop: () -> Unit) {
    private var view: TextView? = null
    private var manager: WindowManager? = null
    fun show(enabled: Boolean): Boolean {
        if (!enabled) { close(); return false }
        if (view != null) return true
        if (!Permissions.overlay(context)) return false
        return try {
            val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(0)
            val localContext = context.createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            val wm = localContext.getSystemService(WindowManager::class.java)
            val density = context.resources.displayMetrics.density
            val size = (48 * density).toInt()
            val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; x = 0; y = (180 * density).toInt(); title = "EasyOverlay control"
            }
            val control = TextView(localContext).apply {
                text = "E"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setTextColor(0xFFF1FFF8.toInt())
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF175E51.toInt()); setStroke((2*density).toInt(),0xFFD0F0E3.toInt()) }
                contentDescription = "EasyOverlay：轻触切换主屏遮罩，拖动移位，长按停止全部"
                setOnClickListener { toggle() }
                setOnLongClickListener { stop(); true }
            }
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragged = false
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            control.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX=params.x; startY=params.y; dragged=false; false }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX-downX)>slop || abs(event.rawY-downY)>slop) dragged=true
                        if (dragged) {
                            v.cancelLongPress()
                            val bounds = wm.maximumWindowMetrics.bounds
                            params.x = (startX + event.rawX-downX).toInt().coerceIn(0,(bounds.width()-size).coerceAtLeast(0))
                            params.y = (startY + event.rawY-downY).toInt().coerceIn(0,(bounds.height()-size).coerceAtLeast(0))
                            runCatching { wm.updateViewLayout(v,params) }
                        }
                        dragged
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragged) {
                            val bounds=wm.maximumWindowMetrics.bounds
                            params.x=if (params.x < bounds.width()/2) 0 else (bounds.width()-size).coerceAtLeast(0)
                            runCatching { wm.updateViewLayout(v,params) }
                        }
                        dragged
                    }
                    else -> false
                }
            }
            wm.addView(control, params); view=control; manager=wm; true
        } catch (_: Exception) { close(); false }
    }
    fun close() { view?.let { runCatching { manager?.removeViewImmediate(it) } }; view=null; manager=null }
}
