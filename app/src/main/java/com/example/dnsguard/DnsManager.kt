package com.example.dnsguard

import android.content.Context
import android.provider.Settings

object DnsManager {
    
    // returns TRUE if safe, FALSE if unsafe
    fun isSecure(ctx: Context): Boolean {
        try {
            // 1. Check Mode: 'off', 'opportunistic' (Auto), or 'hostname' (Private)
            val mode = Settings.Global.getString(ctx.contentResolver, "private_dns_mode")
            
            // 2. Check Specifier: The actual URL
            val host = Settings.Global.getString(ctx.contentResolver, "private_dns_specifier")
            
            // CONDITION: Mode MUST be 'hostname' AND host must not be empty
            return mode == "hostname" && !host.isNullOrBlank()
        } catch (e: Exception) {
            return false // Fail secure
        }
    }
}