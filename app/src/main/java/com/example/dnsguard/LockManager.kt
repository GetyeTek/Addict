package com.example.dnsguard

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object LockManager {

    private const val PREFS = "admin_prefs"
    private const val KEY_UNLOCK_TIME = "unlock_ts"
    private const val TIMEOUT_MS = 15 * 60 * 1000 // 15 Minutes

    // PASSWORD (Hardcoded for now)
    const val ADMIN_PASS = "1234"

    // BROWSER BLACKLIST (Whitelisted: Chrome)
    private val BROWSERS = setOf(
        "org.mozilla.firefox",
        "com.microsoft.emmx", // Edge
        "com.opera.browser",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "com.brave.browser",
        "com.duckduckgo.mobile.android",
        "com.UCMobile.intl", // UC
        "mobi.mbrowser", // Mint
        "com.vivaldi.browser",
        "org.torproject.torbrowser",
        "com.cloudmosa.puffinFree",
        "com.yandex.browser"
    )

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

    fun isBlacklistedBrowser(pkg: String): Boolean {
        // Allow Chrome (com.android.chrome), block others
        return BROWSERS.contains(pkg)
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