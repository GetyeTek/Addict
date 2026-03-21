package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DebugLogger.log("ALARM", "WAKE UP! Triggering Ringer.")
        val i = Intent(context, AlarmRingerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_FULLSCREEN)
        }
        context.startActivity(i)
    }
}