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
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISABLED, disabled)
            .putLong(KEY_DISABLED_TS, now)
            .apply()
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
        // 1. Check Auto Time Zone
        val autoTime = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0)
        if (autoTime != 1) return "ERROR: Automatic Time Zone must be enabled."

        // 2. Check Time Window (6 AM - 6 PM)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour < 6 || hour >= 18) return "ERROR: Protocol only available between 06:00 and 18:00."

        return "OK"
    }

    fun generateOtp(ctx: Context): String {
        val otp = UUID.randomUUID().toString().substring(0, 6).uppercase()
        val now = System.currentTimeMillis()
        
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_OTP, otp)
            .putLong(KEY_OTP_TS, now)
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