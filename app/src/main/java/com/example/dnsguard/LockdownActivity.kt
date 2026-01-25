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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val type = blockTypeState.value
                val uiConfig = getUiConfig(type)
                var showPassDialog by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
                        colors = listOf(uiConfig.color.copy(alpha = 0.15f), Color.Black),
                        radius = 1800f
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
                color = config.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (type == "SYSTEM") {
                Text("PICK ONE, GENIUS", color = config.color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
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

            val hasEmergencyBypass = listOf("NIGHT_LOCK", "BREAK_TIME", "PENALTY", "USER_LOCKOUT").contains(type)

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
                            val dnsIntent = Intent("android.settings.PVT_DNS_SETTINGS").apply { 
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK 
                            }
                            val networkIntent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            
                            try {
                                // 1. Attempt deep link to Private DNS
                                context.startActivity(dnsIntent)
                            } catch (e: Exception) {
                                try {
                                    // 2. Fallback to Connections/Network menu (Closer than general settings)
                                    context.startActivity(networkIntent)
                                    Toast.makeText(context, "Tap 'More connection settings' for DNS", Toast.LENGTH_LONG).show()
                                } catch (e2: Exception) {
                                    // 3. Final resort: General Settings
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                                }
                            }
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
                val shouldClose = when (type) {
                    "SYSTEM" -> DnsManager.isSecure(ctx) || LockManager.isUnlocked(ctx)
                    "NIGHT_LOCK" -> !LockManager.isNightLockActive(ctx)
                    "BREAK_TIME" -> LockManager.getBreakRemaining(ctx) <= 0
                    "USER_LOCKOUT" -> !LockManager.isUserLockedOut(ctx)
                    "PENALTY" -> LockManager.getPenaltyRemaining(ctx) <= 0 && !LockManager.isSystemCompromised(ctx)
                    "BROWSER_VIOLATION" -> !LockManager.isBrowserBanned(ctx)
                    "TELEGRAM_SUSPENDED" -> !LockManager.isTelegramBanned(ctx)
                    else -> false
                }
                if (shouldClose) { finishAffinity(); break }
                delay(1500)
            }
        }
    }

    @Composable
    private fun MaintenanceDialog(onDismiss: () -> Unit, onCorrect: () -> Unit) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Maintenance Access") },
            text = { 
                OutlinedTextField(value = text, onValueChange = { text = it },
                    label = { Text("Admin Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            },
            confirmButton = { Button(onClick = { if (text == "1234") onCorrect() }) { Text("CONFIRM") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
        )
    }

    private fun getUiConfig(type: String): UiConfig {
        return when (type) {
            "BROWSER_VIOLATION" -> UiConfig(Icons.Default.Block, Color(0xFFFF3B30), "GET REKT", "No browsing for you.")
            "TELEGRAM_SUSPENDED" -> UiConfig(Icons.Default.Lock, Color(0xFFFF9500), "TOUCH GRASS", "You're grounded, kiddo.")
            "NIGHT_LOCK" -> UiConfig(Icons.Default.NightsStay, Color(0xFF5856D6), "GO TO SLEEP", "It's past your bedtime.")
            "BREAK_TIME" -> UiConfig(Icons.Default.Timer, Color(0xFF34C759), "CHILL OUT", "Sit your ass down for a bit.")
            "USER_LOCKOUT" -> UiConfig(Icons.Default.Timer, Color(0xFF007AFF), "LOCKED IN", "Do some actual work.")
            "PENALTY" -> UiConfig(Icons.Default.Warning, Color(0xFFFF2D55), "PENALTY BOX", "Stop messing with my settings.")
            "ROGUE_VIOLATION" -> UiConfig(Icons.Default.Block, Color(0xFF9333EA), "NICE TRY", "That app is banned. Get lost.")
            "SECURITY_TRIPWIRE" -> UiConfig(Icons.Default.Shield, Color(0xFFDC2626), "I SEE YOU", "Stay out of my settings.")
            else -> UiConfig(Icons.Default.Shield, Color(0xFFEF4565), "YOU THOUGHT?", "Fix your DNS or stare at this screen.")
        }
    }

    data class UiConfig(val icon: ImageVector, val color: Color, val title: String, val description: String)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"
    }

    override fun onBackPressed() { }
}
