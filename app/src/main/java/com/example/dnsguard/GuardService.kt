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

    // SHIELD: Invisible Touch Blocker
    private var windowManager: android.view.WindowManager? = null
    private var shieldView: android.view.View? = null
    private var isShieldActive = false

    // TIMESTAMP: Tracks when you were last touching settings
    private var lastSettingsInteraction: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // INIT SHIELD
        windowManager = getSystemService(android.view.WindowManager::class.java)
        
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

        // 0. DIALOG TRAP (The Backup Plan)
        // If a system dialog pops up asking to "Stop" or "Deactivate", kill it.
        if (event.className?.toString()?.contains("Dialog") == true || 
            event.className?.toString()?.contains("AlertDialog") == true) {
            val source = event.source
            if (source != null) {
                // Check if this dialog is about US
                val dialogText = StringBuilder()
                recursiveScan(source, dialogText)
                val text = dialogText.toString()
                
                if (text.contains("DNS Guard", ignoreCase = true) && 
                   (text.contains("Stop", ignoreCase = true) || text.contains("Deactivate", ignoreCase = true))) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    // Also try to find the "Cancel" button and click it
                    val cancelNodes = source.findAccessibilityNodeInfosByText("Cancel")
                    cancelNodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }

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
        // 3. SETTINGS GUARD (Always Active - Locked OR Unlocked)
        // FIX: Broadened package check to include 'accessibility' (for Samsung/others) and 'settings'
        if (pkg.contains("settings") || pkg.contains("accessibility") || pkg.contains("packageinstaller")) {

            // OPTIMIZED SHIELDING: Zone-Based Activation
            // We only raise the pre-emptive shield if we are entering a "Danger Zone".
            // This prevents lag in safe menus like Wi-Fi or Display.
            
            val eventText = event.text.toString().lowercase()
            val isDangerZone = 
                pkg.contains("accessibility") || // Samsung/Pixel Accessibility App
                eventText.contains("accessibility") ||
                eventText.contains("admin") ||
                eventText.contains("app info") ||
                eventText.contains("dns guard") // Catches our own App Info page

            if (isDangerZone) {
                setShield(true)
            }
            
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
                // A. ACCESSIBILITY TRAP
                val trap1 = root.findAccessibilityNodeInfosByText("Monitors system settings")
                val trap2 = root.findAccessibilityNodeInfosByText("enforce Private DNS")
                
                if (trap1.isNotEmpty() || trap2.isNotEmpty()) {
                    // DANGER DETECTED: Keep Shield UP
                    
                    // AGGRESSIVE DEFENSE: 
                    // 1. Go Home (Harder to fight than Back)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                // B. ADMIN TRAP
                // B. SELF-DEFENSE (App Info & Storage Guard)
                // If he tries to open "DNS Guard" in Settings to Force Stop or Clear Data.
                // We look for our App Name appearing alongside "App info" or "Storage".
                val selfName = root.findAccessibilityNodeInfosByText("DNS Guard")
                if (selfName.isNotEmpty()) {
                    // If we see our name AND (App info OR Storage OR Force Stop)
                    if (root.findAccessibilityNodeInfosByText("App info").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByText("Storage").isNotEmpty() ||
                        root.findAccessibilityNodeInfosByText("Force stop").isNotEmpty()) {
                        
                        // DANGER DETECTED: Keep Shield UP
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        
                        // Punishment: Lock immediately if he tries to kill the guard
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        i.putExtra("BLOCK_TYPE", "SECURITY_TRIPWIRE")
                        startActivity(i)
                        break
                    }
                }

                // C. ADMIN TRAP
                // C. ADMIN TRAP
                // We search for 'DNS Guard Admin' AND 'Deactivate'/'Remove' in the same window
                val adminTitle = root.findAccessibilityNodeInfosByText("DNS Guard Admin")
                if (adminTitle.isNotEmpty()) {
                     if (root.findAccessibilityNodeInfosByText("Deactivate").isNotEmpty() ||
                         root.findAccessibilityNodeInfosByText("Remove").isNotEmpty() ||
                         root.findAccessibilityNodeInfosByText("Uninstall").isNotEmpty()) {
                             // DANGER DETECTED: Keep Shield UP
                             performGlobalAction(GLOBAL_ACTION_HOME)
                             break
                     }
                }
            }
            
            // VERDICT: SAFE
            // We scanned everything and found no traps.
            // It is now safe to lower the shield and let the user click.
            setShield(false)
    }

    // SHIELD LOGIC
    private fun setShield(active: Boolean) {
        if (active == isShieldActive) return
        
        try {
            if (active) {
                if (shieldView == null) {
                    shieldView = android.view.View(this).apply {
                        setBackgroundColor(0x00000000) // Transparent
                        isClickable = true
                        isFocusable = true
                    }
                }
                val params = android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // Priority Overlay
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                windowManager?.addView(shieldView, params)
                isShieldActive = true
            } else {
                if (shieldView != null) {
                    windowManager?.removeView(shieldView)
                    isShieldActive = false
                }
            }
        } catch (e: Exception) {
            // Use standard overlay if accessibility overlay fails
            try {
                 // Fallback logic if needed, but Accessibility Overlay usually works for Services
            } catch (e2: Exception) {}
        }
    }
    
    // HELPER: Recursive scan for the Dialog Trap
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
                if (!Settings.canDrawOverlays(applicationContext)) {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    i.data = Uri.parse("package:$packageName")
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                // 2. ENFORCE AUTO TIME (Required for Nuke Timer)
                else if ((Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME, 0) != 1 ||
                          Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) != 1)) {
                    val i = Intent(Settings.ACTION_DATE_SETTINGS)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                }
                // 3. ENFORCE ENGLISH LANGUAGE (Required for Text Scanners)
                else if (java.util.Locale.getDefault().language != "en") {
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