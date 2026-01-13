package com.guardian.net

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
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
                   // 3. Overlay
                   startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }) { Text("3. Allow Overlays") }

                Button(onClick = {
                   // 4. Battery Immunity
                   val i = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                   i.data = android.net.Uri.parse("package:$packageName")
                   startActivity(i)
                }) { Text("4. Unrestricted Battery") }

                Spacer(modifier = Modifier.height(40.dp))
                Divider(color = Color.DarkGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(40.dp))

                // --- UNLOCK FOR MAINTENANCE ---
                var showMaintenanceDialog by remember { mutableStateOf(false) }
                var maintenancePass by remember { mutableStateOf("") }

                Button(
                    onClick = { showMaintenanceDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)) // Green
                ) { Text("UNLOCK FOR MAINTENANCE") }

                if (showMaintenanceDialog) {
                    AlertDialog(
                        onDismissRequest = { showMaintenanceDialog = false; maintenancePass = "" },
                        title = { Text("Maintenance Unlock") },
                        text = { 
                            Column {
                                Text("Pauses protection for 5 minutes.")
                                Spacer(modifier = Modifier.height(10.dp))
                                TextField(
                                    value = maintenancePass, 
                                    onValueChange = { maintenancePass = it },
                                    label = { Text("Password") },
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (maintenancePass == LockManager.ADMIN_PASS) {
                                    LockManager.unlock(applicationContext)
                                    showMaintenanceDialog = false
                                    maintenancePass = ""
                                }
                            }) { Text("UNLOCK") }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(30.dp))

                // --- NUKE PROTOCOL UI ---
                var showNukeRequestDialog by remember { mutableStateOf(false) }
                var showNukeConfirmDialog by remember { mutableStateOf(false) }
                var nukeMsg by remember { mutableStateOf("") }

                // 1. INITIATE NUKE
                OutlinedButton(
                    onClick = { 
                        val status = NukeManager.canRequestNuke(applicationContext)
                        if (status == "OK") {
                            showNukeRequestDialog = true
                        } else {
                            nukeMsg = status
                            showNukeRequestDialog = true
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) { Text("INITIATE NUKE") }

                if (showNukeRequestDialog) {
                    AlertDialog(
                        onDismissRequest = { showNukeRequestDialog = false; nukeMsg = "" },
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
                                Button(onClick = { showNukeRequestDialog = false }) { Text("DONE") }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. ENTER NUKE CODE
                var otpInput by remember { mutableStateOf("") }
                var passInput by remember { mutableStateOf("") }
                var nukeError by remember { mutableStateOf("") }

                OutlinedButton(
                    onClick = { showNukeConfirmDialog = true; nukeError = "" },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                ) { Text("ENTER NUKE CODE") }

                if (showNukeConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showNukeConfirmDialog = false },
                        title = { Text("Confirm Nuke") },
                        text = {
                            Column {
                                TextField(
                                    value = passInput, onValueChange = { passInput = it; nukeError = "" },
                                    label = { Text("Admin Password") },
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(
                                    value = otpInput, onValueChange = { otpInput = it; nukeError = "" },
                                    label = { Text("3-Hour OTP") },
                                    singleLine = true
                                )
                                if (nukeError.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(text = nukeError, color = Color.Red, fontSize = 14.sp)
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (passInput != LockManager.ADMIN_PASS) {
                                    nukeError = "Incorrect Password"
                                } else {
                                    val res = NukeManager.verifyOtp(applicationContext, otpInput)
                                    if (res == "OK") {
                                        NukeManager.setProtectionDisabled(applicationContext, true)
                                        showNukeConfirmDialog = false
                                    } else {
                                        nukeError = res
                                    }
                                }
                            }) { Text("DISABLE PROTECTION") }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // DEBUG: BROWSER CHECKER
                var showBrowserList by remember { mutableStateOf(false) }
                var browserListText by remember { mutableStateOf("") }

                TextButton(onClick = {
                    val list = LockManager.getDetectedBrowsers(applicationContext)
                    browserListText = list.joinToString("\n")
                    showBrowserList = true
                }) { Text("DEBUG: SHOW DETECTED BROWSERS", color = Color.DarkGray, fontSize = 10.sp) }

                // --- PERFORMANCE STATS BUTTON ---
                var showStatsDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showStatsDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA000))
                ) { Text("VIEW APP PERFORMANCE") }

                if (showStatsDialog) {
                    // REFRESHER for Live UI
                    var refreshTrigger by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) {
                        while(true) {
                            delay(1000)
                            refreshTrigger++
                        }
                    }

                    AlertDialog(
                        onDismissRequest = { showStatsDialog = false },
                        title = { Text("Real-Time Usage") },
                        text = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 1. NUMBERS
                                Text("Uptime: ${StatsManager.getFormattedUptime()}", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("RAM Usage: ${StatsManager.currentMem} MB")
                                LinearProgressIndicator(
                                    progress = StatsManager.currentMem / 256f, // Assumed 256MB max for service
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = Color.Blue,
                                    trackColor = Color.DarkGray
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("CPU Load: ${String.format("%.2f", StatsManager.currentCpu)}%")
                                LinearProgressIndicator(
                                    progress = (StatsManager.currentCpu / 10f).coerceIn(0f, 1f), // Scale 0-10%
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = if (StatsManager.currentCpu > 5f) Color.Red else Color.Green,
                                    trackColor = Color.DarkGray
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // 2. BATTERY SECTION
                                Text("Battery Impact", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(5.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Device Level:", color = Color.Gray)
                                    Text("${StatsManager.currentBatteryPct}% " + if (StatsManager.isCharging) "(Charging)" else "")
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Session Drop:", color = Color.Gray)
                                    val drop = StatsManager.startBatteryPct - StatsManager.currentBatteryPct
                                    Text(if (drop >= 0) "$drop%" else "+${-drop}%") // Handle charging gain
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Est. App Drain:", color = Color.Gray)
                                    Text(StatsManager.getBatteryImpact(), color = if (StatsManager.currentCpu > 5f) Color.Red else Color(0xFF00C853))
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Text("CPU History (Last 50 Samples)", fontSize = 12.sp)
                                
                                // 3. MINI GRAPH
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .background(Color(0xFF222222))
                                        .padding(4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val history = StatsManager.history.toList() // Snapshot
                                    history.forEach { point ->
                                        // Height relative to 10% CPU
                                        val barHeight = (point.cpuPercent / 10f).coerceIn(0.05f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(barHeight)
                                                .padding(horizontal = 1.dp)
                                                .background(if (point.cpuPercent > 5f) Color.Red else Color.Green)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showStatsDialog = false }) { Text("CLOSE") }
                        }
                    )
                }

                // --- DEBUG LOGS BUTTON ---
                var showLogDialog by remember { mutableStateOf(false) }
                var logContent by remember { mutableStateOf("") }

                TextButton(onClick = {
                    logContent = DebugLogger.getLogs()
                    showLogDialog = true
                }) { Text("DEBUG: SHOW LOGS", color = Color.Cyan, fontSize = 10.sp) }

                if (showLogDialog) {
                    AlertDialog(
                        onDismissRequest = { showLogDialog = false },
                        title = { Text("System Logs") },
                        text = {
                            SelectionContainer {
                                Text(logContent, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        },
                        confirmButton = {
                            Row {
                                TextButton(onClick = { 
                                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Logs", logContent))
                                }) { Text("COPY") }
                                
                                TextButton(onClick = { 
                                    DebugLogger.clear()
                                    logContent = ""
                                }) { Text("CLEAR") }

                                Button(onClick = { showLogDialog = false }) { Text("CLOSE") }
                            }
                        }
                    )
                }

                if (showBrowserList) {
                    AlertDialog(
                        onDismissRequest = { showBrowserList = false },
                        title = { Text("Detected Browsers") },
                        text = {
                            SelectionContainer {
                                Text(browserListText, fontSize = 12.sp)
                            }
                        },
                        confirmButton = {
                            Button(onClick = { showBrowserList = false }) { Text("CLOSE") }
                        }
                    )
                }
            }
        }
    }
}