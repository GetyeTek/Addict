package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED || 
            intent.action == Intent.ACTION_PACKAGE_REMOVED) {
            GlobalScope.launch {
                AppCache.loadApps(context, force = true)
                DebugLogger.log("SYSTEM", "Package changed. Cache Refreshed.")
            }
        }
    }
}