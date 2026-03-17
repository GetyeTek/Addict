package com.guardian.net

import android.app.*
import android.content.*
import android.net.Uri
import android.provider.Settings
import android.os.IBinder
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.media.AudioManager
import kotlinx.coroutines.*

class WatcherService : Service(), SensorEventListener, android.location.LocationListener {
    private val job = SupervisorJob()
    private var mediaPlayer: MediaPlayer? = null
    private var sensorManager: SensorManager? = null
    private var locationManager: android.location.LocationManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var locationSettlementCount = 0

    private val shakeThreshold = 30.0f // Requires ~3G of force
    private val shakeWindow = 1000L
    private var lastShakeTimestamp = 0L
    private val shakeTimestamps = java.util.LinkedList<Long>()
    
    // SENSOR INTERLOCK STATE
    private var lastLocation: android.location.Location? = null

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                enforceMaxVolume()
            }
        }
    }

    private fun enforceMaxVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (LockManager.isWhisperMode(applicationContext)) {
            val elapsed = LockManager.getWhisperElapsed(applicationContext)
            val steps = LockManager.getWhisperSteps(applicationContext)
            val dist = LockManager.currentDisplacement
            val hasMovedEnough = dist >= 20f

            if (elapsed > 60000 && (steps < 30 || !hasMovedEnough)) {
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val currentVol = am.getStreamVolume(AudioManager.STREAM_ALARM)

                // 1. Force DND Off
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (nm.isNotificationPolicyAccessGranted) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    }
                }
                
                // 2. Force Volume Max with Logic Logging
                if (currentVol < maxVol) {
                    DebugLogger.log("AUDIO_ENFORCE", "RESTRICTED: $currentVol/$maxVol. Snapping to MAX.")
                    am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)
                } else {
                    // This log proves the recursion guard is working
                    DebugLogger.log("AUDIO_GUARD", "PASSED: $currentVol/$maxVol. No change needed.")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)

        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ctx = applicationContext
        
        // 1. SHAKE DETECTION (Requires holding Volume Up)
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            if (!LockManager.isVolumeUpHeld) {
                shakeTimestamps.clear()
                return
            }

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            if (magnitude > shakeThreshold) {
                val now = System.currentTimeMillis()
                
                // Debounce: Only count one shake every 150ms to ensure directional change
                if (now - lastShakeTimestamp > 150) {
                    shakeTimestamps.addLast(now)
                    lastShakeTimestamp = now
                    
                    while (shakeTimestamps.isNotEmpty() && now - shakeTimestamps.first > shakeWindow) {
                        shakeTimestamps.removeFirst()
                    }

                    DebugLogger.log("EXORCIST", "Distinct Shake Detected (${shakeTimestamps.size}/3)")
                    if (shakeTimestamps.size >= 3) {
                        shakeTimestamps.clear()
                        if (!LockManager.isWhisperMode(ctx)) {
                            DebugLogger.log("EXORCIST", "TRIGGER: Conditions met. Launching Protocol.")
                            LockManager.startWhisperMode(ctx, isReflex = true)
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            vibrator.vibrate(longArrayOf(0, 500, 200, 500), -1)
                            
                                                    val intent = Intent(ctx, LockdownActivity::class.java).apply {
                            putExtra("BLOCK_TYPE", "WHISPER_PROTOCOL")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startLocationTracking()
                        startActivity(intent)
                        }
                    }
                }
            }
        }

        // 2. STEP DETECTION (ENFORCEMENT)
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            if (LockManager.isWhisperMode(ctx)) {
                LockManager.addWhisperStep(ctx)
                // Add 2.5 meters of 'Movement Budget' per step detected
                LockManager.whisperDistanceBudget += 2.5f
                
                val steps = LockManager.getWhisperSteps(ctx)
                val currentDist = LockManager.currentDisplacement
                if (steps >= 30 && currentDist >= 20f) {
                    DebugLogger.log("EXORCIST", "Release Authorized. Steps: $steps, Dist: ${currentDist}m")
                    LockManager.stopWhisperMode(ctx)
                    stopLocationTracking()
                }
            }
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        if (LockManager.isWhisperMode(applicationContext)) {
            // 1. ANTI-SPOOFING
            val isMock = if (android.os.Build.VERSION.SDK_INT >= 31) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
            if (isMock) return

            // 2. ACCURACY FILTER: Ignore garbage data (Drift)
            // Anything worse than 25m is usually a bounce off a wall.
            if (location.accuracy > 25f) {
                DebugLogger.log("GPS_DRIFT", "Ignored low accuracy: ${location.accuracy}m")
                return
            }

            val start = LockManager.startLocation
            
            // 3. ANCHOR STABILIZATION
            if (start == null) {
                // Loosened threshold: 25m accuracy required for anchor
                if (location.accuracy <= 25f) {
                    LockManager.startLocation = location
                    LockManager.currentDisplacement = 0f
                    LockManager.whisperDistanceBudget = 0f
                    DebugLogger.log("EXORCIST_GPS", "Stable Anchor Set (Acc: ${location.accuracy}m)")
                } else {
                    DebugLogger.log("EXORCIST_GPS", "Wait for accuracy... (Current: ${location.accuracy}m)")
                }
            } else {
                val prev = lastLocation ?: start
                val deltaDist = prev.distanceTo(location)
                
                // 1. Accuracy Scaling: Proportional filter to ignore drift
                val minMovementRequired = location.accuracy * 0.7f
                
                if (deltaDist > minMovementRequired && deltaDist < 15f) {
                    // 2. Budget Consumption: Only count GPS move if we have 'Step Credit'
                    val allowedMove = Math.min(deltaDist, LockManager.whisperDistanceBudget)
                    if (allowedMove > 0.1f) {
                        LockManager.currentDisplacement += allowedMove
                        LockManager.whisperDistanceBudget -= allowedMove
                        DebugLogger.log("EXORCIST_GPS", "Valid Move: ${allowedMove.toInt()}m. Total: ${LockManager.currentDisplacement.toInt()}m")
                    }
                }
                lastLocation = location
            }
        }
    }

    private fun startLocationTracking() {
        try {
            // REQUEST FROM BOTH: GPS and Network for max responsiveness
            locationManager?.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 0L, 0f, this)
            locationManager?.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 0L, 0f, this)
            DebugLogger.log("EXORCIST_GPS", "Dual-Tracker Engaged (0ms/0m)")
        } catch (e: Exception) {
            DebugLogger.log("EXORCIST_ERR", "GPS Failed: ${e.message}")
        }
    }

    private fun stopLocationTracking() {
        locationManager?.removeUpdates(this)
        LockManager.startLocation = null
        lastLocation = null
        LockManager.currentDisplacement = 0f
        LockManager.whisperDistanceBudget = 0f
        locationSettlementCount = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPenaltyAudio() {
        // 1. Check if already playing robustly
        try {
            if (mediaPlayer != null && mediaPlayer?.isPlaying == true) return
        } catch (e: Exception) {
            stopPenaltyAudio()
        }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Diagnostic State Check
        val ringerMode = am.ringerMode
        val dndState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) nm.currentInterruptionFilter else -1
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        
        DebugLogger.log("AUDIO_SYS", "Ringer: $ringerMode, DND: $dndState, MaxVol: $maxVol")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } else {
                DebugLogger.log("AUDIO_WARN", "DND access missing - audio may be muted by system")
            }
        }

        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

        try {
            mediaPlayer = MediaPlayer().apply {
                // Must set attributes BEFORE prepare()
                val attr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attr)

                val afd = resources.openRawResourceFd(R.raw.bad_song)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                setOnErrorListener { _, what, extra ->
                    val err = when(what) {
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "UNKNOWN"
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "SERVER_DIED"
                        else -> "WHAT_$what"
                    }
                    val det = when(extra) {
                        -1004 -> "IO_ERROR"
                        -1007 -> "MALFORMED"
                        -1010 -> "UNSUPPORTED"
                        -110 -> "TIMEOUT"
                        else -> "EXTRA_$extra"
                    }
                    DebugLogger.log("MEDIA_CRASH", "Type: $err, Detail: $det")
                    stopPenaltyAudio()
                    true
                }

                setOnInfoListener { _, what, extra ->
                    DebugLogger.log("MEDIA_INFO", "Code: $what, Extra: $extra")
                    false
                }

                isLooping = true
                prepare()
                start()
            }
            DebugLogger.log("AUDIO_OK", "Engine started successfully")
        } catch (e: Exception) {
            DebugLogger.log("AUDIO_INIT_FAIL", "${e.message}")
            stopPenaltyAudio()
        }
    }

    private fun stopPenaltyAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // Already inactive
        } finally {
            mediaPlayer = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(99, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(99, createNotification())
        }
        
        scope.launch {
            while (isActive) {
                // 4th Suggestion: Self-Healing Cleanup
                LockManager.cleanupExpiredLocks(applicationContext)
                
                // 5th Suggestion: Dynamic Notification Update
                val statusLine = LockManager.getStatusLine(applicationContext)
                updateNotification(statusLine)

                // DAILY LIMIT & WARNING CHECK
                val dailyUsage = LockManager.getDailyUsage(applicationContext)
                val prefs = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR).toString()
                val lastWarnDay = prefs.getString("last_daily_warn_day", "")

                if (dailyUsage >= LockManager.DAILY_WARN_MS && dailyUsage < LockManager.DAILY_LIMIT_MS) {
                    if (lastWarnDay != today) {
                        sendDailyWarningNotification()
                        prefs.edit().putString("last_daily_warn_day", today).apply()
                    }
                }

                // WHISPER PENALTY CHECK
                if (LockManager.isWhisperMode(applicationContext)) {
                    val isGpsOn = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
                    val isNetOn = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                    
                    if (!isGpsOn && !isNetOn) {
                        val i = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(i)
                    }

                    val elapsed = LockManager.getWhisperElapsed(applicationContext)
                    val steps = LockManager.getWhisperSteps(applicationContext)
                    val dist = LockManager.currentDisplacement
                    val hasMovedEnough = dist >= 20f
                    
                    if (elapsed > 60000 && (steps < 30 || !hasMovedEnough)) {
                        // RESURRECTION LOGIC: 
                        // If it should be playing but isn't (crashed/killed), start it again.
                        if (mediaPlayer == null || !mediaPlayer!!.isPlaying) {
                             DebugLogger.log("EXORCIST_RECOVER", "Penalty audio died. Restarting...")
                             startPenaltyAudio()
                        }
                        enforceMaxVolume()
                    } else {
                        // User finished the task or is still in grace period
                        if (steps >= 30 && hasMovedEnough) stopPenaltyAudio()
                    }
                } else {
                    // Not in whisper mode at all
                    stopPenaltyAudio()
                }

                // Respect Master Key / Nuke status
                if (NukeManager.isProtectionDisabled(applicationContext)) {
                    // TOGGLE 1: RESURRECTION
                    if (LockManager.isExpEnabled(applicationContext, "resurrect")) {
                        DebugLogger.log("RESURRECTION", "Kill Signal Detected. Force-Restarting Engine.")
                        LockManager.setSetupComplete(applicationContext)
                        NukeManager.setProtectionDisabled(applicationContext, false)
                    } else {
                        // Standard behavior: Respect the Nuke
                        NukeManager.checkAutoReEnable(applicationContext)
                        NukeManager.checkNotifications(applicationContext)
                        LockManager.clearRebellion(applicationContext)
                        delay(5000)
                        continue
                    }
                }

                val hasAcc = isAccessibilityEnabled(applicationContext)
                val hasOverlay = android.provider.Settings.canDrawOverlays(applicationContext)
                val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val hasBattery = pm.isIgnoringBatteryOptimizations(packageName)
                val isSetupDone = LockManager.isSetupComplete(applicationContext)

                if (isSetupDone) {
                    val isCompromised = LockManager.isSystemCompromised(applicationContext)
                    val isLocked = LockManager.getPenaltyRemaining(applicationContext) > 0

                    if (isCompromised && !isLocked) {
                        LockManager.triggerPenalty(applicationContext)
                    }

                    val hasNotifs = androidx.core.app.NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
                    
                    // REFRESH: If notifs were blocked but now fixed, force the icon to show
                    if (hasNotifs) {
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            startForeground(99, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(99, createNotification())
                        }
                    }

                    // If we are in penalty, ENFORCE UI via Overlay (Respecting Grace Period)
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    
                    // CORRECTED: Use shared state from Accessibility Service via LockManager
                    val topPkg = LockManager.currentActivePackage
                    val penaltyActive = LockManager.getPenaltyRemaining(applicationContext) > 0
                    
                    // DETERMINISTIC BLOCK: If the system is compromised or in penalty, we ignore the package check.
                    val effectiveBlock = if (isCompromised || penaltyActive) "PENALTY" 
                                        else LockManager.getActiveBlockType(applicationContext, topPkg)
                    
                    val isFixing = LockManager.isFixWindowActive(applicationContext)
                    val isEmergency = LockManager.isEmergencyApp(topPkg) || topPkg.contains("systemui")

                    // NO NEGOTIATION: If it's a PENALTY/Compromised state, we fire regardless of topPkg/Emergency status.
                    // Otherwise, we use surgical blocking.
                    val shouldFire = effectiveBlock != null && !isFixing && !LockManager.isBootGraceActive() && pm.isInteractive && !km.isKeyguardLocked &&
                                     (effectiveBlock == "PENALTY" || (topPkg.isNotBlank() && !isEmergency))

                    if (shouldFire) {
                        DebugLogger.log("YANK_ENFORCE", "Hard Lock Triggered. Reason: $effectiveBlock | Pkg: $topPkg")
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.putExtra("BLOCK_TYPE", effectiveBlock)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        startActivity(i)
                    } 
                    else if (!hasNotifs && !LockManager.isBootGraceActive() && !LockManager.isPermissionFixActive(applicationContext) && !km.isKeyguardLocked) {
                        LockManager.startPermissionFixSession(applicationContext)
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(i) } catch (e: Exception) {}
                    }
                    else if (!hasOverlay && !LockManager.isBootGraceActive() && !LockManager.isPermissionFixActive(applicationContext) && !km.isKeyguardLocked) {
                        LockManager.startPermissionFixSession(applicationContext)
                        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        i.data = Uri.parse("package:$packageName")
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        try { startActivity(i) } catch (e: Exception) {}
                    }
                }
                
                // Harmonized Polling: 2s when active, 10s when sleeping
                if (pm.isInteractive) {
                    delay(2000)
                } else {
                    delay(10000)
                }
            }
        }
        return START_STICKY
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = "${ctx.packageName}/${GuardService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    // Cache to prevent useless updates
    private var lastNotifContent = ""

    private fun updateNotification(content: String) {
        // Optimization: Only notify if text changed (prevents flickering)
        if (content == lastNotifContent) return
        lastNotifContent = content
        
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(99, createNotification(content))
    }

    private fun sendDailyWarningNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_limit_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Usage Warnings", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(chan)
        }
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FINAL HOUR")
            .setContentText("8 HOURS GONE. You have 60 minutes of life left before I brick this slab.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setAutoCancel(true)
        nm.notify(888, builder.build())
    }

    private fun createNotification(content: String = "Watching you fail."): Notification {
        val channelId = "watcher_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Guardian Monitor", NotificationManager.IMPORTANCE_MIN)
            nm.createNotificationChannel(chan)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("YOUR OVERSEER")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(volumeReceiver) } catch (e: Exception) {}
        job.cancel()
    }
}