package com.example.dnsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        if (LockManager.isUnlocked(applicationContext)) {
            if (LockManager.isBlacklistedBrowser(pkg)) {
                // Launch Lockdown UI with Browser Block message
                val i = Intent(applicationContext, LockdownActivity::class.java)
                i.putExtra("BLOCK_TYPE", "BROWSER")
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(i)
            }
        }

        // 2. NUKE CHECK: If Nuke Protocol is active, we STOP here.
        if (NukeManager.isProtectionDisabled(applicationContext)) {
            return
        }

        // 3. SETTINGS GUARD (Always Active - Locked OR Unlocked)
        // FIX: Broadened package check to include 'accessibility' (for Samsung/others) and 'settings'
        if ((pkg.contains("settings") || pkg.contains("accessibility") || pkg.contains("packageinstaller")) && !LockManager.isUninstallMode(applicationContext)) {
            
            // MULTI-WINDOW DEFENSE: Iterate ALL visible windows
            val allWindows = this.windows
            if (allWindows.isEmpty()) return

            for (window in allWindows) {
                val root = window.root ?: continue
                val content = StringBuilder()
                recursiveScan(root, content)
                val screenText = content.toString()

                // DEBUG: DUMP TO FILE
                logToFile(screenText)

                // A. ACCESSIBILITY TRAP (Broadened)
                // We check for EITHER part of the unique description string.
                // This handles cases where UI nodes split the sentence or valid layouts differ.
                if (screenText.contains("Monitors system settings", ignoreCase = true) || 
                    screenText.contains("enforce Private DNS", ignoreCase = true)) {
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

    private fun logToFile(text: String) {
        try {
            val logFile = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "dns_guard_log.txt"
            )
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logLine = "$timestamp --- $text\n\n"

            logFile.appendText(logLine)
        } catch (e: Exception) {
            // Failed to write, do nothing to avoid crashing the service
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

                // FIX: GRACE PERIOD (2000ms flicker)
                if (System.currentTimeMillis() - lastSettingsInteraction < 2000) {
                    delay(500)
                    continue
                }

                // 1. SELF-HEALING: Check if Overlay Permission was revoked
                if (!Settings.canDrawOverlays(applicationContext) && !LockManager.isUninstallMode(applicationContext)) {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                // 2. ENFORCE AUTO TIME (Required for Nuke Timer)
                else if ((Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME, 0) != 1 ||
                          Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) != 1) &&
                          !LockManager.isUninstallMode(applicationContext)) {
                    val i = Intent(Settings.ACTION_DATE_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                // 3. ENFORCE ENGLISH LANGUAGE (Required for Text Scanners)
                else if (java.util.Locale.getDefault().language != "en" && !LockManager.isUninstallMode(applicationContext)) {
                    val i = Intent(Settings.ACTION_LOCALE_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                // 4. CHECK DNS
                else if (!DnsManager.isSecure(applicationContext)) {
                    try {
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                // Check freq (Aggressive: 0.5s)
                delay(500)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}