package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 1. If we are an accessibility service, the system usually restarts us automatically.
            // However, we can nudge the Overlay/Lockdown activity if protection is needed immediately.
            
            if (!DnsManager.isSecure(context) && !LockManager.isUnlocked(context)) {
                 val i = Intent(context, LockdownActivity::class.java)
                 i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                 context.startActivity(i)
            }
        }
    }
}