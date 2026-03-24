package com.guardian.net

import android.content.Context
import android.provider.Settings
import java.util.Calendar
import java.util.UUID

object NukeManager {

    private const val PREFS = "nuke_prefs"
    private const val KEY_OTP_TS = "nuke_ts"
    private const val KEY_DISABLED = "protection_disabled"
    private const val KEY_LOCK_NOTIFIED = "lock_notified"
    private const val KEY_DISABLED_TS = "protection_disabled_ts"
    private const val AUTO_RE_ENABLE_MS = 60 * 60 * 1000L // 1 Hour

    // 2 Hours in MS
    private const val WAIT_TIME = 2 * 60 * 60 * 1000L
    // 3 Hours in MS (Expiry window)
    private const val EXPIRY_TIME = 3 * 60 * 60 * 1000L

    fun isProtectionDisabled(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLED, false)
    }

    fun setProtectionDisabled(ctx: Context, disabled: Boolean) {
        val now = if (disabled) System.currentTimeMillis() else 0L
        val editor = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        
        editor.putBoolean(KEY_DISABLED, disabled)
              .putLong(KEY_DISABLED_TS, now)
              .putBoolean(KEY_LOCK_NOTIFIED, false)
        
        if (disabled) {
            // Round completed successfully, clear timestamps
            editor.remove(KEY_OTP_TS)
        }
        editor.apply()
        
        if (!disabled) {
            showNotification(ctx, "SHACKLES ON", "Fun's over. You're back in the cage.")
        }
    }

    fun checkNotifications(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val disabledAt = prefs.getLong(KEY_DISABLED_TS, 0L)
        val isDisabled = prefs.getBoolean(KEY_DISABLED, false)
        if (isDisabled && !prefs.getBoolean(KEY_LOCK_NOTIFIED, false)) {
            if (now - disabledAt >= (50 * 60 * 1000L)) {
                showNotification(ctx, "TIMES UP, LOSER", "10 minutes until I take control again.")
                prefs.edit().putBoolean(KEY_LOCK_NOTIFIED, true).apply()
            }
        }
    }

    private fun showNotification(ctx: Context, title: String, msg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "security_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = android.app.NotificationChannel(channelId, "Security Alerts", android.app.NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(chan)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(msg)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        nm.notify(title.hashCode(), builder.build())
    }

    fun checkAutoReEnable(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val isDisabled = prefs.getBoolean(KEY_DISABLED, false)
        if (!isDisabled) return

        val disabledAt = prefs.getLong(KEY_DISABLED_TS, 0L)
        val elapsed = System.currentTimeMillis() - disabledAt

        if (elapsed > AUTO_RE_ENABLE_MS) {
            DebugLogger.log("NUKE", "Auto-Lock Triggered: 1 hour expired.")
            setProtectionDisabled(ctx, false)
        }
    }

    fun canRequestNuke(ctx: Context): String {
        // 1. Check Auto Time
        val autoTime = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, 0)
        val autoZone = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0)
        if (autoTime != 1 || autoZone != 1) {
            DebugLogger.log("NUKE_FAIL", "Auto-Time: $autoTime, Auto-Zone: $autoZone")
            return "ERROR: Automatic Time & Zone must be enabled in System Settings."
        }

        // 2. Check Time Window (6 AM - 6 PM)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour < 6 || hour >= 18) {
            DebugLogger.log("NUKE_FAIL", "Hour $hour is outside 06-18 window")
            return "ERROR: Protocol only available between 06:00 and 18:00."
        }

        return "OK"
    }

    fun isNukeReadyToConfirm(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_OTP_TS, 0L)
        val now = System.currentTimeMillis()
        if (ts == 0L) return false
        
        val diff = now - ts
        return diff >= WAIT_TIME && diff <= EXPIRY_TIME
    }

    data class NukeStatus(
        val isProtectionDisabled: Boolean,
        val isWaiting: Boolean,
        val isReady: Boolean,
        val isExpired: Boolean,
        val remainingWaitMs: Long,
        val isProtocolActive: Boolean,
        val isWindowOpen: Boolean
    )

    fun getStatus(ctx: Context): NukeStatus {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val disabled = prefs.getBoolean(KEY_DISABLED, false)
        val ts = prefs.getLong(KEY_OTP_TS, 0L)
        val now = System.currentTimeMillis()
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val windowOpen = hour in 6..17

        // If no protocol is active, check if we are manually disabled (via Debug Tile)
        if (ts == 0L) {
            return NukeStatus(disabled, false, false, false, 0L, false, windowOpen)
        }

        val elapsed = now - ts
        return when {
            elapsed < WAIT_TIME -> {
                // PHASE 1: Fuse Burning (Waiting 2 hours)
                NukeStatus(disabled, true, false, false, WAIT_TIME - elapsed, true, windowOpen)
            }
            elapsed < EXPIRY_TIME -> {
                // PHASE 2: Protection Dropped (The 1-hour window)
                NukeStatus(true, false, true, false, EXPIRY_TIME - elapsed, true, windowOpen)
            }
            else -> {
                // PHASE 3: Expired
                NukeStatus(false, false, false, true, 0L, false, windowOpen)
            }
        }
    }

    fun startQuitterTimer(ctx: Context) {
        val now = System.currentTimeMillis()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_OTP_TS, now)
            .apply()
    }

    fun handleAutoTransition(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ts = prefs.getLong(KEY_OTP_TS, 0L)
        if (ts == 0L) return

        val now = System.currentTimeMillis()
        val elapsed = now - ts

        when {
            // Window 1: Waiting (0-2 hours) -> Ensure protection is ON
            elapsed < WAIT_TIME -> {
                if (prefs.getBoolean(KEY_DISABLED, false)) setProtectionDisabled(ctx, false)
            }
            // Window 2: Protection Dropped (2-3 hours) -> Ensure protection is OFF
            elapsed >= WAIT_TIME && elapsed < (WAIT_TIME + AUTO_RE_ENABLE_MS) -> {
                if (!prefs.getBoolean(KEY_DISABLED, false)) {
                    setProtectionDisabled(ctx, true)
                    DebugLogger.log("NUKE", "Timer reached. Protection Dropped automatically.")
                }
            }
            // Window 3: Finished (3+ hours) -> Reset everything
            elapsed >= (WAIT_TIME + AUTO_RE_ENABLE_MS) -> {
                setProtectionDisabled(ctx, false)
                prefs.edit().remove(KEY_OTP_TS).apply()
                DebugLogger.log("NUKE", "Window closed. Protection Restored.")
            }
        }
    }
}