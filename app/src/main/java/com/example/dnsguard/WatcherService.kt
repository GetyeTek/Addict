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
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(99, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(99, createNotification())
        }
        
        scope.launch {
            while (isActive) {
                // 4th Suggestion: Self-Healing Cleanup
                LockManager.cleanupExpiredLocks(applicationContext)
                
                // 5th Suggestion: Dynamic Notification Update
                val statusLine = LockManager.getStatusLine(applicationContext)
                updateNotification(statusLine)

                // Respect Master Key / Nuke status
                if (NukeManager.isProtectionDisabled(applicationContext)) {
                    // KEEP ALIVE: Check for warnings and auto-lock expiry even if protections are down
                    NukeManager.checkAutoReEnable(applicationContext)
                    NukeManager.checkNotifications(applicationContext)

                    LockManager.clearRebellion(applicationContext)
                    delay(5000)
                    continue
                }

                val hasAcc = isAccessibilityEnabled(applicationContext)
                val hasOverlay = android.provider.Settings.canDrawOverlays(applicationContext)
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)
                val isSetupDone = LockManager.isSetupComplete(applicationContext)

                if (isSetupDone) {
                    val isCompromised = LockManager.isSystemCompromised(applicationContext)
                    val isLocked = LockManager.getPenaltyRemaining(applicationContext) > 0

                    if (isCompromised && !isLocked) {
                        LockManager.triggerPenalty(applicationContext)
                    }

                    val hasNotifs = androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
                    
                    // REFRESH: If notifs were blocked but now fixed, force the icon to show
                    if (hasNotifs) {
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            startForeground(99, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(99, createNotification())
                        }
                    }

                    // If we are in penalty, ENFORCE UI via Overlay (Respecting Grace Period)
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    
                    // CORRECTED: Use shared state from Accessibility Service via LockManager
                    val topPkg = LockManager.currentActivePackage
                    val isEmergency = topPkg.contains("dialer") || topPkg.contains("telecom") || topPkg.contains("clock") || topPkg.contains("alarm")

                    val blockType = LockManager.getActiveBlockType(applicationContext, topPkg)
                    if (blockType != null && !LockManager.isBootGraceActive() && pm.isInteractive && !km.isKeyguardLocked && !isEmergency) {
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.putExtra("BLOCK_TYPE", blockType)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } 
                    else if (!hasNotifs && !LockManager.isBootGraceActive() && !LockManager.isPermissionFixActive(applicationContext)) {
                        LockManager.startPermissionFixSession(applicationContext)
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(i) } catch (e: Exception) {}
                    }
                    else if (!hasOverlay && !LockManager.isBootGraceActive() && !LockManager.isPermissionFixActive(applicationContext)) {
                        LockManager.startPermissionFixSession(applicationContext)
                        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        i.data = Uri.parse("package:$packageName")
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(i) } catch (e: Exception) {}
                    }
                }
                
                // Harmonized Polling: 2s when active, 10s when sleeping
                if (pm.isInteractive) {
                    delay(2000)
                } else {
                    delay(10000)
                }
            }
        }
        return START_STICKY
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = "${ctx.packageName}/${GuardService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    // Cache to prevent useless updates
    private var lastNotifContent = ""

    private fun updateNotification(content: String) {
        // Optimization: Only notify if text changed (prevents flickering)
        if (content == lastNotifContent) return
        lastNotifContent = content
        
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(99, createNotification(content))
    }

    private fun createNotification(content: String = "Making sure you behave."): Notification {
        val channelId = "watcher_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Guardian Monitor", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(chan)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("GUARDIAN STATUS")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setGroup("guardian_service_group")
            .setSortKey("1")
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}