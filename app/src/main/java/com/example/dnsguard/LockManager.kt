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
    var currentActivePackage: String = ""

    // THRESHOLDS
    val T1 = 20 * 60 * 1000L
    val T2 = 40 * 60 * 1000L
    val T3 = 60 * 60 * 1000L
    val T4 = 90 * 60 * 1000L

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

    fun isUnlocked(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val unlockTime = prefs.getLong(KEY_UNLOCK_TIME, 0L)
        val now = System.currentTimeMillis()
        
        // Check 1: Time Expiry
        if (now - unlockTime > TIMEOUT_MS) {
            return false
        }

        // Check 2: VPN Tripwire (If VPN is on, we force lock immediately)
        if (isVpnActive(ctx)) {
            lock(ctx)
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
            .edit().putLong(KEY_BROWSER_BAN, System.currentTimeMillis()).apply()
    }

    fun isBrowserBanned(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getLong(KEY_BROWSER_BAN, 0L)
        val now = System.currentTimeMillis()
        return (now - start) < BROWSER_BAN_MS
    }

    fun banNonStandardApp(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_NON_STD_BAN, System.currentTimeMillis()).apply()
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

    fun isBootGraceActive(): Boolean {
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
        return hour in 10..17 // 10 AM to 6 PM
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

        return !hasBattery || !hasAdmin || !hasAccessibility || !hasOverlay || !areGlobalNotifsEnabled || !areChannelsEnabled
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
        val isRecent = (now - lastFix < 15000) // 15 second window
        
        // Only active if we are actually in Settings or the Package Installer
        val inSettings = currentActivePackage.contains("settings") || 
                         currentActivePackage.contains("packageinstaller")
        
        return isRecent && inSettings
    }

    fun updateUsageAndCheckBreak(ctx: Context, deltaMs: Long): Boolean {
        if (!isLadderEnabled(ctx)) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var usage = prefs.getLong(KEY_USAGE_ACCUMULATED, 0L) + deltaMs
        var lastT = prefs.getInt(KEY_LAST_THRESHOLD, 0)
        
        var breakMs = 0L
        var newT = 0

        when {
            usage >= 90 * 60 * 1000L -> { breakMs = 10 * 60 * 1000L; usage = 0; newT = 0 }
            usage >= 60 * 60 * 1000L && lastT < 60 -> { breakMs = 5 * 60 * 1000L; newT = 60 }
            usage >= 40 * 60 * 1000L && lastT < 40 -> { breakMs = 3 * 60 * 1000L; newT = 40 }
            usage >= 20 * 60 * 1000L && lastT < 20 -> { breakMs = 30 * 1000L; newT = 20 }
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
        if (pkg == "com.android.chrome") return false
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

    fun isEmergencyApp(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("dialer") || 
               p.contains("telecom") || 
               p.contains("incallui") || 
               p.contains("clock") || 
               p.contains("alarm") ||
               p.contains("contacts") ||
               p == "com.android.phone"
    }

    fun getActiveBlockType(ctx: Context, pkg: String = ""): String? {
        // 0. EMERGENCY BYPASS (Highest Priority)
        if (isEmergencyApp(pkg)) return null

        // 1. HARD BLOCKERS (Never bypassed)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
        if (getBreakRemaining(ctx) > 0) return "BREAK_TIME"
        if (isNightLockActive(ctx)) return "NIGHT_LOCK"

        val tempLocks = prefs.getStringSet(KEY_TEMP_LOCKS, emptySet()) ?: emptySet()
        val entry = tempLocks.find { it.startsWith("$pkg:") }
        if (entry != null) {
            val expiry = entry.substringAfter(":").toLongOrNull() ?: 0L
            if (System.currentTimeMillis() < expiry) return "MANUAL_LOCK"
        }

        return null
    }

    private fun isVpnActive(ctx: Context): Boolean {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            return false
        }
    }
}