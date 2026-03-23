package com.guardian.net

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import kotlinx.coroutines.*

class GuardService : AccessibilityService() {

    // OPTIMIZATION: Throttle content scanning to prevent UI Lag
    private var lastContentScanTime = 0L

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)



    // POLLING & STRIKE SYSTEM
    private var pollingJob: Job? = null
    private var activePackage = ""
    private val browserStrikes = mutableListOf<Long>()
    private val telegramStrikes = mutableListOf<Long>()
    
    // COOLDOWN: Prevents 100 strikes in 1 second
    private var lastBrowserAction = 0L
    private var lastTelegramAction = 0L

    // TIMESTAMP: Tracks when you were last touching settings
    private var lastSettingsInteraction: Long = 0L
    private var lastUsageTick: Long = System.currentTimeMillis()
    private var lastMinuteWarningShown = 0L

    private fun sendPenaltyWarning() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "security_penalties"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Security Penalties", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(chan)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("LAST WARNING, IDIOT")
            .setContentText("One minute until I brick your distractions.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
        nm.notify(999, builder.build())
    }

    private fun checkPreBreakWarnings(usage: Long) {
        val thresholds = listOf(LockManager.T1, LockManager.T2, LockManager.T3, LockManager.T4, LockManager.T_RESET)
        var warningActive = false
        
        for (t in thresholds) {
            val diff = t - usage
            
            // 1. THE 10-SECOND COUNTDOWN
            if (diff in 1..10000) {
                val secs = (diff / 1000) + 1
                BreakWarningManager.showWarning(this, "⚠️ LOCKDOWN IN ${secs}s", true)
                warningActive = true
                break
            } 
            
            // 2. THE 1-MINUTE WARNING
            if (diff in 55000..65000) {
                 if (lastMinuteWarningShown != t) {
                     DebugLogger.log("BREAK", "Threshold logic matched for 1-minute warning (Diff: $diff)")
                     BreakWarningManager.showWarning(this, "Take a break in 1 minute", false)
                     lastMinuteWarningShown = t
                 }
                 warningActive = true
                 break
            }
        }

        if (!warningActive) {
            BreakWarningManager.hide()
        }
    }
    
    // SYNC: Tracks last time we pulled updates from Supabase
    private var lastCloudSync: Long = 0L
    // SESSION: Remembers if the current App Info page has been proven innocent
    private var verifiedSafeAppInfoSession = false

    // DYNAMIC LEARNING: Remembers any app that has shown a WebView during this session
    private val dynamicBrowsers = mutableSetOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        StatsManager.init(this)
        
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
            .setContentTitle("SECURITY DICTATOR")
            .setContentText("ALWAYS WATCHING, ALWAYS JUDGING")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        // 1337 is the notification ID
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1337, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1337, notif)
        }
    }

     override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: ""

        // 1. EVENT-DRIVEN SECURITY (High Priority)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            verifiedSafeAppInfoSession = false
            val isGraceActive = LockManager.isBootGraceActive()

            if (!NukeManager.isProtectionDisabled(applicationContext) && !isGraceActive) {
                val blockType = LockManager.getActiveBlockType(applicationContext, pkg)
                if (blockType != null) {
                    showInstantOverlay(blockType)
                    return
                }
            }
        }
        // DebugLogger.log("Event", "Pkg: $pkg Type: ${event.eventType} Class: ${event.className}")

        // GLOBAL RESET: If we switch to a different app (ignore SystemUI overlays like volume/keyboard)
        if (!pkg.contains("settings") && !pkg.contains("packageinstaller") && 
            !pkg.contains("accessibility") && !pkg.contains("systemui")) {
            if (verifiedSafeAppInfoSession) {
                DebugLogger.log("Reset", "Left Settings (Pkg: $pkg). Session Invalidated.")
                verifiedSafeAppInfoSession = false
            }
        }
        
        // Update active package and manage heartbeat polling
        if (pkg != activePackage) {
            activePackage = pkg
            LockManager.updateActivePackage(pkg)
            managePolling(pkg)
        }

        // LEARN: Detect Web-capable views and initiate Quarantine
        val className = event.className?.toString() ?: ""
        if (className.contains("WebView", ignoreCase = true) || 
            className.contains("ChromeCustomTab", ignoreCase = true) ||
            className.contains("WebSettings", ignoreCase = true)) {
            
            val isKnownBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || 
                                 LockManager.STANDARD_BROWSERS.contains(pkg)

            if (!isKnownBrowser && pkg != packageName && !LockManager.isAppApproved(applicationContext, pkg)) {
                LockManager.registerLearnedApp(applicationContext, pkg)
                if (dynamicBrowsers.add(pkg)) {
                    managePolling(pkg)
                }
            }
        }

         // 0. PERMANENT BAN: The Dirty Dozen
 val nukeList = listOf(
 "twitter", "com.x.android", "torproject", "org.plus18", "stashx", 
 "adultfriendfinder", "ashleymadison", "com.grindr", "getpure", "reddit"
 )
 if (nukeList.any { pkg.contains(it) }) {
 performGlobalAction(GLOBAL_ACTION_HOME)
 return
 }

 // 0. ROGUE BAN ENFORCEMENT (30 Minutes)
 if (LockManager.isNonStandardAppBanned(applicationContext) && 
 LockManager.isNonStandardApp(applicationContext, pkg)) {
 if (!LockManager.isFixWindowActive(applicationContext)) {
 showInstantOverlay("ROGUE_VIOLATION")
 performGlobalAction(GLOBAL_ACTION_BACK)
 }
 return
 }

 // 0. BROWSER BAN ENFORCEMENT
 val isBrowserCheck = LockManager.isBlacklistedBrowser(applicationContext, pkg) || pkg == "com.android.chrome" || pkg == "com.google.android.googlequicksearchbox"
 if (isBrowserCheck && LockManager.isBrowserBanned(applicationContext)) {
 if (!LockManager.isFixWindowActive(applicationContext)) {
 showInstantOverlay("BROWSER_VIOLATION")
 performGlobalAction(GLOBAL_ACTION_BACK)
 }
 return
 }

        // REAL-TIME TRIGGER: Run check on text/content changes
        // OPTIMIZATION: Throttled to prevent CPU spikes/Jank
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val now = System.currentTimeMillis()
            if (now - lastContentScanTime < 400) return // Debounce
            lastContentScanTime = now
            
            // EXEMPTION: Only hardcoded system apps (ChatGPT) bypass the scanner
            if (LockManager.isHardcodedSafe(pkg)) return

            val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || dynamicBrowsers.contains(pkg)
            val isTelegram = pkg.contains("telegram") || pkg.contains("challegram")
            
            if (isBrowser || isTelegram) {
                scope.launch { scanForViolations(isBrowser, isTelegram) }
            }
        }

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
                
                // DIALOG TRAP: Matches "Stop Guardian?" or "Deactivate Guardian?"
                if (!LockManager.isExpEnabled(applicationContext, "no_perm_scan")) {
                    if (text.contains("Guardian", ignoreCase = true) && 
                       (text.contains("Stop", ignoreCase = true) || text.contains("Deactivate", ignoreCase = true))) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        val cancelNodes = source.findAccessibilityNodeInfosByText("Cancel")
                        cancelNodes.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
            }
        }

        // 1. INTERACTION DETECTED: Pause the background loop if we are in Settings
        if (pkg == "com.android.settings" || pkg == "com.samsung.accessibility") {
            lastSettingsInteraction = System.currentTimeMillis()
            
            // ANTI-DRIFT PROTOCOL: If we are in the middle of a fix session, don't let them wander
            if (LockManager.isSystemCompromised(applicationContext) && LockManager.isPermissionFixActive(applicationContext)) {
                val cls = event.className?.toString() ?: ""
                // If they back into the main Settings dashboard or Top-level categories
                if (cls.contains("Settings\$SettingsDashboardActivity") || 
                    cls.contains("Settings\$AccessibilitySettingsActivity") ||
                    cls.endsWith(".Settings")) {
                    DebugLogger.log("ANTI_DRIFT", "Drift detected to main Settings. Relaunching Dashboard.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    val i = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(i)
                }
            }
        }

        // 3. NUKE CHECK: If Nuke Protocol is active, we STOP here.
        if (NukeManager.isProtectionDisabled(applicationContext)) {
            return
        }

        // 4. TELEGRAM GUARD (Ban Enforcement Only)
        // We removed the instant-ban scanner from here. The Strike System in scanForViolations() handles the rest.
        if (pkg.contains("telegram") || pkg.contains("challegram")) {
            if (LockManager.isTelegramBanned(applicationContext)) {
                showInstantOverlay("TELEGRAM_SUSPENDED")
                // Block interaction but don't force Home, let the Overlay sit there.
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }

        // 5. OMNI-BROWSER GUARD: REMOVED
        // We now rely solely on scanForViolations() (Polling & TextChange)
        // because it implements the Strict Boundary Check to prevent false positives.

        // 6. SETTINGS GUARD (Always Active - Locked OR Unlocked)
        // FIX: Broadened package check to include 'accessibility' (for Samsung/others) and 'settings'
        if (pkg.contains("settings") || pkg.contains("accessibility") || pkg.contains("packageinstaller")) {
            // OPTIMIZED DETECTION: Native Class Detection
            val cls = event.className?.toString()?.lowercase() ?: ""
            
            // DETECT APP INFO: Multiple Triggers (Header, Class, or Bottom Buttons)
            val rootNode = rootInActiveWindow
            val headerText = rootNode?.findAccessibilityNodeInfosByText("App info")
            val hasAppInfoHeader = headerText != null && headerText.isNotEmpty()

            // FIX: If header is scrolled away, check for persistent bottom buttons.
            // "Uninstall" and "Force stop" are strong indicators of the App Info page.
            val hasUninstall = rootNode?.findAccessibilityNodeInfosByText("Uninstall")?.isNotEmpty() == true
            val hasForceStop = rootNode?.findAccessibilityNodeInfosByText("Force stop")?.isNotEmpty() == true

            val isAppInfoPage = cls.contains("installedappdetails") || 
                                cls.contains("appmanagement") || 
                                hasAppInfoHeader || 
                                (hasUninstall && hasForceStop)
            
            var confirmedDanger = false
            
            // MULTI-WINDOW DEFENSE: Content Scanning
            val allWindows = this.windows
            if (!allWindows.isEmpty()) {
                for (window in allWindows) {
                    val root = window.root ?: continue
                    
                    // GLOBAL SCAN: Locate identifying strings first
                    // We look for the App Name "Guardian" to detect our own App Info page
                    val hasDnsGuard = root.findAccessibilityNodeInfosByText("Guardian")
                    
                    // A. ACCESSIBILITY TRAP (Targeted)
                    if (!LockManager.isExpEnabled(applicationContext, "no_perm_scan")) {
                        val trap1 = root.findAccessibilityNodeInfosByText("I see everything")
                        val trap2 = root.findAccessibilityNodeInfosByText("Don't act stupid")
                        
                        if (trap1.isNotEmpty() || trap2.isNotEmpty()) {
                            confirmedDanger = true
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            startTripwire()
                            break
                        }
                    }

                    // B. DEVICE ADMIN TRAP (Precision Match)
                    if (!LockManager.isExpEnabled(applicationContext, "no_perm_scan")) {
                        val hasDeactivate = root.findAccessibilityNodeInfosByText("Deactivate")
                        val hasGuardianTitle = root.findAccessibilityNodeInfosByText("Guardian Admin")
                        
                        if (hasDeactivate.isNotEmpty() && (hasGuardianTitle.isNotEmpty() || hasDnsGuard.isNotEmpty())) {
                             confirmedDanger = true
                             performGlobalAction(GLOBAL_ACTION_BACK)
                             startTripwire()
                             break
                        }
                    }

                                // C. SELF-DEFENSE (App Info & Storage Guard)
             if (isAppInfoPage) {
 val nodePkg = root.packageName?.toString() ?: ""
 val isSettingsWindow = nodePkg.contains("settings") || nodePkg.contains("packageinstaller")

 // DEADLOCK FIX: If we are actively fixing permissions, allow access to App Info
 val isFixing = LockManager.isPermissionFixActive(applicationContext)

 if (isSettingsWindow && hasDnsGuard.isNotEmpty()) {
 // Toggle 3: App Info Freedom check
 if (!isFixing && !LockManager.isExpEnabled(applicationContext, "no_app_info")) confirmedDanger = true
 break
 }
 if (!LockManager.isSafeSession(applicationContext, "ANY") && !isFixing && !LockManager.isExpEnabled(applicationContext, "no_app_info")) {
 confirmedDanger = true 
 }
 }
 }
 }
 
            if (confirmedDanger) {
                DebugLogger.log("BLOCK", "Tamper Detected! Neutralizing Settings.")
                performGlobalAction(GLOBAL_ACTION_HOME)
                val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses("com.android.settings")
                am.killBackgroundProcesses("com.samsung.accessibility")
                am.killBackgroundProcesses("com.android.packageinstaller")
                startTripwire()
            } else if (isAppInfoPage && !LockManager.isExpEnabled(applicationContext, "no_app_info")) {
                val hasAnchor = rootInActiveWindow?.findAccessibilityNodeInfosByText("Notifications")?.isNotEmpty() == true
                if (!verifiedSafeAppInfoSession && !hasAnchor) {
                    DebugLogger.log("BLOCK", "KICK OUT! Anchor missing.")
                    scope.launch {
                        repeat(4) { performGlobalAction(GLOBAL_ACTION_BACK); delay(100) }
                    }
                } else if (hasAnchor) {
                    verifiedSafeAppInfoSession = true
                }
            } else {
                val root = rootInActiveWindow
                if (root?.findAccessibilityNodeInfosByText("Settings")?.isNotEmpty() == true) {
                    verifiedSafeAppInfoSession = false
                }
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val isDown = action == KeyEvent.ACTION_DOWN
            if (isDown != LockManager.isVolumeUpHeld) {
                DebugLogger.log("EXORCIST", "VolUp ${if(isDown) "HELD" else "RELEASED"}")
            }
            LockManager.isVolumeUpHeld = isDown
            return false
        }
        return super.onKeyEvent(event)
    }

    private fun startTripwire() {
        if (LockManager.isBootGraceActive()) return
        showInstantOverlay("SECURITY_TRIPWIRE")
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
                // 0. AUTO-LOCK WATCHDOG
                NukeManager.checkAutoReEnable(applicationContext)
                NukeManager.checkNotifications(applicationContext)

                // 1. PENALTY LOGIC
                // REDEMPTION: If permissions are fixed, cancel the penalty immediately
                if (LockManager.getPenaltyRemaining(applicationContext) > 0 && !LockManager.isSystemCompromised(applicationContext)) {
                    LockManager.clearPenalty(applicationContext)
                }

                val penaltyRemaining = LockManager.getPenaltyRemaining(applicationContext)
                
                if (penaltyRemaining > 0) {
                    if (penaltyRemaining in 58000..65000 && !LockManager.wasPenaltyWarned(applicationContext)) {
                        sendPenaltyWarning()
                        LockManager.setPenaltyWarned(applicationContext)
                    }

                    // EMERGENCY BYPASS (Loose Check)
                    val isEmergency = activePackage.contains("dialer") || 
                                      activePackage.contains("telecom") || 
                                      activePackage.contains("clock") || 
                                      activePackage.contains("alarm") ||
                                      activePackage.contains("incallui")

                    if (activePackage != packageName && !isEmergency) {
                        if (!LockManager.isBootGraceActive()) {
                            val i = Intent(applicationContext, LockdownActivity::class.java)
                            i.putExtra("BLOCK_TYPE", "PENALTY")
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(i)
                        }
                    }
                } else if (LockManager.isSetupComplete(applicationContext) && LockManager.isSystemCompromised(applicationContext)) {
                    LockManager.triggerPenalty(applicationContext)
                }



                // USAGE TRACKING TICK
                val nowTick = System.currentTimeMillis()
                val delta = nowTick - lastUsageTick
                lastUsageTick = nowTick
                
                val powerManager = getSystemService(android.os.PowerManager::class.java)
                val isInteractive = powerManager.isInteractive
                val prefs = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)

                if (isInteractive) {
                    // 1. Check for 15-minute screen-off reset
                    val offTime = prefs.getLong("last_screen_off_ts", 0L)
                    if (offTime > 0) {
                        if (nowTick - offTime > 15 * 60 * 1000L) {
                            LockManager.resetUsage(applicationContext)
                            DebugLogger.log("LADDER", "15m Inactivity detected. Cycle Reset.")
                        }
                        prefs.edit().remove("last_screen_off_ts").apply()
                    }

                    // 2. Accumulate usage (only if NOT in a Lockdown/Break screen)
                    if (activePackage != packageName) {
                        val usage = LockManager.getAccumulatedUsage(applicationContext)
                        checkPreBreakWarnings(usage)
                        LockManager.updateUsageAndCheckBreak(applicationContext, delta)
                    }
                } else {
                    // Screen is OFF: Record the timestamp if not already recording
                    if (prefs.getLong("last_screen_off_ts", 0L) == 0L) {
                        prefs.edit().putLong("last_screen_off_ts", nowTick).apply()
                    }
                }

                // Global skip removed: Maintenance mode is now targeted to DNS only

                // 3. NUKE CHECK: If Nuke Protocol is active, we STOP here.
                if (NukeManager.isProtectionDisabled(applicationContext)) {
                    delay(5000) // Lower frequency check while Nuked
                    continue
                }

                // FIX: GRACE PERIOD (2000ms flicker)
                if (System.currentTimeMillis() - lastSettingsInteraction < 2000) {
                    delay(500)
                    continue
                }

                // 1. SELF-HEALING: Check if Overlay Permission was revoked
                if (!Settings.canDrawOverlays(applicationContext)) {
                    // FIX: Android disables overlays on Admin screens to prevent Tapjacking.
                    // We must NOT interfere if the user is currently in Settings.
                    if (!activePackage.contains("settings") && !activePackage.contains("packageinstaller")) {
                        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        i.data = Uri.parse("package:$packageName")
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    }
                }

                // 4. CHECK USER LOCKOUT (Focus Mode)
                else if (LockManager.isUserLockedOut(applicationContext)) {
                    showInstantOverlay("USER_LOCKOUT")
                }
                // 5. ENFORCE SYSTEM TIME (No Maintenance Bypass)
                else if (Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME, 0) != 1 ||
                         Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME_ZONE, 0) != 1) {
                    val i = Intent(Settings.ACTION_DATE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(i)
                }
                // 6. ENFORCE LANGUAGE (No Maintenance Bypass)
                else if (java.util.Locale.getDefault().language != "en") {
                    val i = Intent(Settings.ACTION_LOCALE_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(i)
                }
                else if (LockManager.isNightLockActive(applicationContext)) {
                    val isClock = activePackage.contains("clock") || activePackage.contains("alarm")
                    val isOurApp = activePackage == packageName || activePackage.contains(packageName)
                    
                    if (!isOurApp && !isClock) {
                        showInstantOverlay("NIGHT_LOCK")
                    }
                }
                else if (LockManager.getBreakRemaining(applicationContext) > 0) {
                    showInstantOverlay("BREAK_TIME")
                }
                                // 5. CHECK DNS (Honors Maintenance Mode & Boot Grace)
                val isSettingsApp = activePackage.contains("settings") || activePackage.contains("accessibility")
                val isMaintenance = LockManager.isUnlocked(applicationContext)
                val isGraceActive = LockManager.isBootGraceActive()

                if (!DnsManager.isSecure(applicationContext) && !isSettingsApp && !isMaintenance && !isGraceActive) {
                    try {
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 6. CLOUD SYNC (Hourly)
                // Keeps the bad words database updated
                val now = System.currentTimeMillis()
                if (now - lastCloudSync > 60 * 60 * 1000) { // 1 Hour
                    lastCloudSync = now
                    scope.launch {
                        CloudLogger.syncToCloud(applicationContext)
                    }
                }

                // METRICS: Update Performance Stats
                StatsManager.update(applicationContext)
                


                // OPTIMIZATION: Smart Sleep to save battery
                if (powerManager.isInteractive) {
                    delay(2000)
                } else {
                    delay(10000)
                }
            }
        }
    }

    private fun managePolling(pkg: String) {
        pollingJob?.cancel()
        
        // EXEMPTION: Only hardcoded system apps (ChatGPT) bypass the polling scanner
        if (LockManager.isHardcodedSafe(pkg)) return

        val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || dynamicBrowsers.contains(pkg)
        val isTelegram = pkg.contains("telegram") || pkg.contains("challegram")

        if (isBrowser || isTelegram) {
            pollingJob = scope.launch {
                while (isActive) {
                    delay(1000) // Content scanning heartbeat
                    scanForViolations(isBrowser, isTelegram)
                }
            }
        }
    }

    private fun scanForViolations(isBrowser: Boolean, isTelegram: Boolean) {
        if (LockManager.isFixWindowActive(applicationContext)) return

        val isLearnedApp = LockManager.getLearnedApps(applicationContext).containsKey(activePackage)
        val surveillanceLog = if (isLearnedApp) StringBuilder() else null

        val windows = this.windows
        for (window in windows) {
            val root = window.root ?: continue
            
            if (isBrowser) {
                val blacklist = listOf(
                    "bsky.app", "twitter.com", "x.com", "reddit.com", "tumblr.com", "threads.net", "plurk.com", "hive.social",
                    "mastodon.social", "pawoo.net", "misskey.io", "pleroma.site", "lemmy.world", "truthsocial.com", "gab.com",
                    "web.telegram.org", "t.me", "telegram.org", "discord.com", "kik.com", "snapchat.com", "slack.com",
                    "pixiv.net", "deviantart.com", "newgrounds.com", "artstation.com", "furaffinity.net", "hentai-foundry.com", "gelbooru.com", "danbooru.donmai.us",
                    "onlyfans.com", "fansly.com", "patreon.com", "subscribestar.com", "fanbox.cc", "unifans.io", "buymeacoffee.com", "ko-fi.com",
                    "kick.com", "bitchute.com", "rumble.com", "vimeo.com", "dailymotion.com", "dlive.tv", "picarto.tv",
                    "fetlife.com", "badoo.com", "tinder.com", "yubo.live", "instagram.com", "tiktok.com", "pornhub", "xnxx"
                )
                
                for (site in blacklist) {
                    val candidates = root.findAccessibilityNodeInfosByText(site)
                    for (node in candidates) {
                        val resId = node.viewIdResourceName?.lowercase() ?: ""
                        val isUrlBar = node.isEditable || resId.contains("url") || resId.contains("address") || resId.contains("omnibox")
                        
                        // SCAN STRATEGY:
                        // If it's a standard browser, focus on the URL bar to prevent over-triggering.
                        // If it's a 'Learned' app (even if approved), scan EVERYTHING (WebViews/Content) 
                        // because they often hide URLs in non-standard nodes.
                        val isStandard = LockManager.STANDARD_BROWSERS.contains(activePackage)
                        if (isStandard && !isUrlBar) continue

                        val rawText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
                        val lowerText = rawText.lowercase()

                        // SURVEILLANCE: Log what we see in Learned Apps
                        if (isLearnedApp && rawText.isNotBlank()) {
                            surveillanceLog?.apply {
                                append("[")
                                append(rawText.trim())
                                append("] ")
                            }
                        }

                        val index = lowerText.indexOf(site)
                        if (index != -1) {
                            val charBefore = if (index > 0) lowerText[index - 1] else ' '
                            if (!charBefore.isLetterOrDigit()) {
                                if (isLearnedApp) {
                                    DebugLogger.log("SURVEILLANCE_MATCH", "Domain [$site] found in Learned App [$activePackage]")
                                }
                                
                                val targetPkg = root.packageName?.toString() ?: activePackage
                                if (LockManager.isNonStandardApp(applicationContext, targetPkg)) {
                                    LockManager.banNonStandardApp(applicationContext)
                                    showInstantOverlay("ROGUE_VIOLATION")
                                } else {
                                    handleBrowserStrike()
                                }
                                return
                            }
                        }
                    }
                }
            }
            
            // Finalize Surveillance Log for this cycle
            surveillanceLog?.let { log ->
                if (isLearnedApp && log.isNotEmpty()) {
                    val preview = log.toString().take(200)
                    DebugLogger.log("QUARANTINE_SIGHT", "Pkg: $activePackage | Content: $preview...")
                }
            }

            if (isTelegram) {
                val allTabs = setOf("chats", "channels", "apps", "posts", "media", "downloads", "links", "files", "music", "voice", "global search")
                val screenContent = mutableListOf<String>()
                fun extract(node: AccessibilityNodeInfo?) {
                    if (node == null) return
                    if (!node.text.isNullOrBlank()) screenContent.add(node.text.toString())
                    if (!node.contentDescription.isNullOrBlank()) screenContent.add(node.contentDescription.toString())
                    for (i in 0 until node.childCount) extract(node.getChild(i))
                }
                extract(root)

                var matchCount = 0
                val contentToCheck = StringBuilder()
                for (text in screenContent) {
                    val lower = text.trim().lowercase()
                    if (allTabs.contains(lower)) matchCount++ else contentToCheck.append(text).append(" ")
                }

                if (matchCount >= 3) {
                    val finalContent = contentToCheck.toString()
                    // EXACT MATCH used here to prevent false positives from normalization
                    val violations = WordBank.getViolations(applicationContext, finalContent, exact = true)
                    if (violations.isNotEmpty()) {
                        val reason = violations.joinToString(", ")
                        DebugLogger.log("TG_BLOCK", "REASON: [$reason] | TEXT: \"$finalContent\"")
                        scope.launch { CloudLogger.logViolation(applicationContext, finalContent) }
                        handleTelegramStrike()
                        return
                    }
                }
            }
        }
    }

    private fun logSystemState(reason: String) {
        val dns = DnsManager.isSecure(applicationContext)
        val acc = LockManager.isSystemCompromised(applicationContext)
        val night = LockManager.isNightLockActive(applicationContext)
        val overlay = android.provider.Settings.canDrawOverlays(applicationContext)
        DebugLogger.log("STATE_DUMP", "Reason: $reason | DNS: $dns | Compromised: $acc | Night: $night | Overlay: $overlay")
    }

     private fun showInstantOverlay(type: String) {
 val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
 val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
 
 // BLOCK: Do not show overlays if screen is off OR keyguard is active
 if (!pm.isInteractive || km.isKeyguardLocked) return

 // 0.5 CONTENT FIX WINDOW (60s Grace) - Absolute Suppression
 if (LockManager.isFixWindowActive(applicationContext)) return

 // Get prioritized type, falling back to the requested type if manager is neutral
 val prioritizedType = LockManager.getActiveBlockType(applicationContext, activePackage) ?: type

 // STRICT EMERGENCY BYPASS
 if (LockManager.isEmergencyApp(activePackage) || activePackage == packageName) return

 if (!android.provider.Settings.canDrawOverlays(this)) return

 val i = Intent(this, LockdownActivity::class.java).apply {
 putExtra("BLOCK_TYPE", prioritizedType)
 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
 }
 try { startActivity(i) } catch (e: Exception) { }
 }
    private fun handleBrowserStrike() {
        val now = System.currentTimeMillis()
        if (now - lastBrowserAction < 1000) return 
        lastBrowserAction = now

        browserStrikes.add(now)
        // FIX: Increased memory window to 60 seconds (was 10s)
        browserStrikes.removeAll { it < now - 60000 }

        // FIX: Reduced threshold to 3 strikes (was 4)
        if (browserStrikes.size >= 3) {
             LockManager.banBrowser(applicationContext)
             showInstantOverlay("BROWSER_VIOLATION")
             browserStrikes.clear() // Reset counter after penalty
        } else {
             performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun handleTelegramStrike() {
        val now = System.currentTimeMillis()
        if (now - lastTelegramAction < 1000) return // Debounce (1s cooldown)
        lastTelegramAction = now

        telegramStrikes.add(now)
        // FIX: Increased memory window to 60 seconds
        telegramStrikes.removeAll { it < now - 60000 }

        if (telegramStrikes.size >= 3) {
             // 3rd Strike: Activate Ban & Overlay
             LockManager.banTelegram(applicationContext)
             showInstantOverlay("TELEGRAM_SUSPENDED")
        } else {
             // 1st & 2nd Strike: Double Back Tap (Clear Search -> Close Keyboard)
             scope.launch {
                 performGlobalAction(GLOBAL_ACTION_BACK)
                 delay(250) // Short delay to allow UI to react
                 performGlobalAction(GLOBAL_ACTION_BACK)
             }
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        pollingJob?.cancel()
    }
}