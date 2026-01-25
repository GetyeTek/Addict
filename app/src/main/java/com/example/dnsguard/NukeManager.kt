package com.guardian.net

import android.content.Context
import android.provider.Settings
import java.util.Calendar
import java.util.UUID

object NukeManager {

    private const val PREFS = "nuke_prefs"
    private const val KEY_OTP = "nuke_otp"
    private const val KEY_OTP_TS = "nuke_ts"
    private const val KEY_DISABLED = "protection_disabled"
    private const val KEY_OTP_NOTIFIED = "otp_notified"
    private const val KEY_LOCK_NOTIFIED = "lock_notified"
    private const val KEY_DISABLED_TS = "protection_disabled_ts"
    private const val AUTO_RE_ENABLE_MS = 60 * 60 * 1000L // 1 Hour

    // 3 Hours in MS
    private const val WAIT_TIME = 3 * 60 * 60 * 1000L
    // 4 Hours in MS (Expiry window)
    private const val EXPIRY_TIME = 4 * 60 * 60 * 1000L

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
            // Round completed successfully, clear OTP for next time
            editor.remove(KEY_OTP).remove(KEY_OTP_TS).remove(KEY_OTP_NOTIFIED)
        }
        editor.apply()
        
        if (!disabled) {
            showNotification(ctx, "We're Back", "Nuke protocol ended. I own you again.")
        }
    }

    fun checkNotifications(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val otpTs = prefs.getLong(KEY_OTP_TS, 0L)
        if (otpTs > 0 && !prefs.getBoolean(KEY_OTP_NOTIFIED, false)) {
            if (now - otpTs >= WAIT_TIME) {
                showNotification(ctx, "Do It Now", "The 3-hour wait is over. Push the button or I'm closing it.")
                prefs.edit().putBoolean(KEY_OTP_NOTIFIED, true).apply()
            }
        }

        val disabledAt = prefs.getLong(KEY_DISABLED_TS, 0L)
        val isDisabled = prefs.getBoolean(KEY_DISABLED, false)
        if (isDisabled && !prefs.getBoolean(KEY_LOCK_NOTIFIED, false)) {
            if (now - disabledAt >= (50 * 60 * 1000L)) {
                showNotification(ctx, "Fun's Over", "10 minutes left before I lock the door.")
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
        if (autoTime != 1 || autoZone != 1) return "ERROR: Automatic Time & Zone must be enabled."

        // 2. Check Time Window (6 AM - 6 PM)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour < 6 || hour >= 18) return "ERROR: Protocol only available between 06:00 and 18:00."

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
        val remainingWaitMs: Long,
        val otpGenerated: Boolean,
        val isWindowOpen: Boolean
    )

    fun getStatus(ctx: Context): NukeStatus {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val disabled = prefs.getBoolean(KEY_DISABLED, false)
        val ts = prefs.getLong(KEY_OTP_TS, 0L)
        val now = System.currentTimeMillis()
        
        val isWaiting = ts > 0 && (now - ts < WAIT_TIME)
        val isReady = ts > 0 && (now - ts >= WAIT_TIME) && (now - ts <= EXPIRY_TIME)
        val remaining = if (isWaiting) WAIT_TIME - (now - ts) else 0L
        
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val windowOpen = hour in 6..17
        
        // An OTP is only 'Generated' if it hasn't expired yet
        val isActiveRound = isWaiting || isReady
        
        return NukeStatus(disabled, isWaiting, isReady, remaining, isActiveRound, windowOpen)
    }

    fun generateOtp(ctx: Context): String {
        val otp = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val now = System.currentTimeMillis()
        
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OTP, otp)
            .putLong(KEY_OTP_TS, now)
            .putBoolean(KEY_OTP_NOTIFIED, false)
            .apply()
        
        return otp
    }

    fun verifyOtp(ctx: Context, inputOtp: String): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedOtp = prefs.getString(KEY_OTP, null)
        val ts = prefs.getLong(KEY_OTP_TS, 0L)
        val now = System.currentTimeMillis()

        if (savedOtp == null) return "No OTP generated."
        if (savedOtp != inputOtp.trim().uppercase()) return "Invalid OTP."

        val diff = now - ts
        if (diff < WAIT_TIME) {
            val remaining = (WAIT_TIME - diff) / 60000
            return "Too early. Wait $remaining minutes."
        }
        if (diff > EXPIRY_TIME) {
            return "OTP Expired. Generate a new one."
        }

        return "OK"
    }
}