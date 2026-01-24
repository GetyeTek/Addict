package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {
    private val KILL_NUMBERS = setOf("0947370726", "+251947370726")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            if (state == TelephonyManager.EXTRA_STATE_RINGING && incomingNumber != null) {
                if (KILL_NUMBERS.any { incomingNumber.contains(it) }) {
                    executeEmergencyKill(context)
                }
            }
        }
    }

    private fun executeEmergencyKill(context: Context) {
        context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("nuke_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("protection_disabled", true)
            .putLong("protection_disabled_ts", System.currentTimeMillis())
            .apply()

        val home = Intent(Intent.ACTION_MAIN)
        home.addCategory(Intent.CATEGORY_HOME)
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(home)
        
        DebugLogger.log("EMERGENCY", "Remote Kill Signal Received. Protection Dropped.")
    }
}