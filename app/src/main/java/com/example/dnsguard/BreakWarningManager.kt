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
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        
        // Create a simple but elegant banner layout programmatically or via basic view
        val view = TextView(ctx).apply {
            text = message
            setTextColor(0xFF34D399.toInt())
            setBackgroundColor(0xDD064E3B.toInt())
            setPadding(40, 20, 40, 20)
            gravity = Gravity.CENTER
            textSize = 14f
            elevation = 10f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 50 // Margin from top
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
