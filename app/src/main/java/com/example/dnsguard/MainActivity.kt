package com.example.dnsguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("DNS GUARD SETUP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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

                Button(onClick = {
                   // 4. Battery Immunity (Unkillable)
                   val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                   i.data = android.net.Uri.parse("package:$packageName")
                   startActivity(i)
                }) { Text("4. Unrestricted Battery") }

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { LockManager.lock(applicationContext) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("LOCK NOW") }

                Spacer(modifier = Modifier.height(20.dp))

                // --- TESTING: UNINSTALL MODE ---
                var uninstallMode by remember { mutableStateOf(LockManager.isUninstallMode(applicationContext)) }
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TESTING: UNINSTALL MODE", color = Color.Yellow, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    androidx.compose.material3.Switch(
                        checked = uninstallMode,
                        onCheckedChange = { 
                            uninstallMode = it
                            LockManager.setUninstallMode(applicationContext, it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(50.dp))

                // --- NUKE PROTOCOL UI ---
                var showNukeDialog by remember { mutableStateOf(false) }
                var showConfirmDialog by remember { mutableStateOf(false) }
                var nukeMsg by remember { mutableStateOf("") }
                var otpInput by remember { mutableStateOf("") }
                var passInput by remember { mutableStateOf("") }

                // 1. REQUEST NUKE BUTTON
                OutlinedButton(
                    onClick = { 
                        val status = NukeManager.canRequestNuke(applicationContext)
                        if (status == "OK") {
                            showNukeDialog = true
                        } else {
                            nukeMsg = status
                            showNukeDialog = true // Reuse dialog for error
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) { Text("INITIATE NUKE") }

                if (showNukeDialog) {
                    AlertDialog(
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
                                        SelectionContainer {
                                            Text(nukeMsg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (!nukeMsg.startsWith("OTP")) {
                                Button(onClick = {
                                    val code = NukeManager.generateOtp(applicationContext)
                                    nukeMsg = "OTP: $code"
                                }) { Text("GENERATE OTP") }
                            } else {
                                Button(onClick = { showNukeDialog = false }) { Text("DONE") }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. CONFIRM NUKE BUTTON
                TextButton(
                    onClick = { showConfirmDialog = true }
                ) { Text("ENTER NUKE CODE", color = Color.Gray) }

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text("Confirm Nuke") },
                        text = {
                            Column {
                                TextField(
                                    value = passInput, onValueChange = { passInput = it },
                                    label = { Text("Admin Password") }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = otpInput, onValueChange = { otpInput = it },
                                    label = { Text("3-Hour OTP") }
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
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