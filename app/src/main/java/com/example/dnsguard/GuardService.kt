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
            .setContentTitle("YO, CUT IT OUT")
            .setContentText("Fix it in 1 min or get bricked.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
        nm.notify(999, builder.build())
    }

    private fun checkPreBreakWarnings(usage: Long) {
        val thresholds = listOf(LockManager.T1, LockManager.T2, LockManager.T3, LockManager.T4)
        
        for (t in thresholds) {
            val diff = t - usage
            
            // 1. THE 10-SECOND COUNTDOWN (All breaks)
            if (diff in 1..10000) {
                val secs = (diff / 1000) + 1
                BreakWarningManager.showWarning(this, "Sit down in $secs...", true)
                return
            } 
            
            // 2. THE 1-MINUTE WARNING (Only T3 and T4)
            if ((t == LockManager.T3 || t == LockManager.T4) && diff in 59000..61000) {
                 if (lastMinuteWarningShown != t) {
                     BreakWarningManager.showWarning(this, "You're done in 1 minute", false)
                     lastMinuteWarningShown = t
                 }
                 return
            }
        }
        BreakWarningManager.hide()
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
            .setContentTitle("I'm Watching You")
            .setContentText("Don't try anything stupid.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
        
        // 1337 is the notification ID
        startForeground(1337, notif)
    }

     override fun onAccessibilityEvent(event: AccessibilityEvent?) {
 if (event == null) return
 val pkg = event.packageName?.toString() ?: ""

 // EVENT-DRIVEN SECURITY
 if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
 // Fix: Reset session verification
 verifiedSafeAppInfoSession = false

 val isMaintenance = LockManager.isUnlocked(applicationContext)
 if (!NukeManager.isProtectionDisabled(applicationContext) && !isMaintenance) {
 val isSettingsApp = pkg.contains("settings") || pkg.contains("accessibility")
 if (!DnsManager.isSecure(applicationContext) && !isSettingsApp) {
 val i = Intent(applicationContext, LockdownActivity::class.java)
 i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
 startActivity(i)
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

        // LEARN: If this app displays a WebView, mark it as a browser for the session
        if (event.className == "android.webkit.WebView") {
            if (dynamicBrowsers.add(pkg)) {
                DebugLogger.log("LEARN", "Detected hidden WebView in $pkg. Marked as browser.")
                // Restart polling immediately for this new threat
                managePolling(pkg)
            }
        }

         // 0. PERMANENT BAN: The Dirty Dozen
 val nukeList = listOf(
 "twitter", "com.x.android", "torproject", "org.plus18", "stashx", 
 "adultfriendfinder", "ashleymadison", "com.grindr", "getpure"
 )
 if (nukeList.any { pkg.contains(it) }) {
 performGlobalAction(GLOBAL_ACTION_HOME)
 return
 }

 // 0. ROGUE BAN ENFORCEMENT (30 Minutes)
 if (LockManager.isNonStandardAppBanned(applicationContext) && 
 LockManager.isNonStandardApp(applicationContext, pkg)) {
 showInstantOverlay("ROGUE_VIOLATION")
 performGlobalAction(GLOBAL_ACTION_BACK)
 return
 }

 // 0. BROWSER BAN ENFORCEMENT
 val isBrowserCheck = LockManager.isBlacklistedBrowser(applicationContext, pkg) || pkg == "com.android.chrome" || pkg == "com.google.android.googlequicksearchbox"
 if (isBrowserCheck && LockManager.isBrowserBanned(applicationContext)) {
 showInstantOverlay("BROWSER_VIOLATION")
 performGlobalAction(GLOBAL_ACTION_BACK)
 return
 }

        // REAL-TIME TRIGGER: Run check on text/content changes
        // OPTIMIZATION: Throttled to prevent CPU spikes/Jank
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val now = System.currentTimeMillis()
            if (now - lastContentScanTime < 400) return // Debounce: Max 2.5 scans per second
            lastContentScanTime = now
            
        // FIX: Check Whitelist, Blacklist, AND Dynamic List
        val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || 
                       pkg == "com.android.chrome" || 
                       pkg == "com.google.android.googlequicksearchbox" ||
                       dynamicBrowsers.contains(pkg)

        val isTelegram = pkg.contains("telegram") || pkg.contains("challegram")
            
            if (isBrowser || isTelegram) {
                // Launch immediate check (Bypassing the 1.5s Polling delay)
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
                
                if (text.contains("Guardian", ignoreCase = true) && 
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
            if (LockManager.isBlacklistedBrowser(applicationContext, pkg)) {
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

        // 4. TELEGRAM GUARD (Ban Enforcement Only)
        // We removed the instant-ban scanner from here. The Strike System in scanForViolations() handles the rest.
        if (pkg.contains("telegram") || pkg.contains("challegram")) {
            if (LockManager.isTelegramBanned(applicationContext)) {
                val i = Intent(applicationContext, LockdownActivity::class.java)
                i.putExtra("BLOCK_TYPE", "TELEGRAM_SUSPENDED")
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(i)
                
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
                    val trap1 = root.findAccessibilityNodeInfosByText("Monitors system settings")
                    val trap2 = root.findAccessibilityNodeInfosByText("enforce Private DNS")
                    
                    if (trap1.isNotEmpty() || trap2.isNotEmpty()) {
                        confirmedDanger = true
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        startTripwire()
                        break
                    }

                    // B. DEVICE ADMIN TRAP
                    val hasDeactivate = root.findAccessibilityNodeInfosByText("Deactivate")
                    if (hasDeactivate.isNotEmpty() && hasDnsGuard.isNotEmpty()) {
                         confirmedDanger = true
                         performGlobalAction(GLOBAL_ACTION_BACK)
                         startTripwire()
                         break
                    }

                                // C. SELF-DEFENSE (App Info & Storage Guard)
             if (isAppInfoPage) {
 val nodePkg = root.packageName?.toString() ?: ""
 val isSettingsWindow = nodePkg.contains("settings") || nodePkg.contains("packageinstaller")

 if (isSettingsWindow && hasDnsGuard.isNotEmpty()) {
 confirmedDanger = true
 break
 }
 if (!LockManager.isSafeSession(applicationContext, "ANY")) {
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
 } else if (isAppInfoPage) {
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


    
    private fun startTripwire() {
        if (LockManager.isBootGraceActive()) return

        val i = Intent(applicationContext, LockdownActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        i.putExtra("BLOCK_TYPE", "SECURITY_TRIPWIRE")
        startActivity(i)
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

                    val dialerIntent = Intent(Intent.ACTION_DIAL)
                    val resolveInfo = packageManager.resolveActivity(dialerIntent, 0)
                    val dialerPkg = resolveInfo?.activityInfo?.packageName ?: "com.android.dialer"
                    val clockPkg = "com.sec.android.app.clockpackage"

                    if (activePackage != packageName && activePackage != dialerPkg && activePackage != clockPkg) {
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
                if (powerManager.isInteractive) {
                    val usage = LockManager.getAccumulatedUsage(applicationContext)
                    checkPreBreakWarnings(usage)
                    LockManager.updateUsageAndCheckBreak(applicationContext, delta)
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
                // 4. CHECK USER LOCKOUT (Focus Mode)
                else if (LockManager.isUserLockedOut(applicationContext)) {
                    showInstantOverlay("USER_LOCKOUT")
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
                                // 5. CHECK DNS (Honors Maintenance Mode)
                val isSettingsApp = activePackage.contains("settings") || activePackage.contains("accessibility")
                val isMaintenance = LockManager.isUnlocked(applicationContext)
                if (!DnsManager.isSecure(applicationContext) && !isSettingsApp && !isMaintenance) {
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
        // Stop existing poll to avoid duplicates
        pollingJob?.cancel()

        // FIX: Explicitly include Chrome & Google App for monitoring
        val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || 
                       pkg == "com.android.chrome" || 
                       pkg == "com.google.android.googlequicksearchbox" ||
                       dynamicBrowsers.contains(pkg)

        val isTelegram = pkg.contains("telegram") || pkg.contains("challegram")

        if (isBrowser || isTelegram) {
            pollingJob = scope.launch {
                while (isActive) {
                    delay(1500) // 1.5 Second Heartbeat
                    scanForViolations(isBrowser, isTelegram)
                }
            }
        }
    }

    private fun scanForViolations(isBrowser: Boolean, isTelegram: Boolean) {
        val root = rootInActiveWindow ?: return

        // 1. BROWSER LOGIC (Strict Domain Matching)
        if (isBrowser) {
            val spyKeywords = listOf("google", "http")
            for (key in spyKeywords) {
                val spies = root.findAccessibilityNodeInfosByText(key)
                for (node in spies) {
                    val resId = node.viewIdResourceName?.lowercase() ?: "null"
                    DebugLogger.log("SPY", "Pkg: $activePackage | ID: $resId | Text: ${node.text}")
                }
            }

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
                    val isUrlBar = node.isEditable || resId.contains("url") || resId.contains("address") || resId.contains("omnibox") || 
                                   resId.contains("search_box") || resId.contains("location") || resId.contains("toolbar") || 
                                   resId.contains("title") || resId.contains("input") || resId.contains("bar") ||
                                   resId.contains("mozac") || resId.contains("search_text") || resId.contains("edit_text") || resId.contains("query")
                    
                    val isWebContent = resId.contains("content") || node.className == "android.webkit.WebView"
                    if (!isUrlBar && isWebContent) continue

                    val rawText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
                    val lowerText = rawText.lowercase()
                    val index = lowerText.indexOf(site)

                    if (index != -1) {
                        val charBefore = if (index > 0) lowerText[index - 1] else ' '
                        if (!charBefore.isLetterOrDigit()) {
                            if (LockManager.isNonStandardApp(applicationContext, activePackage)) {
                                LockManager.banNonStandardApp(applicationContext)
                                val i = Intent(applicationContext, LockdownActivity::class.java)
                                i.putExtra("BLOCK_TYPE", "ROGUE_VIOLATION")
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(i)
                            } else {
                                handleBrowserStrike()
                            }
                            return
                        }
                    }
                }
            }
        }

        // 2. TELEGRAM LOGIC (Fingerprint Scan)
        if (isTelegram) {
            // FINGERPRINT: The specific UI structure of the Search Screen
            val visibleTabs = setOf("chats", "channels", "apps", "posts")
            val hiddenTabs = setOf("media", "downloads", "links", "files", "music", "voice")
            val allTabs = visibleTabs + hiddenTabs + "global search"

            // A. EXTRACT ALL TEXT
            val screenContent = mutableListOf<String>()
            fun extract(node: AccessibilityNodeInfo?) {
                if (node == null) return
                if (!node.text.isNullOrBlank()) screenContent.add(node.text.toString())
                if (!node.contentDescription.isNullOrBlank()) screenContent.add(node.contentDescription.toString())
                for (i in 0 until node.childCount) extract(node.getChild(i))
            }
            extract(root)

            // B. CALCULATE CONFIDENCE & FILTER
            var matchCount = 0
            val contentToCheck = StringBuilder()

            for (text in screenContent) {
                val lower = text.trim().lowercase()
                if (allTabs.contains(lower)) {
                    // It matches our fingerprint -> Increase Confidence
                    matchCount++
                } else {
                    // It is NOT a UI tab -> This is content we must check (e.g. what you typed)
                    contentToCheck.append(text).append(" ")
                }
            }

            // C. MATHEMATICAL PROOF (Threshold)
            // We require at least 3 UI elements to match before we assume this is the Search Screen.
            // This prevents false positives (e.g., chatting about "Channels" in a group).
            if (matchCount >= 3) {
                val finalContent = contentToCheck.toString()

                if (!WordBank.isSafe(applicationContext, finalContent)) {
                   scope.launch { CloudLogger.logViolation(applicationContext, finalContent) }
                   handleTelegramStrike()
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
 
 // Bypass if screen is off or locked
 if (!pm.isInteractive || km.isKeyguardLocked) return

 // Get prioritized type
 val prioritizedType = LockManager.getActiveBlockType(applicationContext) ?: return

 // STRICT EMERGENCY BYPASS
 val isEmergencyApp = activePackage.contains("dialer") || 
                          activePackage.contains("telecom") || 
                          activePackage.contains("clock") || 
                          activePackage.contains("alarm") ||
                          activePackage.contains("incallui")
 
 if (isEmergencyApp) return

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
        browserStrikes.removeAll { it < now - 10000 }

        if (browserStrikes.size >= 4) {
             LockManager.banBrowser(applicationContext)
             showInstantOverlay("BROWSER_VIOLATION")
        } else {
             performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun handleTelegramStrike() {
        val now = System.currentTimeMillis()
        if (now - lastTelegramAction < 1000) return // Debounce (1s cooldown)
        lastTelegramAction = now

        telegramStrikes.add(now)
        telegramStrikes.removeAll { it < now - 10000 }

        if (telegramStrikes.size >= 3) {
             // 3rd Strike: Activate Ban & Overlay
             LockManager.banTelegram(applicationContext)
             val i = Intent(applicationContext, LockdownActivity::class.java)
             i.putExtra("BLOCK_TYPE", "TELEGRAM_SUSPENDED")
             i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
             startActivity(i)
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