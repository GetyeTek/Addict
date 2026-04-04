package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            if (action == Intent.ACTION_MY_PACKAGE_REPLACED) DebugLogger.log("SYSTEM", "Self-Update Detected. Restarting Guardian.")
            // GRACE PERIOD ACTIVE: We do NOT launch the lockdown overlay immediately.
            // We only ensure the services are primed.
            val i = Intent(context, WatcherService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }
}