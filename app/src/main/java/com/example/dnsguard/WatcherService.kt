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
                // Respect Master Key / Nuke status
                if (NukeManager.isProtectionDisabled(applicationContext)) {
                    LockManager.clearRebellion(applicationContext)
                    delay(5000)
                    continue
                }

                val hasAcc = isAccessibilityEnabled(applicationContext)
                val hasOverlay = android.provider.Settings.canDrawOverlays(applicationContext)
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val hasBattery = powerManager.isIgnoringBatteryOptimizations(packageName)
                val isSetupDone = LockManager.isSetupComplete(applicationContext)

                if (isSetupDone) {
                    val isCompromised = LockManager.isSystemCompromised(applicationContext)
                    val isLocked = LockManager.getPenaltyRemaining(applicationContext) > 0

                    if (isCompromised && !isLocked) {
                        LockManager.triggerPenalty(applicationContext)
                    }

                    val hasNotifs = androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()

                    // If we are in penalty, ENFORCE UI via Overlay (Respecting Grace Period)
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    
                    // IMPROVED EMERGENCY CHECK: Get the current top activity package if possible
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val topPkg = am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName ?: ""
                    val isEmergency = topPkg.contains("dialer") || topPkg.contains("telecom") || topPkg.contains("clock") || topPkg.contains("alarm")

                    if (LockManager.getPenaltyRemaining(applicationContext) > 0 && !LockManager.isBootGraceActive() && pm.isInteractive && !km.isKeyguardLocked && !isEmergency) {
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.putExtra("BLOCK_TYPE", "PENALTY")
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } 
                    else if (!hasNotifs && !LockManager.isBootGraceActive()) {
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(i) } catch (e: Exception) {}
                    }
                    else if (!hasAcc && !LockManager.isBootGraceActive()) {
                        val yankIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        yankIntent.data = Uri.parse("package:$packageName")
                        yankIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(yankIntent) } catch (e: Exception) {}
                    }
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