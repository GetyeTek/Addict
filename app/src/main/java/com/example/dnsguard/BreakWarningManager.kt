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
        
        val isCountdown = message.contains("LOCKDOWN")
        val background = android.graphics.drawable.GradientDrawable().apply {
            // Red/Orange for countdown, Green for warning
            setColor(if (isCountdown) 0xEEB91C1C.toInt() else 0xEE064E3B.toInt())
            cornerRadius = 100f
            setStroke(2, 0xFFFFFFFF.toInt())
        }

        val view = TextView(ctx).apply {
            text = message
            setTextColor(0xFFFFFFFF.toInt())
            setBackground(background)
            setPadding(80, 25, 80, 25)
            gravity = Gravity.CENTER
            textSize = 14f
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            letterSpacing = 0.05f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE 
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200 // Pushed lower to clear notches and status bars
            windowAnimations = android.R.style.Animation_Toast
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
