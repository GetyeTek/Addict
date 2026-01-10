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
        startMonitoring()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName != "com.android.settings") return

        val root = rootInActiveWindow ?: return
        val content = StringBuilder()
        recursiveScan(root, content)
        val screenText = content.toString()

        // 1. DEVICE ADMIN: Detects "DNS Guard Admin" header + "Deactivate" button
        // The presence of "Deactivate" confirms it is currently granted/active.
        if (screenText.contains("DNS Guard Admin", ignoreCase = true) && 
            screenText.contains("Deactivate", ignoreCase = true)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // 2. ACCESSIBILITY: Detects Unique Description + "On" status
        // Uses the unique string from strings.xml to avoid false positives on other apps.
        if (screenText.contains("Monitors system settings to enforce Private DNS rules", ignoreCase = true) && 
            screenText.contains("On", ignoreCase = true)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
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
                    // Being an AccessibilityService allows starting activities from background
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