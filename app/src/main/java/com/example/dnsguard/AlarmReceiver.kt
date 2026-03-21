package com.guardian.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_EXORCISE") {
            DebugLogger.log("ALARM", "Grace Period Expired. Starting Exorcist.")
            LockManager.startWhisperMode(context)
            return
        }

        DebugLogger.log("ALARM", "WAKE UP! Triggering Ringer.")
        val i = Intent(context, AlarmRingerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_FULLSCREEN)
        }
        context.startActivity(i)
        // Reschedule next recurring alarm if needed
        AlarmScheduler.scheduleNext(context)
    }
}