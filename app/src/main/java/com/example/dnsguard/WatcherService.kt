package com.guardian.net

import android.app.*
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.os.IBinder
import kotlinx.coroutines.*

class WatcherService : Service() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(99, createNotification())
        
        scope.launch {
            while (isActive) {
                val hasAcc = isAccessibilityEnabled(applicationContext)
                
                if (!hasAcc && LockManager.isSetupComplete(applicationContext)) {
                    // 1. Mark defiance start
                    LockManager.startRebellion(applicationContext)
                    
                    // 2. 15 Minute Hammer
                    if (LockManager.getRebellionTime(applicationContext) > 15 * 60 * 1000L) {
                        LockManager.triggerPenalty(applicationContext, isInitial = false)
                        LockManager.clearRebellion(applicationContext)
                    }

                    // 3. THE YANK
                    val yankIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    yankIntent.data = Uri.parse("package:$packageName")
                    yankIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    try {
                        startActivity(yankIntent)
                    } catch (e: Exception) {
                        // If Overlay settings fail, try general Accessibility settings
                        val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(fallback)
                    }
                } else {
                    LockManager.clearRebellion(applicationContext)
                }
                
                delay(1500)
            }
        }
        return START_STICKY
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = "${ctx.packageName}/${GuardService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    private fun createNotification(): Notification {
        val channelId = "watcher_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Guardian Monitor", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(chan)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Integrity Active")
            .setContentText("Guardian is verifying security permissions.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}