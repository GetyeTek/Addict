package com.example.dnsguard

import android.content.Context
import android.provider.Settings

object DnsManager {

    // STRICT ADULT FILTER LIST
    val ALLOWED_HOSTNAMES = listOf(
        "family.cloudflare-dns.com",
        "dns-family.adguard.com",
        "family-filter-dns.cleanbrowsing.org"
    )
    
    // returns TRUE if safe, FALSE if unsafe
    fun isSecure(ctx: Context): Boolean {
        try {
            // 1. Check Mode: 'off', 'opportunistic' (Auto), or 'hostname' (Private)
            val mode = Settings.Global.getString(ctx.contentResolver, "private_dns_mode")
            
            // 2. Check Specifier: The actual URL
            val host = Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")
            
            // CONDITION: Mode MUST be 'hostname' AND host must be in the ALLOWED list
            return mode == "hostname" && !host.isNullOrBlank() && ALLOWED_HOSTNAMES.contains(host)
        } catch (e: Exception) {
            return false // Fail secure
        }
    }
}