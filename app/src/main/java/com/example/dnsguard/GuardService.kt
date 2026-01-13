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

    // POLLING & STRIKE SYSTEM
    private var pollingJob: Job? = null
    private var activePackage = ""
    private val browserStrikes = mutableListOf<Long>()
    private val telegramStrikes = mutableListOf<Long>()
    
    // COOLDOWN: Prevents 100 strikes in 1 second
    private var lastBrowserAction = 0L
    private var lastTelegramAction = 0L

    // SHIELD: Invisible Touch Blocker
    private var windowManager: android.view.WindowManager? = null
    private var shieldView: android.view.View? = null
    private var isShieldActive = false
    private var shieldJob: Job? = null

    // TIMESTAMP: Tracks when you were last touching settings
    private var lastSettingsInteraction: Long = 0L
    
    // SYNC: Tracks last time we pulled updates from Supabase
    private var lastCloudSync: Long = 0L
    // SESSION: Remembers if the current App Info page has been proven innocent
    private var verifiedSafeAppInfoSession = false

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
        
        // Update active package and manage heartbeat polling
        if (pkg != activePackage) {
            activePackage = pkg
            managePolling(pkg)
        }

        // 0. BROWSER BAN ENFORCEMENT
        // If penalty box is active, block access immediately.
        val isBrowserCheck = LockManager.isBlacklistedBrowser(applicationContext, pkg) || pkg == "com.android.chrome"
        if (isBrowserCheck && LockManager.isBrowserBanned(applicationContext)) {
             val i = Intent(applicationContext, LockdownActivity::class.java)
             i.putExtra("BLOCK_TYPE", "BROWSER_VIOLATION")
             i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
             startActivity(i)
             performGlobalAction(GLOBAL_ACTION_BACK)
             return
        }

        // REAL-TIME TRIGGER: Run check immediately on text/content changes
        // This acts as the "Keylogger" to catch typing instantly.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            // FIX: Explicitly include Chrome for monitoring, even if it is whitelisted in LockManager
            val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || pkg == "com.android.chrome"
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

            // --- STEP 1: PRE-EMPTIVE STRIKE ---
            // Block touches IMMEDIATELY. Guilty until proven innocent.
            setShield(true)

            // OPTIMIZED SHIELDING: Native Class Detection
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
                    val hasDnsGuard = root.findAccessibilityNodeInfosByText("DNS Guard")
                    
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
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            startTripwire()
                            break
                        }
                    }
                }
            }
            
            // VERDICT: BACK BUTTON MACHINE GUN
            if (confirmedDanger) {
                // CASE 1: THREAT CONFIRMED -> LOCKDOWN ACTIVITY
                shieldJob?.cancel()
                performGlobalAction(GLOBAL_ACTION_BACK)
                startTripwire()
            } else if (isAppInfoPage) {
                // CASE 2: APP INFO PAGE (Ambiguous)
                shieldJob?.cancel()
                
                // VERIFICATION: Look for "Notifications" anchor.
                // It is usually the first item below the header. If we see it, we are at the top.
                val hasAnchor = rootInActiveWindow?.findAccessibilityNodeInfosByText("Notifications")?.isNotEmpty() == true

                if (verifiedSafeAppInfoSession) {
                    // ALREADY VERIFIED: Allow scrolling.
                    setShield(false)
                } else if (hasAnchor) {
                    // PROVEN INNOCENT: We are at the top (Anchor visible) and didn't see "DNS Guard".
                    verifiedSafeAppInfoSession = true
                    setShield(false)
                } else {
                    // CASE 3: HIDDEN/SCROLLED AWAY -> KICK OUT
                    // If we don't see the anchor, we assume the user might have scrolled to hide the App Name.
                    // Guilty until proven innocent -> Eject.
                    scope.launch {
                        repeat(4) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                            delay(100)
                        }
                    }
                }
            } else {
                // CASE 4: GENERAL SETTINGS / MENU
                shieldJob?.cancel()
                
                // SMART RESET: If we see the main "Settings" header, we have left the App Info page.
                // We must reset the verification flag so the next App Info page is scanned fresh.
                val hasSettingsHeader = rootInActiveWindow?.findAccessibilityNodeInfosByText("Settings")?.isNotEmpty() == true
                if (hasSettingsHeader) {
                    verifiedSafeAppInfoSession = false
                }

                shieldJob = scope.launch {
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        if (isShieldActive) setShield(false)
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
                // RESTORED FLAG_NOT_FOCUSABLE to prevent Keyboard flickering in Search
                // The shield still blocks touches because it is fullscreen.
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
    
    private fun startTripwire() {
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

                // 5. CLOUD SYNC (Hourly)
                // Keeps the bad words database updated
                val now = System.currentTimeMillis()
                if (now - lastCloudSync > 60 * 60 * 1000) { // 1 Hour
                    lastCloudSync = now
                    scope.launch {
                        CloudLogger.syncToCloud(applicationContext)
                    }
                }

                // Check freq (Aggressive: 0.5s)
                delay(500)
            }
        }
    }

    private fun managePolling(pkg: String) {
        // Stop existing poll to avoid duplicates
        pollingJob?.cancel()

        // FIX: Explicitly include Chrome for monitoring
        val isBrowser = LockManager.isBlacklistedBrowser(applicationContext, pkg) || pkg == "com.android.chrome"
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
            val blacklist = listOf(
                // Social & Microblogging
                "bsky.app", "twitter.com", "x.com", "reddit.com", "tumblr.com", "threads.net", "plurk.com", "hive.social",
                // Fediverse
                "mastodon.social", "pawoo.net", "misskey.io", "pleroma.site", "lemmy.world", "truthsocial.com", "gab.com",
                // Community & Messaging
                "web.telegram.org", "t.me", "telegram.org", "discord.com", "kik.com", "snapchat.com", "slack.com",
                // Art & Creative
                "pixiv.net", "deviantart.com", "newgrounds.com", "artstation.com", "furaffinity.net", "hentai-foundry.com", "gelbooru.com", "danbooru.donmai.us",
                // Creator & Membership
                "onlyfans.com", "fansly.com", "patreon.com", "subscribestar.com", "fanbox.cc", "unifans.io", "buymeacoffee.com", "ko-fi.com",
                // Video & Streaming
                "kick.com", "bitchute.com", "rumble.com", "vimeo.com", "dailymotion.com", "dlive.tv", "picarto.tv",
                // Specialized & Existing
                "fetlife.com", "badoo.com", "tinder.com", "yubo.live", "instagram.com", "tiktok.com", "pornhub", "xnxx"
            )
            
            for (site in blacklist) {
                val candidates = root.findAccessibilityNodeInfosByText(site)
                for (node in candidates) {
                    // 1. CONTEXT CHECK: Is this the URL Bar?
                    // We check if the node is editable (typing) OR if its ID indicates it's an address bar.
                    val resId = node.viewIdResourceName?.lowercase() ?: ""
                    val isUrlBar = node.isEditable || resId.contains("url") || resId.contains("address") || resId.contains("omnibox") || resId.contains("search_box")
                    
                    // If it's just static text on a page (not the URL bar), ignore it.
                    if (!isUrlBar) continue 

                    // 2. TEXT MATCHING
                    val rawText = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
                    val lowerText = rawText.lowercase()
                    val index = lowerText.indexOf(site)

                    if (index != -1) {
                        // STRICT CHECK: char before must NOT be letter/digit
                        val charBefore = if (index > 0) lowerText[index - 1] else ' '
                        if (!charBefore.isLetterOrDigit()) {
                            handleBrowserStrike()
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

    private fun handleBrowserStrike() {
        val now = System.currentTimeMillis()
        if (now - lastBrowserAction < 1000) return // Debounce (1s cooldown)
        lastBrowserAction = now

        browserStrikes.add(now)
        browserStrikes.removeAll { it < now - 10000 }

        if (browserStrikes.size >= 4) {
             // TRIGGER BLOCK & BAN
             LockManager.banBrowser(applicationContext)
             val i = Intent(applicationContext, LockdownActivity::class.java)
             i.putExtra("BLOCK_TYPE", "BROWSER_VIOLATION")
             i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
             startActivity(i)
        } else {
             // WARNING STRIKE
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