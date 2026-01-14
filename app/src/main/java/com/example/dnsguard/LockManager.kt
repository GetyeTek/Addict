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



    fun isBlacklistedBrowser(ctx: Context, pkg: String): Boolean {
        // 0. Explicitly monitor Rogue Apps
        if (ROGUE_APPS.contains(pkg)) return true

        // 1. Whitelist Chrome Stable
        if (pkg == "com.android.chrome") return false

        // 2. Check Manual List
        if (MANUAL_BLACKLIST.contains(pkg)) return true

        // 3. Check Cache
        if (BROWSER_CACHE.containsKey(pkg)) return BROWSER_CACHE[pkg]!!

        // 4. Dynamic Check: Does it handle generic web URLs?
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        
        // MATCH_ALL (131072) ensures we see everything provided we have the permission
        val list = ctx.packageManager.queryIntentActivities(intent, 131072)
        val isBrowser = list.any { it.activityInfo.packageName == pkg }
        
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