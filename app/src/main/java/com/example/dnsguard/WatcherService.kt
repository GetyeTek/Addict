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

    private val shakeThreshold = 30.0f // Requires ~3G of force
    private val shakeWindow = 1000L
    private var lastShakeTimestamp = 0L
    private val shakeTimestamps = java.util.LinkedList<Long>()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST)

        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)

        val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager?.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ctx = applicationContext

        // 0. MAGNETIC FLUX (Movement detection fallback)
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD && LockManager.isWhisperMode(ctx)) {
            val vals = event.values
            LockManager.lastMagVector?.let {
                val delta = Math.abs(vals[0]-it[0]) + Math.abs(vals[1]-it[1]) + Math.abs(vals[2]-it[2])
                LockManager.magneticFluxTotal += delta
            }
            LockManager.lastMagVector = vals.clone()
        }
        
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
                val steps = LockManager.getWhisperSteps(ctx)
                val dist = LockManager.currentDisplacement
                val flux = LockManager.magneticFluxTotal
                
                // LOGIC: End only if Steps met AND (Moved 20m OR high magnetic variance)
                val hasMovedEnough = dist >= 20f || flux > 150f
                
                if (steps >= 30 && hasMovedEnough) {
                    DebugLogger.log("EXORCIST", "Release Authorized. Steps: $steps, Dist: ${dist}m, Flux: $flux")
                    LockManager.stopWhisperMode(ctx)
                    stopLocationTracking()
                    stopPenaltyAudio()
                } else if (steps >= 30) {
                    DebugLogger.log("EXORCIST_STALL", "Steps done, but displacement failed. Dist: ${dist}m, Flux: $flux")
                }
            }
        }
    }

    override fun onLocationChanged(location: android.location.Location) {
        if (LockManager.isWhisperMode(applicationContext)) {
            // 1. ANTI-SPOOFING: Check if location is fake
            val isMock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                location.isMock
            } else {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }

            if (isMock) {
                DebugLogger.log("TAMPER_GPS", "Nice try. Mock location ignored.")
                return
            }

            val start = LockManager.startLocation
            if (start == null) {
                LockManager.startLocation = location
                DebugLogger.log("EXORCIST_GPS", "Anchor Locked (Acc: ${location.accuracy}m)")
            } else {
                val distance = start.distanceTo(location)
                LockManager.currentDisplacement = distance
                DebugLogger.log("EXORCIST_GPS", "Displacement: ${distance}m (Acc: ${location.accuracy}m)")
            }
        }
    }

    private fun startLocationTracking() {
        try {
            val provider = if (locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true) 
                android.location.LocationManager.GPS_PROVIDER else android.location.LocationManager.NETWORK_PROVIDER
            locationManager?.requestLocationUpdates(provider, 1000L, 1f, this)
            DebugLogger.log("EXORCIST_GPS", "Tracker started using $provider")
        } catch (e: SecurityException) {
            DebugLogger.log("EXORCIST_ERR", "GPS Permission Denied")
        }
    }

    private fun stopLocationTracking() {
        locationManager?.removeUpdates(this)
        LockManager.startLocation = null
        LockManager.currentDisplacement = 0f
        LockManager.magneticFluxTotal = 0f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPenaltyAudio() {
        if (mediaPlayer != null && mediaPlayer?.isPlaying == true) return
        
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Diagnostic State Check
        val dndState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) nm.currentInterruptionFilter else -1
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        DebugLogger.log("AUDIO_DIAG", "Attempting start. DND: $dndState, MaxVol: $maxVol")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                DebugLogger.log("AUDIO_DIAG", "DND Bypassed successfully.")
            } else {
                DebugLogger.log("AUDIO_WARN", "DND Access NOT granted. Audio might be suppressed.")
            }
        }

        am.setStreamVolume(AudioManager.STREAM_ALARM, maxVol, 0)

        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.bad_song)
            if (mediaPlayer == null) {
                DebugLogger.log("AUDIO_ERR", "MediaPlayer.create returned NULL. Is bad_song.mp3 in res/raw?")
                return
            }
            mediaPlayer?.setAudioStreamType(AudioManager.STREAM_ALARM)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
            DebugLogger.log("AUDIO_OK", "Playback started successfully.")
        } catch (e: Exception) { 
            DebugLogger.log("AUDIO_CRASH", "Engine Error: ${e.message}")
        }
    }

    private fun stopPenaltyAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
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

                // WHISPER PENALTY CHECK
                if (LockManager.isWhisperMode(applicationContext)) {
                    // 2. LOCATION-OFF ENFORCEMENT
                    val isGpsOn = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
                    val isNetOn = locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
                    
                    if (!isGpsOn && !isNetOn) {
                        DebugLogger.log("EXORCIST_EVASION", "Location turned off during protocol!")
                        val i = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        }
                        startActivity(i)
                    }

                    val elapsed = LockManager.getWhisperElapsed(applicationContext)
                    val steps = LockManager.getWhisperSteps(applicationContext)
                    
                    if (elapsed > 60000 && steps < 30) {
                        val isPlaying = mediaPlayer?.isPlaying == true
                        DebugLogger.log("EXORCIST_STATE", "Penalty Active. Time: ${elapsed/1000}s, Steps: $steps, Playing: $isPlaying")
                        startPenaltyAudio()
                    } else {
                        if (elapsed <= 60000) DebugLogger.log("EXORCIST_STATE", "Grace Period: ${60 - (elapsed/1000)}s left")
                        stopPenaltyAudio()
                    }
                } else {
                    if (mediaPlayer != null) stopPenaltyAudio()
                }

                // Respect Master Key / Nuke status
                if (NukeManager.isProtectionDisabled(applicationContext)) {
                    // KEEP ALIVE: Check for warnings and auto-lock expiry even if protections are down
                    NukeManager.checkAutoReEnable(applicationContext)
                    NukeManager.checkNotifications(applicationContext)

                    LockManager.clearRebellion(applicationContext)
                    delay(5000)
                    continue
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
                    val blockType = LockManager.getActiveBlockType(applicationContext, topPkg)
                    val isFixing = LockManager.isFixWindowActive(applicationContext)
                    
                    // DO NOT launch if we are in an emergency app OR if the package is empty
                    val isEmergency = LockManager.isEmergencyApp(topPkg)
                    
                    // DIAGNOSTIC LOG: Only log if we are about to block something that looks like an emergency app
                    if (blockType != null && (topPkg.contains("dialer") || topPkg.contains("clock") || topPkg.contains("telecom") || topPkg.contains("alarm"))) {
                        DebugLogger.log("YANK_CHECK", "Pkg: $topPkg | Block: $blockType | isEmergency: $isEmergency")
                    }

                    if (blockType != null && !isFixing && topPkg.isNotBlank() && !isEmergency && !LockManager.isBootGraceActive() && pm.isInteractive && !km.isKeyguardLocked) {
                        DebugLogger.log("YANK_ENFORCE", "Yanking back from $topPkg because of $blockType")
                        val i = Intent(applicationContext, LockdownActivity::class.java)
                        i.putExtra("BLOCK_TYPE", blockType)
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
        job.cancel()
    }
}