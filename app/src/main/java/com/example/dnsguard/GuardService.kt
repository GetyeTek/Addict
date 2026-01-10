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
        if (pkg == "com.android.settings") {
            val root = rootInActiveWindow ?: return
            val content = StringBuilder()
            recursiveScan(root, content)
            val screenText = content.toString()

            // A. Admin & Accessibility Trap
            if ((screenText.contains("DNS Guard Admin", ignoreCase = true) && screenText.contains("Deactivate", ignoreCase = true)) ||
                (screenText.contains("Monitors system settings to enforce Private DNS rules", ignoreCase = true) && screenText.contains("On", ignoreCase = true))) {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }

            // B. Language & Time Trap
            if (screenText.contains("Language", ignoreCase = true) ||
                screenText.contains("Input", ignoreCase = true) ||
                screenText.contains("Date", ignoreCase = true) ||
                screenText.contains("Time", ignoreCase = true) ||
                screenText.contains("Region", ignoreCase = true) ||
                screenText.contains("Locale", ignoreCase = true)) {
                
                // Safety: Ensure we aren't detecting our own app name in the list
                if (!screenText.contains("DNS Guard", ignoreCase = true)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
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

                // 1. SELF-HEALING: Check if Overlay Permission was revoked
                if (!Settings.canDrawOverlays(applicationContext)) {
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