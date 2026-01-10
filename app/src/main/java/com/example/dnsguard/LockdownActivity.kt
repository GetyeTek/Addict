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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blockType = intent.getStringExtra("BLOCK_TYPE") ?: "DNS"
        
        // INTRUSION: Remove system bars
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            // CyberUI Reused Theme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050505)), // Void Black
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // DYNAMIC HEADER
                    Text(
                        text = if (blockType == "BROWSER") "UNSAFE BROWSER" else "CONNECTION UNSECURE",
                        color = Color(0xFFEF4565), // Neon Red
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // DYNAMIC BODY
                    Text(
                        text = if (blockType == "BROWSER") 
                               "Only official Chrome is allowed during maintenance." 
                               else "Private DNS must be set to 'Strict'.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // DYNAMIC BUTTON
                    if (blockType == "BROWSER") {
                        Button(
                            onClick = { 
                                val i = Intent(Intent.ACTION_MAIN)
                                i.addCategory(Intent.CATEGORY_HOME)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(i)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4565))
                        ) {
                            Text("CLOSE APP", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { 
                                val i = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(i)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4565))
                        ) {
                            Text("FIX NOW", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                    
                    // UNLOCK BUTTON
                    var showDialog by remember { mutableStateOf(false) }
                    var password by remember { mutableStateOf("") }

                    androidx.compose.material3.TextButton(onClick = { showDialog = true }) {
                        Text("UNLOCK ADMIN", color = Color.DarkGray, fontSize = 12.sp)
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

            // LIVE MONITOR LOOP
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                scope.launch {
                    while(true) {
                        // 1. If this is a BROWSER BLOCK, we DO NOT exit just because we are unlocked.
                        // User must press 'CLOSE APP' or leave the bad app manually.
                        if (blockType != "BROWSER") {
                             // Normal Mode: Exit if DNS fixed OR Unlocked
                             if (DnsManager.isSecure(applicationContext) || LockManager.isUnlocked(applicationContext)) {
                                 finishAffinity() // Release lock
                             }
                        }
                        delay(1000)
                    }
                }
            }
        }
    }

    // Trap user
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }
}