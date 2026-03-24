package com.guardian.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object LockManager {

    private const val PREFS = "admin_prefs"
    private const val KEY_UNLOCK_TIME = "unlock_ts"
    private const val KEY_TG_BAN = "tg_ban_ts"
    private const val KEY_BROWSER_BAN = "browser_ban_ts"
    private const val TIMEOUT_MS = 5 * 60 * 1000 // 5 Minutes
    private const val BAN_MS = 10 * 60 * 1000 // 10 Minutes
    private const val BROWSER_BAN_MS = 5 * 60 * 1000 // 5 Minutes
    private const val NON_STD_BAN_MS = 30 * 60 * 1000 // 30 Minutes
    private const val KEY_NON_STD_BAN = "non_std_ban_ts"
    private const val KEY_LOCKOUT_END = "user_lockout_end_ts"
    private const val KEY_BREAK_END = "break_end_ts"
    private const val KEY_USAGE_ACCUMULATED = "usage_ms"
    private const val KEY_LAST_THRESHOLD = "last_threshold"
    private const val KEY_LADDER_ENABLED = "ladder_enabled"
    private const val KEY_NIGHT_PASSES = "night_pass_history"
    private const val KEY_PENALTY_END = "penalty_end_ts"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_PENALTY_NOTIFIED = "penalty_warned"
    private const val KEY_REBELLION_START = "rebellion_start_ts"
    private const val KEY_SAFE_PKG = "safe_pkg_name"
    private const val KEY_SAFE_TS = "safe_pkg_ts"
    private const val KEY_FIX_TS = "perm_fix_ts"
    private const val KEY_PERM_BANS = "perm_banned_apps"
    private const val KEY_TEMP_LOCKS = "temp_locked_apps"
    private const val KEY_DEEP_FOCUS_END = "deep_focus_end_ts"
    private const val KEY_DEEP_FOCUS_ALLOWED = "deep_focus_allowed_apps"
    private const val KEY_FIX_WINDOW_TS = "content_fix_ts"
    private const val KEY_WHISPER_ACTIVE = "whisper_mode_active"
    private const val KEY_WHISPER_START = "whisper_start_ts"
    private const val KEY_WHISPER_STEPS = "whisper_steps_count"
    private const val KEY_WHISPER_REFLEX = "whisper_is_reflex"
    private const val KEY_LEARNED_APPS = "learned_apps_map"
    private const val KEY_APPROVED_APPS = "approved_apps_set"
    private const val KEY_EXP_RESURRECT = "exp_resurrect"
    private const val KEY_EXP_NO_DNS_OVERLAY = "exp_no_dns_overlay"
    private const val KEY_EXP_NO_APP_INFO_KICK = "exp_no_app_info_kick"
    private const val KEY_EXP_NO_PERM_SCAN = "exp_no_perm_scan"
    private const val KEY_YT_GUARD_ENABLED = "yt_guard_enabled"
    private const val KEY_YT_REQUEST_TS = "yt_request_ts"
    private const val KEY_YT_ACCESS_TS = "yt_access_ts"
    private const val KEY_ALARM_TRIGGER_TS = "last_alarm_trigger_ts"
    private const val EXORCISM_MAX_DURATION = 30 * 60 * 1000L // 30 Minutes
    var currentActivePackage: String = ""

    // THRESHOLDS (Linear 20-120-20 Rule)
    val T1 = 20 * 60 * 1000L
    val T2 = 40 * 60 * 1000L
    val T3 = 60 * 60 * 1000L
    val T4 = 80 * 60 * 1000L
    val T_RESET = 90 * 60 * 1000L
    const val DAILY_LIMIT_MS = 9 * 60 * 60 * 1000L
    const val DAILY_WARN_MS = 8 * 60 * 60 * 1000L

    // APPS THAT DOUBLE-COUNT USAGE (System Overlays, Launchers, Keyboards)
    private val GHOST_PACKAGES = setOf(
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.sec.android.inputmethod",
        "com.apple.android.music",
        "com.google.android.gms",
        "com.android.vending",
        "com.google.android.projection.gearhead" // Android Auto
    )

    @Volatile
    var isVolumeUpHeld: Boolean = false

    // PASSWORD (Hardcoded for now)
    const val ADMIN_PASS = "1234"

    // WHITELIST: Standard Browsers (Subject to standard 5 min ban)
    val STANDARD_BROWSERS = setOf(
        "com.android.chrome", "com.chrome.canary", "com.chrome.dev",
        "com.kiwibrowser.browser", "com.microsoft.bing", "com.opera.browser",
        "com.sec.android.app.sbrowser", "org.mozilla.firefox",
        "com.google.android.googlequicksearchbox" // Moved from Blacklist to Whitelist
    )

    // BLACKLIST: Rogue Apps (Subject to immediate 30 min ban)
    val ROGUE_APPS = setOf(
        "com.snaptube.premium", "com.video.fun.app", "hesoft.T2S"
    )

    // DYNAMIC BROWSER DETECTION
    // Added Google App because it functions as a browser proxy
    // DYNAMIC BROWSER DETECTION
    // MAINTENANCE BLOCK LIST: These are blocked ONLY when Unlocked.
    private val MANUAL_BLACKLIST = setOf(
        // Social & Messaging (Browsers in disguise)
        "com.reddit.frontpage", 
        "com.discord", "com.tumblr", "sh.whisper", "tw.com.yellotalk",
        "com.kik.chat", "com.snapchat.android",
        
        // Alternative Browsers (Risky without DNS)
        "com.UCMobile.intl", "com.opera.mini.native", 
        "com.duckduckgo.mobile.android", "com.brave.browser",

        // Art / Creator (High Porn Risk)
        "com.patreon.android", "jp.pxv.android", "com.deviantart.android.main",
        
        // Dating (Standard)
        "com.tinder", "com.bumble.app", "co.hinge.app", "com.okcupid.okcupid", 
        "com.badoo.mobile", "co.feeld", "com.modest.hud",
        
        // Alt Video
        "com.kick.app", "com.rumble.v7", "com.bitchute.app",

        // Mixed Content / Softcore Risk (DNS cannot filter these)
        "com.instagram.android", "com.zhiliaoapp.musically", "com.pinterest",
        "com.facebook.katana", "com.facebook.lite",
        
        // Proxies & Bypass Tools
        "com.google.android.apps.translate"
    )
    private val BROWSER_CACHE = mutableMapOf<String, Boolean>()
    private const val KEY_FIX_USED = "fix_window_consumed"

    // BREAK IMMUNITY: Apps that won't be interrupted by usage breaks
    private val MEDIA_WHITELIST = setOf("video.player.videoplayer")

    // HARDCODED SAFE: Apps that bypass all WebView/Quarantine checks
    private val HARDCODED_SAFE_APPS = setOf(
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "com.google.android.apps.googleassistant",
        "cn.tydic.ethiopay",
        "com.osp.app.signin",
        "com.trm.tunnel",
        "io.spck",
        "com.imo.android.imoim",
        "com.flyersoft.moonreader",
        "com.google.android.gm"
    )

    fun isUnlocked(ctx: Context): Boolean {
        // TOGGLE 2: Silence DNS Warnings (Experimental)
        // If this toggle is on, we treat the app as perpetually "Unlocked"
        if (isExpEnabled(ctx, "no_dns")) return true

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0L)
        val now = System.currentTimeMillis()
        
        // Check 1: Time Expiry
        if (now - unlockTime > TIMEOUT_MS) {
            return false
        }

        return true
    }

    fun unlock(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_UNLOCK_TIME, System.currentTimeMillis()).apply()
    }

    fun lock(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_UNLOCK_TIME).apply()
    }

    fun banTelegram(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TG_BAN, System.currentTimeMillis()).apply()
    }

    fun isTelegramBanned(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_TG_BAN, 0L)
        val now = System.currentTimeMillis()
        return (now - start) < BAN_MS
    }

    fun banBrowser(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_BROWSER_BAN, System.currentTimeMillis())
            .putBoolean(KEY_FIX_USED, false)
            .apply()
    }

    fun isBrowserBanned(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_BROWSER_BAN, 0L)
        val now = System.currentTimeMillis()
        return (now - start) < BROWSER_BAN_MS
    }

    fun banNonStandardApp(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NON_STD_BAN, System.currentTimeMillis())
            .putBoolean(KEY_FIX_USED, false)
            .apply()
    }

    fun isNonStandardAppBanned(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_NON_STD_BAN, 0L)
        val now = System.currentTimeMillis()
        return (now - start) < NON_STD_BAN_MS
    }

    fun isNonStandardApp(ctx: Context, pkg: String): Boolean {
        // 1. If it's a known Standard Browser, it's SAFE (uses standard rules)
        if (STANDARD_BROWSERS.contains(pkg)) return false
        
        // 2. If it's a known Rogue App, it's NON-STANDARD
        if (ROGUE_APPS.contains(pkg)) return true
        
        // 3. If it's any other detected browser, it's NON-STANDARD
        return isBlacklistedBrowser(ctx, pkg)
    }

    fun setUserLockout(ctx: Context, minutes: Int) {
        // Clamp minutes between 0 and 1440 (24 hours)
        val safeMinutes = minutes.coerceIn(0, 1440)
        val endTime = System.currentTimeMillis() + (safeMinutes * 60 * 1000L)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LOCKOUT_END, endTime).apply()
    }

    fun getLockoutRemainingMillis(ctx: Context): Long {
        val endTime = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LOCKOUT_END, 0L)
        return (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isUserLockedOut(ctx: Context): Boolean {
        return getLockoutRemainingMillis(ctx) > 0
    }

    // LADDER IS NOW MANDATORY
    fun isLadderEnabled(ctx: Context): Boolean = true

    fun setLadderEnabled(ctx: Context, enabled: Boolean) { /* No-op: Feature is permanent */ }

    fun getBreakRemaining(ctx: Context): Long {
        val end = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_BREAK_END, 0L)
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun getAccumulatedUsage(ctx: Context): Long {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_USAGE_ACCUMULATED, 0L)
    }

    fun isSettlingActive(): Boolean {
        // Phase 1: 0-10 Minutes
        return android.os.SystemClock.elapsedRealtime() < 10 * 60 * 1000
    }

    fun isStrictGraceActive(): Boolean {
        // Phase 2: 10-20 Minutes
        return android.os.SystemClock.elapsedRealtime() < 20 * 60 * 1000
    }

    fun isBootGraceActive(): Boolean {
        return isStrictGraceActive()
    }

    fun isBerserkerActive(): Boolean {
        // 10 Minutes = 600,000 ms
        return android.os.SystemClock.elapsedRealtime() < 10 * 60 * 1000
    }

    // --- NIGHT PASS LOGIC ---

    private fun getNightPassTimestamps(ctx: Context): List<Long> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NIGHT_PASSES, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    fun getRemainingNightPasses(ctx: Context): Int {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val recent = getNightPassTimestamps(ctx).filter { it > weekAgo }
        return (3 - recent.size).coerceAtLeast(0)
    }

    fun isNightPassActivationWindow(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isOpen = hour in 6..17 // 6 AM to 6 PM (Synchronized with Nuke)
        return isOpen
    }

    fun isTonightPassed(ctx: Context): Boolean {
        val cal = java.util.Calendar.getInstance()
        // If it's before 5 AM, we are checking for the pass used yesterday
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 5) cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        
        val yearDay = "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.DAY_OF_YEAR)}"
        val stamps = getNightPassTimestamps(ctx)
        
        return stamps.any { 
            val passCal = java.util.Calendar.getInstance()
            passCal.timeInMillis = it
            "${passCal.get(java.util.Calendar.YEAR)}-${passCal.get(java.util.Calendar.DAY_OF_YEAR)}" == yearDay
        }
    }

    fun useNightPass(ctx: Context): Boolean {
        if (!isNightPassActivationWindow() || getRemainingNightPasses(ctx) <= 0) return false
        val stamps = getNightPassTimestamps(ctx).toMutableList()
        stamps.add(System.currentTimeMillis())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NIGHT_PASSES, stamps.joinToString(",")).apply()
        return true
    }

    fun isNightLockActive(ctx: Context): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isNightTime = hour >= 23 || hour < 5
        return isNightTime && !isTonightPassed(ctx)
    }

    fun setSetupComplete(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    fun isSetupComplete(ctx: Context): Boolean = 
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SETUP_COMPLETE, false)

    fun triggerPenalty(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasServedInitial = prefs.getLong("total_penalties_served", 0L) > 0
        
        val duration = if (!hasServedInitial) 60 * 60 * 1000L else 15 * 60 * 1000L
        val end = System.currentTimeMillis() + duration
        
        prefs.edit()
            .putLong(KEY_PENALTY_END, end)
            .putBoolean(KEY_PENALTY_NOTIFIED, false)
            .putLong("total_penalties_served", prefs.getLong("total_penalties_served", 0L) + 1)
            .apply()
        
        DebugLogger.log("PENALTY", "Started ${if(!hasServedInitial) "1h" else "15m"} penalty.")
    }

    fun isSystemCompromised(ctx: Context): Boolean {
        val hasBattery = (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
        val hasAdmin = (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager).isAdminActive(android.content.ComponentName(ctx, AdminReceiver::class.java))
        val hasOverlay = android.provider.Settings.canDrawOverlays(ctx)
        
        // 1. Check Global Notification Switch
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val areGlobalNotifsEnabled = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        
        // 2. Check Specific Mandatory Channels (Android 8.0+)
        var areChannelsEnabled = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mandatoryChannels = listOf("dns_guard_channel", "watcher_channel")
            for (id in mandatoryChannels) {
                val channel = nm.getNotificationChannel(id)
                // If channel exists and importance is NONE, it's blocked
                if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
                    areChannelsEnabled = false
                    break
                }
            }
        }

        val expected = "${ctx.packageName}/${GuardService::class.java.canonicalName}"
        val enabledServices = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val hasAccessibility = enabledServices.contains(expected)

        // EXORCIST DEFENSE: Activity Recognition (Android 10+)
        val hasActivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        // EXORCIST DEFENSE: DND Access (Android 6.0+)
        val hasDndAccess = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            nm.isNotificationPolicyAccessGranted
        } else true

        return !hasBattery || !hasAdmin || !hasAccessibility || !hasOverlay || !areGlobalNotifsEnabled || !areChannelsEnabled || !hasActivity || !hasDndAccess
    }

    fun setPenaltyWarned(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_PENALTY_NOTIFIED, true).apply()
    }

    fun wasPenaltyWarned(ctx: Context): Boolean = 
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PENALTY_NOTIFIED, false)



    fun getPenaltyRemaining(ctx: Context): Long {
        val end = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_PENALTY_END, 0L)
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun clearPenalty(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_PENALTY_END)
            .remove(KEY_PENALTY_NOTIFIED)
            .apply()
    }

    fun startRebellion(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(KEY_REBELLION_START, 0L) == 0L) {
            prefs.edit().putLong(KEY_REBELLION_START, System.currentTimeMillis()).apply()
        }
    }

    fun clearRebellion(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_REBELLION_START).apply()
    }

    fun getRebellionTime(ctx: Context): Long {
        val start = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_REBELLION_START, 0L)
        if (start == 0L) return 0L
        return System.currentTimeMillis() - start
    }

    fun setSafeSession(ctx: Context, pkg: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SAFE_PKG, pkg)
            .putLong(KEY_SAFE_TS, System.currentTimeMillis())
            .apply()
    }

    fun isSafeSession(ctx: Context, pkg: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val safePkg = prefs.getString(KEY_SAFE_PKG, "")
        val safeTs = prefs.getLong(KEY_SAFE_TS, 0L)
        val now = System.currentTimeMillis()
        
        if (pkg == "ANY") return (now - safeTs < 60000)
        return safePkg == pkg && (now - safeTs < 60000)
    }

    fun updateActivePackage(pkg: String) {
        currentActivePackage = pkg
    }

    fun cleanupExpiredLocks(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val temp = prefs.getStringSet(KEY_TEMP_LOCKS, emptySet())?.toMutableSet() ?: return
        val now = System.currentTimeMillis()
        if (temp.removeIf { it.contains(":") && (it.substringAfter(":").toLongOrNull() ?: 0L) < now }) {
            prefs.edit().putStringSet(KEY_TEMP_LOCKS, temp).apply()
        }
    }

    fun getStatusLine(ctx: Context): String {
        val lockout = getLockoutRemainingMillis(ctx)
        if (lockout > 0) return "Focus: ${lockout / 60000}m left"
        
        val deep = getDeepFocusRemaining(ctx)
        if (deep > 0) return "Zen Mode: ${deep / 60000}m left"
        
        val brk = getBreakRemaining(ctx)
        if (brk > 0) return "Break: ${brk / 60000}m left"
        
        val pen = getPenaltyRemaining(ctx)
        if (pen > 0) return "Penalty Box: ${pen / 60000}m left"

        if (isUnlocked(ctx)) return "DNS REPAIR: 5 MIN WINDOW"
        
        return "I AM IN CONTROL."
    }

    fun startPermissionFixSession(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_FIX_TS, System.currentTimeMillis()).apply()
    }

    fun isPermissionFixActive(ctx: Context): Boolean {
        val lastFix = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_FIX_TS, 0L)
        val now = System.currentTimeMillis()
        // Blind Grace Window: Allow 15 seconds for the system to transition to Settings
        return (now - lastFix < 15000)
    }

    fun resetUsage(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_USAGE_ACCUMULATED, 0L)
            .putInt(KEY_LAST_THRESHOLD, 0)
            .apply()
    }

    fun updateUsageAndCheckBreak(ctx: Context, deltaMs: Long): Boolean {
        if (!isLadderEnabled(ctx)) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        
        var usage = prefs.getLong(KEY_USAGE_ACCUMULATED, 0L) + deltaMs
        var lastT = prefs.getInt(KEY_LAST_THRESHOLD, 0)
        
        var breakMs = 0L
        var newT = 0

        when {
            // 90 Minute Cycle Reset (5 Minute Break)
            usage >= T_RESET -> { breakMs = 5 * 60 * 1000L; usage = 0; newT = 0 }
            // 80 Minute Interval (2 Minute Break)
            usage >= T4 && lastT < 80 -> { breakMs = 2 * 60 * 1000L; newT = 80 }
            // 60 Minute Interval (2 Minute Break)
            usage >= T3 && lastT < 60 -> { breakMs = 2 * 60 * 1000L; newT = 60 }
            // 40 Minute Interval (2 Minute Break)
            usage >= T2 && lastT < 40 -> { breakMs = 2 * 60 * 1000L; newT = 40 }
            // 20 Minute Interval (2 Minute Break)
            usage >= T1 && lastT < 20 -> { breakMs = 2 * 60 * 1000L; newT = 20 }
        }

        val editor = prefs.edit().putLong(KEY_USAGE_ACCUMULATED, usage)
        if (breakMs > 0) {
            editor.putLong(KEY_BREAK_END, System.currentTimeMillis() + breakMs)
            editor.putInt(KEY_LAST_THRESHOLD, newT)
            editor.apply()
            return true
        }
        editor.apply()
        return false
    }



    fun isBlacklistedBrowser(ctx: Context, pkg: String): Boolean {
        // 1. Fast path: Static checks
        if (pkg == "com.android.chrome") return true
        if (ROGUE_APPS.contains(pkg)) return true
        if (MANUAL_BLACKLIST.contains(pkg)) return true

        // 2. Cache path: Avoid PM query
        BROWSER_CACHE[pkg]?.let { return it }

        // 3. Slow path: Package Manager Query (Sync or first time detection)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        
        // Query PM safely
        val isBrowser = try {
            val list = ctx.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
            list.any { it.activityInfo.packageName == pkg }
        } catch (e: Exception) { false }
        
        BROWSER_CACHE[pkg] = isBrowser
        return isBrowser
    }

    // DEBUG TOOL: Returns list of all apps acting as browsers
    fun getDetectedBrowsers(ctx: Context): List<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val list = ctx.packageManager.queryIntentActivities(intent, 131072)
        return list.map { it.activityInfo.packageName }.distinct().sorted()
    }

    fun banAppPermanently(ctx: Context, pkgs: Set<String>) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_PERM_BANS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(pkgs)
        prefs.edit().putStringSet(KEY_PERM_BANS, current).apply()
    }

    fun lockAppsTemporarily(ctx: Context, pkgs: Set<String>, minutes: Int) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_TEMP_LOCKS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val expiry = System.currentTimeMillis() + (minutes * 60 * 1000L)
        pkgs.forEach { pkg ->
            current.removeIf { it.startsWith("$pkg:") }
            current.add("$pkg:$expiry")
        }
        prefs.edit().putStringSet(KEY_TEMP_LOCKS, current).apply()
    }

    fun startDeepFocus(ctx: Context, allowedPkgs: Set<String>, minutes: Int) {
        val end = System.currentTimeMillis() + (minutes * 60 * 1000L)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DEEP_FOCUS_END, end)
            .putStringSet(KEY_DEEP_FOCUS_ALLOWED, allowedPkgs)
            .apply()
    }

    fun getDeepFocusRemaining(ctx: Context): Long {
        val end = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_DEEP_FOCUS_END, 0L)
        return (end - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun startFixWindow(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_FIX_WINDOW_TS, System.currentTimeMillis())
            .putBoolean(KEY_FIX_USED, true)
            .apply()
    }

    fun isFixWindowActive(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fixTs = prefs.getLong(KEY_FIX_WINDOW_TS, 0L)
        return (System.currentTimeMillis() - fixTs < 60000)
    }

    fun shouldShowFixButton(ctx: Context, type: String): Boolean {
        if (type != "BROWSER_VIOLATION" && type != "ROGUE_VIOLATION") return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !prefs.getBoolean(KEY_FIX_USED, false)
    }

    fun registerLearnedApp(ctx: Context, pkg: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val learnedRaw = prefs.getString(KEY_LEARNED_APPS, "{}") ?: "{}"
        val learnedMap = org.json.JSONObject(learnedRaw)
        
        if (!learnedMap.has(pkg)) {
            learnedMap.put(pkg, System.currentTimeMillis())
            prefs.edit().putString(KEY_LEARNED_APPS, learnedMap.toString()).apply()
            DebugLogger.log("QUARANTINE", "New Web App Detected: $pkg. Initiating 1h isolation.")
        }
    }

    fun getLearnedApps(ctx: Context): Map<String, Long> {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val learnedRaw = prefs.getString(KEY_LEARNED_APPS, "{}") ?: "{}"
        val learnedMap = org.json.JSONObject(learnedRaw)
        val result = mutableMapOf<String, Long>()
        val keys = learnedMap.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            result[k] = learnedMap.getLong(k)
        }
        return result
    }

    fun approveApp(ctx: Context, pkg: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val approved = prefs.getStringSet(KEY_APPROVED_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        approved.add(pkg)
        prefs.edit().putStringSet(KEY_APPROVED_APPS, approved).apply()
    }

    fun isAppApproved(ctx: Context, pkg: String): Boolean {
        if (isHardcodedSafe(pkg)) return true
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_APPROVED_APPS, emptySet())?.contains(pkg) == true
    }

    fun isAppPermanentlyBanned(ctx: Context, pkg: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PERM_BANS, emptySet())?.contains(pkg) == true
    }

    fun isHardcodedSafe(pkg: String): Boolean {
        return HARDCODED_SAFE_APPS.contains(pkg)
    }

    fun getQuarantineRemaining(ctx: Context, pkg: String): Long {
        val learned = getLearnedApps(ctx)
        val detectTime = learned[pkg] ?: return 0L
        val hour = 60 * 60 * 1000L
        return (detectTime + hour - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun isExpEnabled(ctx: Context, key: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when(key) {
            "resurrect" -> prefs.getBoolean(KEY_EXP_RESURRECT, false)
            "no_dns" -> prefs.getBoolean(KEY_EXP_NO_DNS_OVERLAY, false)
            "no_app_info" -> prefs.getBoolean(KEY_EXP_NO_APP_INFO_KICK, false)
            "no_perm_scan" -> prefs.getBoolean(KEY_EXP_NO_PERM_SCAN, false)
            else -> false
        }
    }

    fun setExpEnabled(ctx: Context, key: String, enabled: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val k = when(key) {
            "resurrect" -> KEY_EXP_RESURRECT
            "no_dns" -> KEY_EXP_NO_DNS_OVERLAY
            "no_app_info" -> KEY_EXP_NO_APP_INFO_KICK
            "no_perm_scan" -> KEY_EXP_NO_PERM_SCAN
            else -> return
        }
        prefs.edit().putBoolean(k, enabled).apply()
    }

    fun getYouTubeBlockStatus(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_YT_GUARD_ENABLED, false)
        if (!enabled) return "YT_REQUEST_REQUIRED"

        val requestTs = prefs.getLong(KEY_YT_REQUEST_TS, 0L)
        val now = System.currentTimeMillis()
        val waitLeft = (requestTs + 30 * 60 * 1000L) - now

        if (waitLeft > 0) return "YT_WAITING"

        // AUTHORIZED WINDOW CHECK
        var accessTs = prefs.getLong(KEY_YT_ACCESS_TS, 0L)
        if (accessTs == 0L) {
            accessTs = now
            prefs.edit().putLong(KEY_YT_ACCESS_TS, now).apply()
        }

        val accessLeft = (accessTs + 60 * 60 * 1000L) - now
        if (accessLeft <= 0) {
            // CYCLE EXPIRED: Reset to wait phase
            prefs.edit()
                .putLong(KEY_YT_REQUEST_TS, now)
                .putLong(KEY_YT_ACCESS_TS, 0L)
                .apply()
            return "YT_WAITING"
        }

        return null // ENJOY YOUR 1 HOUR
    }

    fun setYouTubeGuard(ctx: Context, enabled: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_YT_GUARD_ENABLED, enabled)
        if (enabled) {
            editor.putLong(KEY_YT_REQUEST_TS, System.currentTimeMillis())
        } else {
            editor.putLong(KEY_YT_REQUEST_TS, 0L)
            editor.putLong(KEY_YT_ACCESS_TS, 0L)
        }
        editor.apply()
    }

    var lastNagTs: Long = 0

    fun getNextPermissionIntent(ctx: Context): android.content.Intent? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComp = android.content.ComponentName(ctx, AdminReceiver::class.java)

        // 1. Accessibility (The Core)
        val expected = "${ctx.packageName}/${GuardService::class.java.canonicalName}"
        val enabledServices = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (!enabledServices.contains(expected)) return android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)

        // 2. Overlay
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            return android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
        }

        // 3. Battery
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
            return android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${ctx.packageName}"))
        }

        // 4. Admin
        if (!dpm.isAdminActive(adminComp)) {
            return android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            }
        }

        // 5. Notifications (DND Access & Channel check)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
            return android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }

        return null
    }

    fun getDailyUsage(ctx: Context): Long {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val events = usm.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()
        
        var totalUsage = 0L
        var lastEventTime = startTime
        var currentForegroundPkg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            
            // Calculate time elapsed since last event for the PREVIOUSLY active app
            val timeDelta = event.timeStamp - lastEventTime
            
            if (currentForegroundPkg != null && 
                currentForegroundPkg != ctx.packageName &&
                !GHOST_PACKAGES.any { currentForegroundPkg!!.contains(it) }) {
                totalUsage += timeDelta
            }

            lastEventTime = event.timeStamp

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    currentForegroundPkg = event.packageName
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    // If the app we were tracking just went to background, stop tracking
                    if (event.packageName == currentForegroundPkg) {
                        currentForegroundPkg = null
                    }
                }
                android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE -> { /* Potentially useful for SOT sync */ }
                android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    currentForegroundPkg = null // Stop counting when screen is off
                }
            }
        }
        
        // Catch the very last slice of time from the last event until 'now'
        if (currentForegroundPkg != null && 
            currentForegroundPkg != ctx.packageName &&
            !GHOST_PACKAGES.any { currentForegroundPkg!!.contains(it) }) {
            totalUsage += (endTime - lastEventTime)
        }

        return totalUsage
    }

    fun logUsageBreakdown(ctx: Context) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        
        val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, calendar.timeInMillis, System.currentTimeMillis())
        val sb = StringBuilder()
        sb.append("\n--- HEAVY USAGE AUDIT (8HR MARK) ---\n")
        
        stats.filter { it.totalTimeInForeground > 0 && it.packageName != ctx.packageName }
             .sortedByDescending { it.totalTimeInForeground }
             .forEach { stat ->
                 val mins = stat.totalTimeInForeground / 60000
                 val isGhost = GHOST_PACKAGES.any { stat.packageName.contains(it) }
                 sb.append("${if (isGhost) "[IGNORED]" else "[COUNTED]"} ${stat.packageName}: ${mins}m\n")
             }
        
        val totalMins = getDailyUsage(ctx) / 60000
        sb.append("TOTAL CALCULATED USAGE: ${totalMins}m\n")
        sb.append("-----------------------------------")
        DebugLogger.log("MATH_AUDIT", sb.toString())
    }

    fun isEmergencyApp(pkg: String): Boolean {
        if (pkg.isBlank()) return false
        val p = pkg.lowercase()
        val match = p.contains("dialer") || 
               p.contains("telecom") || 
               p.contains("incallui") || 
               p.contains("clock") || 
               p.contains("alarm") ||
               p.contains("contacts") ||
               p.contains("phone") ||
               p.contains("emergency") ||
               p.contains("messaging") || 
               p.contains("stk")
        
        if (match) DebugLogger.log("EMERGENCY_MATCH", "Package $pkg validated as Emergency Bypass")
        return match
    }

    fun startWhisperMode(ctx: Context, isReflex: Boolean = false) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WHISPER_ACTIVE, true)
            .putLong(KEY_WHISPER_START, System.currentTimeMillis())
            .putInt(KEY_WHISPER_STEPS, 0)
            .putBoolean(KEY_WHISPER_REFLEX, isReflex)
            .apply()
    }

    fun isWhisperReflex(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WHISPER_REFLEX, false)

    fun stopWhisperMode(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_WHISPER_ACTIVE, false)
            .apply()
    }

    fun isWhisperMode(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_WHISPER_ACTIVE, false)
        if (!active) return false

        val triggerTs = prefs.getLong(KEY_ALARM_TRIGGER_TS, 0L)
        
        // HEALING LOGIC: If timestamp is missing (old version) or expired, kill the protocol.
        if (triggerTs == 0L || (System.currentTimeMillis() - triggerTs > EXORCISM_MAX_DURATION)) {
            DebugLogger.log("ALARM_HEAL", "Stale or missing timestamp detected. Purging Ghost Alarm.")
            prefs.edit().putBoolean(KEY_WHISPER_ACTIVE, false).apply()
            return false
        }
        return true
    }
    
    fun getWhisperSteps(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_WHISPER_STEPS, 0)
    
    fun addWhisperStep(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_WHISPER_STEPS, 0)
        prefs.edit().putInt(KEY_WHISPER_STEPS, current + 1).apply()
    }

    fun setAlarmTriggerTs(ctx: Context, ts: Long) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ALARM_TRIGGER_TS, ts).apply()
    }

    fun getWhisperElapsed(ctx: Context): Long {
        val start = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_WHISPER_START, 0L)
        return System.currentTimeMillis() - start
    }

    fun getActiveBlockType(ctx: Context, pkg: String = ""): String? {
        // NEUTRALITY GUARD: If package is unknown/empty (during transitions), do not block.
        if (pkg.isBlank()) return null

        // 0. EMERGENCY BYPASS (Highest Priority)
        if (isEmergencyApp(pkg)) return null

        // 0.05 YOUTUBE PROTOCOL
        if (pkg == "com.google.android.youtube" || pkg == "com.google.android.apps.youtube.kids") {
            val ytBlock = getYouTubeBlockStatus(ctx)
            if (ytBlock != null) return ytBlock
        }

        // 0.1 DAILY QUOTA (The 9-Hour Executioner)
        if (getDailyUsage(ctx) > DAILY_LIMIT_MS) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val isNightTime = hour >= 23 || hour < 5
            if (!(isNightTime && isTonightPassed(ctx))) {
                return "DAILY_LIMIT_EXCEEDED"
            }
        }

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 0.5 CONTENT FIX WINDOW (60s Grace)
        if (isFixWindowActive(ctx)) return null

        // 0.6 MAINTENANCE DISCIPLINE: Only Chrome allowed for DNS lookup
        // Even if not a standard browser, Google Search can show explicit images/feeds.
        val isGoogleSearch = pkg == "com.google.android.googlequicksearchbox"
        if (isUnlocked(ctx) && pkg != "com.android.chrome" && (isBlacklistedBrowser(ctx, pkg) || isGoogleSearch)) {
            return "MAINTENANCE_BROWSER_ILLEGAL"
        }

        // 1. HARD BLOCKERS (Never bypassed)
        val permBans = prefs.getStringSet(KEY_PERM_BANS, emptySet()) ?: emptySet()
        if (permBans.contains(pkg)) return "PERMANENT_BAN"

        if (getPenaltyRemaining(ctx) > 0) return "PENALTY"

        // 2. SYSTEM BYPASSES (Maintenance/Setup)
        if (isPermissionFixActive(ctx)) return null

        // 3. DNS ENFORCEMENT (The ONLY block that respects isUnlocked)
        if (!DnsManager.isSecure(ctx)) {
            if (!isUnlocked(ctx)) return "SYSTEM"
        }

        // 4. USER/CONTENT ENFORCEMENT (Bypassed only by Nuke)
        if (isNonStandardAppBanned(ctx) && isNonStandardApp(ctx, pkg)) return "ROGUE_VIOLATION"
        if (isBrowserBanned(ctx) && isBlacklistedBrowser(ctx, pkg)) return "BROWSER_VIOLATION"
        if (isTelegramBanned(ctx) && (pkg.contains("telegram") || pkg.contains("challegram"))) return "TELEGRAM_SUSPENDED"

        // 5. DEEP FOCUS / ZEN MODE
        val deepFocusEnd = prefs.getLong(KEY_DEEP_FOCUS_END, 0L)
        if (System.currentTimeMillis() < deepFocusEnd) {
            val allowed = prefs.getStringSet(KEY_DEEP_FOCUS_ALLOWED, emptySet()) ?: emptySet()
            val isSystem = pkg.contains("launcher") || pkg.contains("systemui") || pkg.contains("packageinstaller") || pkg.contains("settings") || pkg.contains("accessibility")
            val isAllowed = allowed.contains(pkg) || pkg == ctx.packageName || isSystem
            if (!isAllowed && pkg.isNotEmpty()) return "DEEP_FOCUS"
        }

        // 6. FOCUS / LADDER / NIGHT LOCK
        if (isUserLockedOut(ctx)) return "USER_LOCKOUT"
        if (getBreakRemaining(ctx) > 0) {
            if (MEDIA_WHITELIST.contains(pkg)) return null
            return "BREAK_TIME"
        }
        if (isWhisperMode(ctx)) {
            val elapsed = getWhisperElapsed(ctx)
            return if (elapsed > 60000) "WHISPER_PROTOCOL" else "EXORCISM_COUNTDOWN"
        }
        if (isNightLockActive(ctx)) return "NIGHT_LOCK"

        // QUARANTINE LOGIC
        val learnedApps = getLearnedApps(ctx)
        if (learnedApps.containsKey(pkg)) {
            if (!isAppApproved(ctx, pkg)) {
                return if (getQuarantineRemaining(ctx, pkg) > 0) "QUARANTINE" else "PENDING_APPROVAL"
            }
        }

        val tempLocks = prefs.getStringSet(KEY_TEMP_LOCKS, emptySet()) ?: emptySet()
        val entry = tempLocks.find { it.startsWith("$pkg:") }
        if (entry != null) {
            val expiry = entry.substringAfter(":").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() < expiry) return "MANUAL_LOCK"
        }

        return null
    }

}