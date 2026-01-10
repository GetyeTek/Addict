package com.example.myandroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class MonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(777, createNotification())
        startTrapLoop()
        return START_STICKY
    }

    private fun startTrapLoop() {
        scope.launch {
            while (isActive) {
                // 1. Check DNS Mode
                val mode = Settings.Global.getString(contentResolver, "private_dns_mode")
                // 'hostname' is the value for Custom Private DNS provider
                val isSecure = !mode.isNullOrEmpty() && mode == "hostname"

                if (!isSecure) {
                    // 2. TRAP ACTIVATED
                    // Check where the user is right now
                    val topPkg = getTopPackage()
                    
                    // If they are NOT in Settings, throw them there.
                    // This includes Home Screen, Recents, or any other app.
                    if (topPkg != "com.android.settings") {
                        launchSettingsTrap()
                    }
                }
                delay(300) // Aggressive Check (300ms)
            }
        }
    }

    private fun launchSettingsTrap() {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } catch(e: Exception) {
            // Fallback for some devices
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private fun getTopPackage(): String {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            // Look back 10 seconds
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
            return stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    private fun createNotification(): Notification {
        val channelId = "policy_service"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "System Policy", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Network Policy Active")
            .setContentText("Enforcing DNS Compliance")
            .setSmallIcon(android.R.drawable.stat_sys_secure)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        // Restart if killed
        sendBroadcast(Intent(this, BootReceiver::class.java))
    }
}