package com.guardian.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockdownActivity : ComponentActivity() {
    private var blockTypeState = mutableStateOf("SYSTEM")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"

        // PREVENT SPLIT SCREEN: Force full screen priority
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val type = blockTypeState.value
                val uiConfig = getUiConfig(type)
                var showPassDialog by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
                        colors = listOf(uiConfig.color.copy(alpha = 0.4f), Color.Black),
                        radius = 2500f
                    )))
                    
                    if (showPassDialog) {
                        MaintenanceDialog(onDismiss = { showPassDialog = false }) {
                            LockManager.unlock(applicationContext)
                            showPassDialog = false
                            finishAffinity()
                        }
                    }

                    LockdownContent(uiConfig, type, onUnlockClick = { showPassDialog = true })
                }
            }
        }
    }

    @Composable
    fun LockdownContent(config: UiConfig, type: String, onUnlockClick: () -> Unit) {
        val context = LocalContext.current
        val elapsed = LockManager.getWhisperElapsed(context)
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = ""
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = config.color.copy(alpha = alpha)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                config.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                type.replace("_", " "),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                config.description,
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (type == "EXORCISM_COUNTDOWN") {
                val remaining = ((60000 - elapsed) / 1000).coerceAtLeast(0)
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GRACE PERIOD", color = config.color, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("$remaining", color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Black)
                    Text("SECONDS UNTIL NOISE", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Change your environment NOW.\nWalk 20 meters and take 30 steps.", 
                        color = Color.White, textAlign = TextAlign.Center, lineHeight = 20.sp)
                }

                val isReflex = LockManager.isWhisperReflex(context)
                if (isReflex && elapsed < 10000) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { LockManager.stopWhisperMode(context); finishAffinity() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("MISTAKE / CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Aborting in ${10 - (elapsed / 1000)}s...", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            if (type == "WHISPER_PROTOCOL") {
                val steps = LockManager.getWhisperSteps(context)
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PENALTY ACTIVE", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Step Progress
                    LinearProgressIndicator(
                        progress = (steps / 30f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF331111), RoundedCornerShape(12.dp)),
                        color = Color.Red
                    )
                    Text("STEPS: $steps / 30", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("WALK TO SILENCE THE VOICES", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // SOCIAL FAIL-SAFE: 10s Cancel Button (Reflex Only)
                val isReflex = LockManager.isWhisperReflex(context)
                if (isReflex && elapsed < 10000) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { LockManager.stopWhisperMode(context); finishAffinity() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("MISTAKE / CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("Aborting in ${10 - (elapsed / 1000)}s...", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
            
            if (type == "YT_WAITING") {
                val prefs = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
                val reqTs = prefs.getLong("yt_request_ts", 0L)
                val remaining = ((reqTs + 30 * 60 * 1000L) - System.currentTimeMillis()).coerceAtLeast(0)
                val mins = (remaining / 60000)
                val secs = (remaining % 60000) / 1000
                Text(String.format("%02d:%02d", mins, secs), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (type == "QUARANTINE") {
                 val remaining = LockManager.getQuarantineRemaining(context, blockTypeState.value) // Note: This uses the pkg if we passed it, but better logic below
                 // We need the active package name. For now, GuardService passes the TYPE, but LockdownActivity can query LockManager.currentActivePackage
                 val pkg = LockManager.currentActivePackage
                 val rem = LockManager.getQuarantineRemaining(context, pkg)
                 val mins = (rem / 60000)
                 val secs = (rem % 60000) / 1000
                 
                 Text(String.format("%02d:%02d REMAINING", mins, secs), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                 Spacer(modifier = Modifier.height(24.dp))
            }

            if (type == "SYSTEM") {
                Text("PICK ONE, GENIUS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                DnsManager.ALLOWED_HOSTNAMES.forEach { host ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("DNS", host))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp), color = Color(0xFF1A1A1A), border = BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Text(host, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            val hasEmergencyBypass = listOf("NIGHT_LOCK", "BREAK_TIME", "PENALTY", "USER_LOCKOUT", "DAILY_LIMIT_EXCEEDED").contains(type)
            val showFixButton = LockManager.shouldShowFixButton(context, type)

            if (showFixButton) {
                Button(
                    onClick = {
                        LockManager.startFixWindow(context)
                        finishAffinity()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Build, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("FIX CONTENT (60s)", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (type == "SYSTEM") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onUnlockClick,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("LET ME OUT") }
                    
                    Button(
                        onClick = {
                            LockManager.startPermissionFixSession(context)
                            
                            // Smart Redirect: Check Accessibility first
                            val expected = "${context.packageName}/${GuardService::class.java.canonicalName}"
                            val enabledServices = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                            val hasAccessibility = enabledServices.contains(expected)

                            if (!hasAccessibility) {
                                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                })
                            } else {
                                val dnsIntent = Intent("android.settings.PVT_DNS_SETTINGS").apply { 
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                }
                                try {
                                    context.startActivity(dnsIntent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply { 
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP 
                                    })
                                }
                            }
                            finishAffinity()
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = config.color),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("FIX IT", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            } else             if (hasEmergencyBypass) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }) },
                        modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))) {
                        Icon(Icons.Filled.Phone, null, tint = config.color)
                    }
                    Button(onClick = { 
                        val i = packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage") 
                            ?: packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                        i?.let { it.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(it) }
                    }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A))) {
                        Icon(Icons.Filled.Alarm, null, tint = config.color)
                    }
                }
            } else {
                Button(onClick = { 
                    val home = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    startActivity(home) 
                }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = config.color)) {
                    Text("FINE, WHATEVER", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        LaunchedEffect(type) {
            while(true) {
                val ctx = applicationContext
                // NEW: If the active package is an emergency app, close overlay instantly
                val currentApp = LockManager.currentActivePackage
                val isEmergency = LockManager.isEmergencyApp(currentApp)
                
                val shouldClose = isEmergency || when (type) {
                    "SYSTEM" -> DnsManager.isSecure(ctx) || LockManager.isUnlocked(ctx)
                    "NIGHT_LOCK" -> !LockManager.isNightLockActive(ctx)
                    "BREAK_TIME" -> LockManager.getBreakRemaining(ctx) <= 0
                    "USER_LOCKOUT" -> !LockManager.isUserLockedOut(ctx)
                    "EXORCISM_COUNTDOWN" -> !LockManager.isWhisperMode(ctx) || LockManager.getWhisperElapsed(ctx) > 60000
                    "WHISPER_PROTOCOL" -> !LockManager.isWhisperMode(ctx)
                    "BOOT_SETTLING" -> !LockManager.isSettlingActive() && !(LockManager.isStrictGraceActive() && LockManager.currentActivePackage.contains("settings"))
                    "PENALTY" -> LockManager.getPenaltyRemaining(ctx) <= 0 && !LockManager.isSystemCompromised(ctx)
                    "BROWSER_VIOLATION" -> !LockManager.isBrowserBanned(ctx)
                    "TELEGRAM_SUSPENDED" -> !LockManager.isTelegramBanned(ctx)
                    "DEEP_FOCUS" -> LockManager.getDeepFocusRemaining(ctx) <= 0
                    else -> false
                }
                if (shouldClose) { finishAffinity(); break }
                delay(500) // Faster check to prevent flickering back to overlay
            }
        }
    }

    @Composable
    private fun MaintenanceDialog(onDismiss: () -> Unit, onCorrect: () -> Unit) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("DNS Repair Access") },
            text = { 
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("Admin Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            },
            confirmButton = { Button(onClick = { if (text == "1234") onCorrect() }) { Text("UNLOCK DNS") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
        )
    }

    private fun getUiConfig(type: String): UiConfig {
        return when (type) {
            "BROWSER_VIOLATION" -> UiConfig(Icons.Filled.Block, Color(0xFFFF3B30), "GET ABSOLUTELY REKT", "Your browser is a weapon of self-destruction. Denied.")
            "TELEGRAM_SUSPENDED" -> UiConfig(Icons.Filled.Lock, Color(0xFFFF9500), "TOUCH GRASS, LOSER", "Social media rot is over. Go talk to a real human.")
            "NIGHT_LOCK" -> UiConfig(Icons.Filled.NightsStay, Color(0xFF5856D6), "BEDTIME, WEAKLING", "Sleep is mandatory for your tiny brain. Eyes closed.")
            "BREAK_TIME" -> UiConfig(Icons.Filled.Timer, Color(0xFF34C759), "SHUT IT DOWN", "Sit down and breathe. You're too high-strung.")
            "USER_LOCKOUT" -> UiConfig(Icons.Filled.Timer, Color(0xFF007AFF), "NO ESCAPE", "You asked for this. Now do your actual work.")
            "PENALTY" -> UiConfig(Icons.Filled.Warning, Color(0xFFFF2D55), "THE DUNGEON", "Stop touching things you don't understand.")
            "ROGUE_VIOLATION" -> UiConfig(Icons.Filled.Block, Color(0xFF9333EA), "PATHETIC ATTEMPT", "You thought you could sneak that app past me? Adorable.")
            "SECURITY_TRIPWIRE" -> UiConfig(Icons.Filled.Shield, Color(0xFFDC2626), "STOP RIGHT THERE", "Try to tamper again and see what happens.")
            "MANUAL_LOCK" -> UiConfig(Icons.Filled.Timer, Color(0xFF818CF8), "YOU DID THIS", "You locked it. Now finish it. No excuses.")
            "PERMANENT_BAN" -> UiConfig(Icons.Filled.Dangerous, Color(0xFF000000), "EXECUTED", "This app is garbage. I've deleted its purpose from your life.")
            "DEEP_FOCUS" -> UiConfig(Icons.Filled.CenterFocusStrong, Color(0xFFFACC15), "TUNNEL VISION", "If it's not on the list, it's irrelevant. Focus.")
            "MAINTENANCE_BROWSER_ILLEGAL" -> UiConfig(Icons.Filled.Dangerous, Color(0xFFFB923C), "STICK TO THE PLAN", "You're here to fix the DNS, not browse with this garbage. Use Chrome or stay locked out.")
            "EXORCISM_COUNTDOWN" -> UiConfig(Icons.Filled.Warning, Color(0xFFFBBF24), "THE DEVIL IS WHISPERING", "You have 60 seconds to move before the penalty music begins.")
            "WHISPER_PROTOCOL" -> UiConfig(Icons.Filled.DirectionsRun, Color(0xFFEF4444), "MOVE OR SUFFER", "The noise will not stop until you finish the task.")
            "QUARANTINE" -> UiConfig(Icons.Filled.HourglassEmpty, Color(0xFFF87171), "MANDATORY QUARANTINE", "I've detected a web-viewer in this app. It is locked for 1 hour while I prepare surveillance.")
            "PENDING_APPROVAL" -> UiConfig(Icons.Filled.FactCheck, Color(0xFFFBBF24), "PENDING APPROVAL", "The quarantine has ended. You must manually approve this app in the Guardian Dashboard to use it.")
            "DAILY_LIMIT_EXCEEDED" -> UiConfig(Icons.Default.Bedtime, Color(0xFF4B5563), "DAWN OF THE DEAD", "9 HOURS. You've spent more time with this screen than your own thoughts. Go to sleep before you forget how to blink.")
            "YT_REQUEST_REQUIRED" -> UiConfig(Icons.Default.VideoSettings, Color(0xFFEF4444), "PERMISSION DENIED", "YouTube is off-limits. Go to Experimental UI to request a viewing session.")
            "YT_WAITING" -> UiConfig(Icons.Default.HourglassTop, Color(0xFFFBBF24), "PATIENCE, GRASSHOPPER", "The 30-minute delay is active. Go contemplate your existence or read a book.")
            "BOOT_SETTLING" -> UiConfig(Icons.Default.Sync, Color(0xFF60A5FA), "SYSTEM OPTIMIZING", "The device is settling after boot. Guardian is calibrating security protocols. Please wait.")
            else -> UiConfig(Icons.Filled.Shield, Color(0xFFEF4565), "FIX IT OR ROT", "Your DNS is compromised. Obey the rules or stare at this wall.")
        }
    }

    data class UiConfig(val icon: ImageVector, val color: Color, val title: String, val description: String)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"
    }

    override fun onBackPressed() { }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        } catch (e: Exception) {
            // Fail silently if vibrator is unavailable
        }
    }
}
