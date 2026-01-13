package com.guardian.net

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // DARK THEME DASHBOARD
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC),
                    error = Color(0xFFCF6679),
                    onSurface = Color.White
                )
            ) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    DashboardContent(padding = it)
                }
            }
        }
    }

    @Composable
    fun DashboardContent(padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HEADER
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "GUARDIAN ACTIVE",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))

            // CARD 1: SETUP CHECKLIST
            SetupCard()
            
            Spacer(modifier = Modifier.height(16.dp))

            // CARD 2: DANGER ZONE (Maintenance & Nuke)
            DangerZoneCard()

            Spacer(modifier = Modifier.weight(1f))

            // FOOTER: DIAGNOSTICS
            DiagnosticsRow()
        }
    }

    @Composable
    fun SetupCard() {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SYSTEM INTEGRITY", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                
                SetupItem("Device Admin", Icons.Filled.AdminPanelSettings) {
                    val comp = ComponentName(this@MainActivity, AdminReceiver::class.java)
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    startActivity(i)
                }
                SetupItem("Accessibility Monitor", Icons.Filled.Visibility) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                SetupItem("Overlay Permission", Icons.Filled.Layers) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                SetupItem("Battery Immunity", Icons.Filled.BatteryAlert) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                }
            }
        }
    }

    @Composable
    fun SetupItem(label: String, icon: ImageVector, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.DarkGray)
        }
        Divider(color = Color(0xFF2C2C2C))
    }

    @Composable
    fun DangerZoneCard() {
        // STATES
        var showMaintenanceDialog by remember { mutableStateOf(false) }
        var maintenancePass by remember { mutableStateOf("") }
        
        var showNukeRequestDialog by remember { mutableStateOf(false) }
        var showNukeConfirmDialog by remember { mutableStateOf(false) }
        var nukeMsg by remember { mutableStateOf("") }
        var otpInput by remember { mutableStateOf("") }
        var passInput by remember { mutableStateOf("") }
        var nukeError by remember { mutableStateOf("") }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1212)), // Dark Red Tint
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CONTROL PROTOCOLS", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF5252))
                Spacer(modifier = Modifier.height(12.dp))

                // MAINTENANCE BTN
                Button(
                    onClick = { showMaintenanceDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), // Green
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("MAINTENANCE UNLOCK")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // NUKE ROW
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                   OutlinedButton(
                       onClick = { 
                            val status = NukeManager.canRequestNuke(applicationContext)
                            if (status == "OK") showNukeRequestDialog = true else { nukeMsg = status; showNukeRequestDialog = true }
                       },
                       colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                       modifier = Modifier.weight(1f).padding(end = 4.dp),
                       shape = RoundedCornerShape(8.dp)
                   ) { Text("INITIATE NUKE") }
                   
                   OutlinedButton(
                       onClick = { showNukeConfirmDialog = true; nukeError = "" },
                       colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                       modifier = Modifier.weight(1f).padding(start = 4.dp),
                       shape = RoundedCornerShape(8.dp)
                   ) { Text("ENTER CODE") }
                }
            }
        }

        // --- DIALOGS (Kept Logic, Updated UI) ---
        if (showMaintenanceDialog) {
            AlertDialog(
                onDismissRequest = { showMaintenanceDialog = false; maintenancePass = "" },
                title = { Text("Maintenance Mode") },
                text = { 
                    OutlinedTextField(
                        value = maintenancePass, 
                        onValueChange = { maintenancePass = it },
                        label = { Text("Admin Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
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

        if (showNukeRequestDialog) {
            AlertDialog(
                onDismissRequest = { showNukeRequestDialog = false; nukeMsg = "" },
                title = { Text("Nuke Protocol", color = Color.Red) },
                text = { 
                    Column {
                        if (nukeMsg.isNotEmpty() && !nukeMsg.startsWith("OTP")) {
                             Text(nukeMsg, color = Color.Red)
                        } else {
                             Text("WARNING: This disables protection for uninstallation.")
                             Spacer(modifier = Modifier.height(10.dp))
                             if (nukeMsg.startsWith("OTP")) {
                                 SelectionContainer {
                                     Text(nukeMsg, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("GENERATE") }
                    } else {
                        TextButton(onClick = { showNukeRequestDialog = false }) { Text("DONE") }
                    }
                }
            )
        }

        if (showNukeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showNukeConfirmDialog = false },
                title = { Text("Confirm Nuke") },
                text = {
                    Column {
                        OutlinedTextField(value = passInput, onValueChange = { passInput = it; nukeError = "" }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = otpInput, onValueChange = { otpInput = it; nukeError = "" }, label = { Text("3-Hour OTP") }, singleLine = true)
                        if (nukeError.isNotEmpty()) Text(nukeError, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                            } else { nukeError = res }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("DISABLE") }
                }
            )
        }
    }

    @Composable
    fun DiagnosticsRow() {
        var showStatsDialog by remember { mutableStateOf(false) }
        var showLogDialog by remember { mutableStateOf(false) }
        var logContent by remember { mutableStateOf("") }
        var showBrowserList by remember { mutableStateOf(false) }
        var browserListText by remember { mutableStateOf("") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showStatsDialog = true }) { Text("STATS", color = Color.Gray, fontSize = 12.sp) }
            TextButton(onClick = {
                logContent = DebugLogger.getLogs()
                showLogDialog = true
            }) { Text("LOGS", color = Color.Gray, fontSize = 12.sp) }
            TextButton(onClick = {
                val list = LockManager.getDetectedBrowsers(applicationContext)
                browserListText = list.joinToString("\n")
                showBrowserList = true
            }) { Text("BROWSERS", color = Color.Gray, fontSize = 12.sp) }
        }

        // --- STATS DIALOG ---
        if (showStatsDialog) {
            // Auto-refresh trigger
            var refresh by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) { while(true) { delay(1000); refresh++ } }
            
            AlertDialog(
                onDismissRequest = { showStatsDialog = false },
                title = { Text("Performance Monitor") },
                text = { 
                   Column {
                       Text("Uptime: ${StatsManager.getFormattedUptime()}")
                       Spacer(modifier = Modifier.height(4.dp))
                       Text("RAM: ${StatsManager.currentMem} MB")
                       LinearProgressIndicator(progress = StatsManager.currentMem / 256f, modifier = Modifier.fillMaxWidth().padding(top=4.dp))
                       Spacer(modifier = Modifier.height(4.dp))
                       Text("CPU: ${String.format("%.2f", StatsManager.currentCpu)}%")
                       LinearProgressIndicator(progress = (StatsManager.currentCpu / 10f).coerceIn(0f, 1f), color = if(StatsManager.currentCpu > 5) Color.Red else Color.Green, modifier = Modifier.fillMaxWidth().padding(top=4.dp))
                       Spacer(modifier = Modifier.height(16.dp))
                       Text("Battery: ${StatsManager.currentBatteryPct}% " + if (StatsManager.isCharging) "(Charging)" else "")
                   }
                },
                confirmButton = { TextButton(onClick = { showStatsDialog = false }) { Text("CLOSE") } }
            )
        }

        // --- LOGS DIALOG ---
        if (showLogDialog) {
            AlertDialog(
                onDismissRequest = { showLogDialog = false },
                title = { Text("System Logs") },
                text = { 
                    SelectionContainer {
                        Text(logContent, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = Color.LightGray)
                    }
                },
                confirmButton = { TextButton(onClick = { showLogDialog = false }) { Text("CLOSE") } }
            )
        }

        // --- BROWSER DIALOG ---
        if (showBrowserList) {
            AlertDialog(
                onDismissRequest = { showBrowserList = false },
                title = { Text("Detected Browsers") },
                text = { SelectionContainer { Text(browserListText) } },
                confirmButton = { TextButton(onClick = { showBrowserList = false }) { Text("CLOSE") } }
            )
        }
    }
}
