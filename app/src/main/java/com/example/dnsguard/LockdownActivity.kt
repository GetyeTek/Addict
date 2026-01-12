package com.example.dnsguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LockdownActivity : ComponentActivity() {

    // STATE: Observable state to update UI without recreating Activity
    private var blockTypeState = mutableStateOf("DNS")

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Refresh state when a new command arrives
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "DNS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init state from first intent
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "DNS"
        
        // INTRUSION: Remove system bars
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        // INTRUSION: Remove system bars
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            val blockType by blockTypeState

            // DISTINCT UI THEMES
            val (bgColor, mainColor, icon, title, desc, btnText) = when (blockType) {
                "BROWSER" -> Preset(
                    Color(0xFF202124), Color(0xFF4285F4),
                    android.R.drawable.ic_dialog_alert, "RESTRICTED APP", 
                    "Maintenance Mode Active.\nUse Chrome for official logging.", "OPEN CHROME"
                )
                "BROWSER_VIOLATION" -> Preset(
                    Color(0xFF2B0000), Color(0xFFFF0033), 
                    android.R.drawable.ic_delete, "BROWSER LOCKED", 
                    "Security Violation Detected.\nLocked for 5 minutes.", "CLOSE BROWSER"
                )
                "TELEGRAM_SUSPENDED" -> Preset(
                    Color(0xFF1A1A00), Color(0xFFFFD700), 
                    android.R.drawable.ic_lock_idle_lock, "TELEGRAM LOCKED", 
                    "Security strikes exceeded.\nLocked for 10 minutes.", "ACKNOWLEDGE"
                )
                "SECURITY_TRIPWIRE" -> Preset(
                    Color(0xFF000000), Color(0xFF00FF00), 
                    android.R.drawable.ic_secure, "SECURITY ALERT", 
                    "Do not tamper with settings.", "GO BACK"
                )
                else -> Preset(
                    Color(0xFF050505), Color(0xFFEF4565), 
                    android.R.drawable.stat_sys_warning, "SYSTEM INSECURE", 
                    "Private DNS must be Strict (hostname).", "FIX DNS"
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // ICON
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = icon),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(mainColor)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // HEADER
                    Text(
                        text = title,
                        color = mainColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // BODY
                    Text(
                        text = desc,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // ACTION BUTTON
                    Button(
                        onClick = { 
                             when (blockType) {
                                 "DNS" -> {
                                     val i = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                     i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                     startActivity(i)
                                 }
                                 "BROWSER" -> {
                                     // Redirect to Chrome
                                     try {
                                         val i = packageManager.getLaunchIntentForPackage("com.android.chrome")
                                         if (i != null) {
                                             i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                             startActivity(i)
                                             finishAffinity()
                                         }
                                     } catch (e: Exception) {
                                         // If chrome missing, go home
                                         val i = Intent(Intent.ACTION_MAIN)
                                         i.addCategory(Intent.CATEGORY_HOME)
                                         i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                         startActivity(i)
                                     }
                                 }
                                 else -> {
                                     // Default: Go Home
                                     val i = Intent(Intent.ACTION_MAIN)
                                     i.addCategory(Intent.CATEGORY_HOME)
                                     i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                     startActivity(i)
                                 }
                             }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = mainColor)
                    ) {
                        Text(btnText, color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    // UNLOCK BUTTON (Only for DNS/System Lock)
                    if (blockType == "DNS") {
                        Spacer(modifier = Modifier.height(40.dp))
                        
                        var showDialog by remember { mutableStateOf(false) }
                        var password by remember { mutableStateOf("") }

                        androidx.compose.material3.OutlinedButton(
                            onClick = { showDialog = true },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = mainColor,
                                containerColor = Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, mainColor.copy(alpha = 0.5f))
                        ) {
                            Text("UNLOCK ADMIN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (showDialog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { showDialog = false },
                                title = { Text("Enter Password") },
                                text = { 
                                    androidx.compose.material3.TextField(
                                        value = password, 
                                        onValueChange = { password = it },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.Button(onClick = {
                                        if (password == LockManager.ADMIN_PASS) {
                                            LockManager.unlock(applicationContext)
                                            finishAffinity()
                                        }
                                    }) { Text("UNLOCK") }
                                }
                            )
                        }
                    }
                }
            }

            // LIVE MONITOR LOOP
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                scope.launch {
                    while(true) {
                        // 1. CONDITIONAL EXIT
                        if (blockType == "BROWSER" || blockType == "BROWSER_VIOLATION" || blockType == "TELEGRAM_SUSPENDED" || blockType == "SECURITY_TRIPWIRE") {
                             // User must press CLOSE APP or wait for suspension to end (if they stay on screen)
                             if (blockType == "TELEGRAM_SUSPENDED" && !LockManager.isTelegramBanned(applicationContext)) {
                                 finishAffinity()
                             }
                             if (blockType == "BROWSER_VIOLATION" && !LockManager.isBrowserBanned(applicationContext)) {
                                 finishAffinity()
                             }
                        } else {
                             // DNS Mode: Exit only if fixed. 
                             // NOTE: We do NOT exit if Unlocked, because this screen might be showing "BROWSER" (Maintenance Mode)
                             if (blockType == "DNS" && DnsManager.isSecure(applicationContext)) {
                                 finishAffinity()
                             }
                        }
                        delay(1000)
                    }
                }
            }
        }
    }

    // DATA CLASS FOR UI PRESETS
    data class Preset(
        val bg: Color, val main: Color, val icon: Int, 
        val title: String, val desc: String, val btn: String
    )

    // Trap user
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }
}