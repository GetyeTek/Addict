package com.example.dnsguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("DNS GUARD SETUP", color = Color.White)
                Spacer(modifier = Modifier.height(20.dp))
                
                Button(onClick = {
                    // 1. Device Admin
                    val comp = android.content.ComponentName(this@MainActivity, AdminReceiver::class.java)
                    val i = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    startActivity(i)
                }) { Text("1. Grant Admin") }
                
                Button(onClick = {
                    // 2. Accessibility
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("2. Enable Monitor") }

                Button(onClick = {
                   // 3. Overlay (Optional, but good for backup)
                   startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }) { Text("3. Allow Overlays") }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { LockManager.lock(applicationContext) },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("LOCK NOW") }

                Spacer(modifier = Modifier.height(50.dp))

                // --- NUKE PROTOCOL UI ---
                var showNukeDialog by remember { mutableStateOf(false) }
                var showConfirmDialog by remember { mutableStateOf(false) }
                var nukeMsg by remember { mutableStateOf("") }
                var otpInput by remember { mutableStateOf("") }
                var passInput by remember { mutableStateOf("") }

                // 1. REQUEST NUKE BUTTON
                androidx.compose.material3.OutlinedButton(
                    onClick = { 
                        val status = NukeManager.canRequestNuke(applicationContext)
                        if (status == "OK") {
                            showNukeDialog = true
                        } else {
                            nukeMsg = status
                            showNukeDialog = true // Reuse dialog for error
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) { Text("INITIATE NUKE") }

                if (showNukeDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showNukeDialog = false; nukeMsg = "" },
                        title = { Text("Nuke Protocol") },
                        text = { 
                            if (nukeMsg.isNotEmpty() && !nukeMsg.startsWith("OTP")) {
                                Text(nukeMsg, color = Color.Red)
                            } else {
                                Column {
                                    Text("WARNING: This will disable self-protection. You can uninstall the app after this.")
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("You must copy this OTP and return in 3 HOURS.")
                                    Spacer(modifier = Modifier.height(10.dp))
                                    if (nukeMsg.startsWith("OTP")) {
                                        androidx.compose.foundation.text.selection.SelectionContainer {
                                            Text(nukeMsg, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (!nukeMsg.startsWith("OTP")) {
                                androidx.compose.material3.Button(onClick = {
                                    val code = NukeManager.generateOtp(applicationContext)
                                    nukeMsg = "OTP: $code"
                                }) { Text("GENERATE OTP") }
                            } else {
                                androidx.compose.material3.Button(onClick = { showNukeDialog = false }) { Text("DONE") }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. CONFIRM NUKE BUTTON
                androidx.compose.material3.TextButton(
                    onClick = { showConfirmDialog = true }
                ) { Text("ENTER NUKE CODE", color = Color.Gray) }

                if (showConfirmDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text("Confirm Nuke") },
                        text = {
                            Column {
                                androidx.compose.material3.TextField(
                                    value = passInput, onValueChange = { passInput = it },
                                    label = { Text("Admin Password") }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.TextField(
                                    value = otpInput, onValueChange = { otpInput = it },
                                    label = { Text("3-Hour OTP") }
                                )
                            }
                        },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                if (passInput == LockManager.ADMIN_PASS) {
                                    val res = NukeManager.verifyOtp(applicationContext, otpInput)
                                    if (res == "OK") {
                                        NukeManager.setProtectionDisabled(applicationContext, true)
                                        showConfirmDialog = false
                                    } else {
                                        // Show error (simplified for UI)
                                        passInput = res // Hack to show msg in field
                                    }
                                }
                            }) { Text("DISABLE PROTECTION") }
                        }
                    )
                }
            }
        }
    }
}