package com.example.dnsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class GuardService : AccessibilityService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    // TIMESTAMP: Tracks when you were last touching settings
    private var lastSettingsInteraction: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // ELEVATE PRIORITY: Persistent Notification
        startForegroundService()
        startMonitoring()
    }

    private fun startForegroundService() {
        val channelId = "dns_guard_channel"
        val nm = getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = android.app.NotificationChannel(
                channelId, 
                "DNS Monitor", 
                android.app.NotificationManager.IMPORTANCE_MIN // Minimal intrusion
            )
            nm.createNotificationChannel(chan)
        }

        val notif = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Protection Active")
            .setContentText("DNS Guard is monitoring network security.")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        
        // 1337 is the notification ID
        startForeground(1337, notif)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""

        // 1. INTERACTION DETECTED: Pause the background loop if we are in Settings
        if (pkg == "com.android.settings" || pkg == "com.samsung.accessibility") {
            lastSettingsInteraction = System.currentTimeMillis()
        }

        // 1. BROWSER & VPN GUARD (Active ONLY when Unlocked)
        // When unlocked, we kill blacklisted browsers and check for VPNs.
        // We do NOT return here, because the Settings Guard must remain active.
        if (LockManager.isUnlocked(applicationContext)) {
            if (LockManager.isBlacklistedBrowser(pkg)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }

        // 2. NUKE CHECK: If Nuke Protocol is active, we STOP here.
        // This is the ONLY way to bypass the Settings Guard below.
        if (NukeManager.isProtectionDisabled(applicationContext)) {
            return
        }

        // 3. SETTINGS GUARD (Always Active - Locked OR Unlocked)
        // This prevents uninstalling or changing Language/Time unless Nuked.
        // BYPASS: If Uninstall Mode is ON (via UI Checkbox), skip this entire block.
        if ((pkg == "com.android.settings" || pkg.contains("packageinstaller")) && !LockManager.isUninstallMode(applicationContext)) {
            
            // MULTI-WINDOW DEFENSE: Iterate ALL visible windows
            val allWindows = this.windows
            if (allWindows.isEmpty()) return

            for (window in allWindows) {
                val root = window.root ?: continue
                val content = StringBuilder()
                recursiveScan(root, content)
                val screenText = content.toString()

                // A. ACCESSIBILITY TRAP (Fixed for Samsung)
                // If we see the unique description, we are on the control screen.
                if (screenText.contains("Monitors system settings to enforce Private DNS rules", ignoreCase = true)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    break
                }

                // B. ADMIN TRAP
                if (screenText.contains("DNS Guard Admin", ignoreCase = true) && 
                   (screenText.contains("Deactivate", ignoreCase = true) || 
                    screenText.contains("Remove", ignoreCase = true) ||
                    screenText.contains("Uninstall", ignoreCase = true))) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    break
                }

                // C. Language & Time Trap
                if (screenText.contains("Language", ignoreCase = true) ||
                    screenText.contains("Date", ignoreCase = true) ||
                    screenText.contains("Time", ignoreCase = true)) {
                    
                    if (!screenText.contains("DNS Guard", ignoreCase = true)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        break
                    }
                }
            }
        }
    }

    private fun recursiveScan(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        for (i in 0 until node.childCount) {
            recursiveScan(node.getChild(i), sb)
        }
    }

    override fun onInterrupt() {}

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                // SKIP CHECKS IF UNLOCKED
                if (LockManager.isUnlocked(applicationContext)) {
                    delay(2000)
                    continue
                }

                // FIX: GRACE PERIOD
                // If user touched Settings in the last 3 seconds, DO NOT interrupt them.
                if (System.currentTimeMillis() - lastSettingsInteraction < 3000) {
                    delay(1000)
                    continue
                }

                // 1. SELF-HEALING: Check if Overlay Permission was revoked
                // FIX: Do not force this loop if Uninstall Mode is active
                if (!Settings.canDrawOverlays(applicationContext) && !LockManager.isUninstallMode(applicationContext)) {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } 
                // 2. CORE LOGIC: Check DNS
                else if (!DnsManager.isSecure(applicationContext)) {
                    // UNSAFE: Launch Lockdown
                    try {
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                // Check freq
                delay(2000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}