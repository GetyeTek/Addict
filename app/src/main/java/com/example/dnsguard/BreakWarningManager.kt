package com.guardian.net

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.os.Handler
import android.os.Looper

object BreakWarningManager {
    private var windowManager: WindowManager? = null
    private var warningView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    fun showWarning(ctx: Context, message: String, isCountdown: Boolean) {
        if (warningView != null) updateText(message)
        else createView(ctx, message)

        if (!isCountdown) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ hide() }, 5000)
        }
    }

    private fun createView(ctx: Context, message: String) {
        windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xEE064E3B.toInt()) // Deep forest green semi-transparent
            cornerRadius = 100f // Pill shape
            setStroke(4, 0xFF10B981.toInt()) // Emerald glow border
        }

        val view = TextView(ctx).apply {
            text = message
            setTextColor(0xFFFFFFFF.toInt()) // High contrast White
            setBackground(background)
            setPadding(60, 20, 60, 20)
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
            elevation = 20f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
        }.apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120 // Positioned below the status bar area
        }

        try {
            windowManager?.addView(view, params)
            warningView = view
        } catch (e: Exception) {}
    }

    private fun updateText(msg: String) {
        (warningView as? TextView)?.text = msg
    }

    fun hide() {
        warningView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) {}
            warningView = null
        }
    }
}
