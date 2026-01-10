package com.example.dnsguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

class GuardService : AccessibilityService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)

    override fun onServiceConnected() {
        super.onServiceConnected()
        startMonitoring()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to read screen content, we just need the service to exist
        // so we can launch activities from background.
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