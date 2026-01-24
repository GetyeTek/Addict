package com.guardian.net

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
                "ROGUE_VIOLATION" -> Preset(
                    Color(0xFF4A0000), Color(0xFFFF4444), 
                    android.R.drawable.ic_delete, "APP BLOCKED", 
                    "Non-standard app violation detected.\nLocked for 30 minutes.", "UNINSTALL"
                )
                "USER_LOCKOUT" -> Preset(
                    Color(0xFF0F172A), Color(0xFF818CF8), 
                    android.R.drawable.ic_lock_power_off, "FOCUS MODE", 
                    "You are intentionally locked out.\nDeep work in progress.", "EMERGENCY CALL"
                )
                "BREAK_TIME" -> Preset(
                    Color(0xFF064E3B), Color(0xFF34D399), 
                    android.R.drawable.ic_menu_today, "TIME TO BREATHE", 
                    "Short break to protect your mind.\nLook away from the screen.", "EMERGENCY CALL"
                )
                "NIGHT_LOCK" -> Preset(
                    Color(0xFF020617), Color(0xFF94A3B8), 
                    android.R.drawable.ic_lock_idle_alarm, "SLEEP WELL", 
                    "Phone usage restricted until 5:00 AM.\nRest is the ultimate productivity.", "EMERGENCY"
                )
                "PENALTY" -> Preset(
                    Color(0xFF450a0a), Color(0xFFf87171), 
                    android.R.drawable.ic_delete, "CONSEQUENCE", 
                    "You have tampered with critical permissions.\nGuardian is now locked for 1 hour.", "INSECURE"
                )
                else -> Preset(
                    Color(0xFF050505), Color(0xFFEF4565), 
                    android.R.drawable.stat_sys_warning, "SYSTEM INSECURE", 
                    "Private DNS must be set to one of the following providers:", "FIX DNS"
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

                    // TIMER FOR USER LOCKOUT
                    if (blockType == "USER_LOCKOUT") {
                        var remaining by remember { mutableStateOf(LockManager.getLockoutRemainingMillis(applicationContext)) }
                        LaunchedEffect(Unit) {
                            while(remaining > 0) {
                                delay(1000)
                                remaining = LockManager.getLockoutRemainingMillis(applicationContext)
                            }
                        }
                        val mins = (remaining / 1000) / 60
                        val secs = (remaining / 1000) % 60
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    // TIMER FOR PENALTY
                    if (blockType == "PENALTY") {
                        var remaining by remember { mutableStateOf(LockManager.getPenaltyRemaining(applicationContext)) }
                        LaunchedEffect(Unit) {
                            while(remaining > 0) {
                                delay(1000)
                                remaining = LockManager.getPenaltyRemaining(applicationContext)
                            }
                        }
                        val mins = (remaining / 1000) / 60
                        val secs = (remaining / 1000) % 60
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = Color(0xFFf87171),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("DO NOT REPEAT", color = Color.Gray, fontSize = 12.sp)
                    }

                    // TIMER FOR BREAK TIME (Static Label Mode)
                    if (blockType == "BREAK_TIME") {
                        // Calculate duration dynamically on each recomposition
                        val remaining = LockManager.getBreakRemaining(applicationContext)
                        val breakLabel = when {
                            remaining > 8 * 60 * 1000L -> "10 Minute Refresh"
                            remaining > 4 * 60 * 1000L -> "5 Minute Reset"
                            remaining > 2 * 60 * 1000L -> "3 Minute Pause"
                            else -> "30 Second Micro-Break"
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = breakLabel,
                            color = Color(0xFF34D399),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        // Static icon instead of a progress indicator to keep it 'still'
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Spa,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // DNS SELECTOR (Tap to Copy)
                    if (blockType == "DNS") {
                        Spacer(modifier = Modifier.height(24.dp))
                        val ctx = LocalContext.current
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                            Text("TAP TO COPY:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            DnsManager.ALLOWED_HOSTNAMES.forEach { dns ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .border(1.dp, mainColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("DNS", dns))
                                            Toast.makeText(ctx, "Copied: $dns", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(16.dp)
                                ) {
                                    Text(dns, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // ACTION BUTTON
                    if (blockType == "NIGHT_LOCK" || blockType == "PENALTY") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val i = Intent(Intent.ACTION_DIAL)
                                    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    startActivity(i)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("PHONE")
                            }
                            Button(
                                onClick = {
                                    val i = packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage")
                                    if (i != null) {
                                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(i)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLOCK")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    if (blockType != "PENALTY") {
                        Button(
                            onClick = {
                                when (blockType) {
                                    "DNS" -> {
                                        val i = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(i)
                                    }
                                    "BROWSER" -> {
                                        try {
                                            val i = packageManager.getLaunchIntentForPackage("com.android.chrome")
                                            if (i != null) {
                                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                startActivity(i)
                                                finishAffinity()
                                            }
                                        } catch (e: Exception) {
                                            val i = Intent(Intent.ACTION_MAIN)
                                            i.addCategory(Intent.CATEGORY_HOME)
                                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            startActivity(i)
                                        }
                                    }
                                    "USER_LOCKOUT", "BREAK_TIME", "NIGHT_LOCK" -> {
                                        val i = Intent(Intent.ACTION_DIAL)
                                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        startActivity(i)
                                    }
                                    else -> {
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
                        if (blockType == "BROWSER" || blockType == "BROWSER_VIOLATION" || blockType == "TELEGRAM_SUSPENDED" || blockType == "SECURITY_TRIPWIRE" || blockType == "ROGUE_VIOLATION" || blockType == "USER_LOCKOUT") {
                             if (blockType == "USER_LOCKOUT" && !LockManager.isUserLockedOut(applicationContext)) {
                                 finishAffinity()
                             }
                             if (blockType == "BREAK_TIME" && LockManager.getBreakRemaining(applicationContext) <= 0) {
                                 finishAffinity()
                             }
                             if (blockType == "NIGHT_LOCK" && !LockManager.isNightLockActive(applicationContext)) {
                                 finishAffinity()
                             }
                             if (blockType == "PENALTY" && LockManager.getPenaltyRemaining(applicationContext) <= 0) {
                                 finishAffinity()
                             }
                             // User must press CLOSE APP or wait for suspension to end (if they stay on screen)
                             if (blockType == "TELEGRAM_SUSPENDED" && !LockManager.isTelegramBanned(applicationContext)) {
                                 finishAffinity()
                             }
                             if (blockType == "BROWSER_VIOLATION" && !LockManager.isBrowserBanned(applicationContext)) {
                                 finishAffinity()
                             }
                             if (blockType == "ROGUE_VIOLATION" && !LockManager.isNonStandardAppBanned(applicationContext)) {
                                 finishAffinity()
                             }
                        } else {
                             // DNS Mode: Exit if fixed OR if in Maintenance Mode
                             val isMaintenance = LockManager.isUnlocked(applicationContext)
                             if (blockType == "DNS" && (DnsManager.isSecure(applicationContext) || isMaintenance)) {
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