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

        // 2. BROWSER & VPN GUARD (Active ONLY when Unlocked)
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

        // 3. NUKE CHECK: If Nuke Protocol is active, we STOP here.
        if (NukeManager.isProtectionDisabled(applicationContext)) {
            return
        }

        // 4. SETTINGS GUARD (Always Active - Locked OR Unlocked)
        // FIX: Broadened package check to include 'accessibility' (for Samsung/others) and 'settings'
        if (pkg.contains("settings") || pkg.contains("accessibility") || pkg.contains("packageinstaller")) {

            // --- STEP 1: PRE-EMPTIVE STRIKE ---
            // Block touches IMMEDIATELY. Guilty until proven innocent.
            setShield(true)

            // OPTIMIZED SHIELDING: Native Class Detection
            val cls = event.className?.toString()?.lowercase() ?: ""
            val txt = event.text.toString().lowercase()

            val isDangerZone = 
                pkg.contains("accessibility") ||
                cls.contains("accessibility") ||
                cls.contains("deviceadmin") ||
                cls.contains("installedappdetails") ||
                cls.contains("appmanagement") ||
                txt.contains("dns guard") || 
                txt.contains("admin")

            var confirmedDanger = false
            if (isDangerZone) confirmedDanger = true
            
            // MULTI-WINDOW DEFENSE
            val allWindows = this.windows
            if (!allWindows.isEmpty()) {
                for (window in allWindows) {
                    val root = window.root ?: continue
                    
                    // A. ACCESSIBILITY TRAP
                    val trap1 = root.findAccessibilityNodeInfosByText("Monitors system settings")
                    val trap2 = root.findAccessibilityNodeInfosByText("enforce Private DNS")
                    
                    if (trap1.isNotEmpty() || trap2.isNotEmpty()) {
                        confirmedDanger = true
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }

                    // B. SELF-DEFENSE (App Info & Storage Guard)
                    val isAppInfoPage = cls.contains("installedappdetails") || 
                                        cls.contains("appmanagement") ||
                                        root.findAccessibilityNodeInfosByText("App info").isNotEmpty()

                    if (isAppInfoPage) {
                        val selfName = root.findAccessibilityNodeInfosByText("DNS Guard")
                        if (selfName.isNotEmpty()) {
                            confirmedDanger = true
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            
                            val i = Intent(applicationContext, LockdownActivity::class.java)
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            i.putExtra("BLOCK_TYPE", "SECURITY_TRIPWIRE")
                            startActivity(i)
                            break
                        }
                    }

                    // C. ADMIN TRAP
                    val adminTitle = root.findAccessibilityNodeInfosByText("DNS Guard Admin")
                    if (adminTitle.isNotEmpty()) {
                         if (root.findAccessibilityNodeInfosByText("Deactivate").isNotEmpty() ||
                             root.findAccessibilityNodeInfosByText("Remove").isNotEmpty() ||
                             root.findAccessibilityNodeInfosByText("Uninstall").isNotEmpty()) {
                                 confirmedDanger = true
                                 performGlobalAction(GLOBAL_ACTION_HOME)
                                 break
                         }
                    }
                }
            }
            
            // VERDICT: SPONGE DELAY
            if (confirmedDanger) {
                // Keep Shield UP
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                // Even if safe, we HOLD the shield for 1 second.
                // This neutralizes "Speed Tapping" by forcing the user to wait.
                // If they interact blindly, the shield eats the tap.
                scope.launch {
                    withContext(Dispatchers.Main) {
                        // Wait for UI to settle
                        delay(1000)
                        // If we haven't detected danger in the meantime, lower it.
                        if (isShieldActive) {
                             setShield(false)
                        }
                    }
                }
            }
        } else {
            // Not in settings? Shield down.
            setShield(false)
        }
    }

    // SHIELD LOGIC (High Performance Mode)
    private fun setShield(active: Boolean) {
        if (active == isShieldActive) return
        
        try {
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
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // REMOVED FLAG_NOT_FOCUSABLE to consume all input events aggressively if needed
                // Added WATCH_OUTSIDE_TOUCH to catch edge cases
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                android.graphics.PixelFormat.TRANSLUCENT
            )

            if (active) {
                if (shieldView?.parent == null) {
                    windowManager?.addView(shieldView, params)
                } else {
                    shieldView?.visibility = android.view.View.VISIBLE
                    windowManager?.updateViewLayout(shieldView, params)
                }
                isShieldActive = true
            } else {
                // Optimization: Don't remove view, just HIDE it. 
                // This eliminates the 'addView' lag for the next trigger.
                if (shieldView?.parent != null) {
                    shieldView?.visibility = android.view.View.GONE
                }
                isShieldActive = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
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