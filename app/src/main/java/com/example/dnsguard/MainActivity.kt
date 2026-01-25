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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AppItem(val name: String, val pkg: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            DebugLogger.logCrash(throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate(savedInstanceState)
        
        MainScope().launch {
            AppCache.loadApps(applicationContext)
        }
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    primary = Color(0xFF6366F1),
                    error = Color(0xFFEF4444),
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DashboardContent()
                }
            }
        }
    }

    @Composable
    fun DashboardContent() {
        val ctx = LocalContext.current
        var setupDone by remember { mutableStateOf(LockManager.isSetupComplete(ctx)) }
        
        if (!setupDone) {
            OnboardingGate(onSetupComplete = { setupDone = true })
        } else {
            LaunchedEffect(Unit) {
                val intent = Intent(ctx, WatcherService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }
            DashboardMain()
        }
    }

    @Composable
    fun OnboardingGate(onSetupComplete: () -> Unit) {
        // Keep strict gating for first-time launch
        val ctx = LocalContext.current
        var allGood by remember { mutableStateOf(false) }
        var missing by remember { mutableStateOf(listOf<String>()) }

        LaunchedEffect(Unit) {
            while (true) {
                val list = getMissingPermissions(ctx)
                missing = list.map { it.label }
                if (list.isEmpty()) {
                    LockManager.setSetupComplete(ctx)
                    onSetupComplete()
                    break
                }
                delay(1000)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("SETUP OR GET OUT", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Give me powers or delete the app.", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(32.dp))

                missing.forEach { 
                    Text("• $it", color = Color(0xFFEF4444), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 4.dp))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = {
                    val list = getMissingPermissions(ctx)
                    list.firstOrNull()?.action?.invoke(ctx)
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))) {
                    Text("DO IT")
                }
            }
        }
    }

    @Composable
    fun DashboardMain() {
        val ctx = LocalContext.current
        var missingPerms by remember { mutableStateOf(listOf<PermissionItem>()) }

        // Dynamic Permission Monitor
        LaunchedEffect(Unit) {
            while(true) {
                missingPerms = getMissingPermissions(ctx)
                delay(2000)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            if (missingPerms.isNotEmpty()) {
                CriticalAlertCard(missingPerms)
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                // Secure Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                     Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                     Spacer(modifier = Modifier.width(8.dp))
                     Text("AIN'T NOBODY GETTING IN", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            FocusCard()
            Spacer(modifier = Modifier.height(16.dp))
            AppManagerCard()
            Spacer(modifier = Modifier.height(16.dp))
            NightPassCard()
            Spacer(modifier = Modifier.height(16.dp))
            EmergencyProtocolCard()
            Spacer(modifier = Modifier.height(16.dp))
            MaintenanceCard()
            Spacer(modifier = Modifier.height(24.dp))
            DiagnosticsRow()
        }
    }

    @Composable
    fun CriticalAlertCard(missing: List<PermissionItem>) {
        val ctx = LocalContext.current
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)), border = BorderStroke(1.dp, Color(0xFFEF4444)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("YOU BROKE IT, DUMMY", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                missing.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { item.action(ctx) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.label, color = Color.White, fontWeight = FontWeight.Medium)
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
                    }
                    Divider(color = Color(0xFF7F1D1D))
                }
            }
        }
    }

    @Composable
    fun EmergencyProtocolCard() {
        val ctx = LocalContext.current
        var status by remember { mutableStateOf(NukeManager.getStatus(ctx)) }
        var showOtpDialog by remember { mutableStateOf(false) }
        var showEntryDialog by remember { mutableStateOf(false) }
        var generatedOtp by remember { mutableStateOf("") }

        // Poll Status
        LaunchedEffect(Unit) {
            while(true) {
                status = NukeManager.getStatus(ctx)
                delay(1000)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF271A1A)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dangerous, null, tint = Color(0xFFF87171))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THE NUCLEAR OPTION", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                if (status.isProtectionDisabled) {
                    Text("SHIELDS DOWN", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("You got 1 hour before I lock you up again.", color = Color.Gray, fontSize = 12.sp)
                } 
                else if (status.isWaiting) {
                    val hours = status.remainingWaitMs / 3600000
                    val mins = (status.remainingWaitMs % 3600000) / 60000
                    val secs = (status.remainingWaitMs % 60000) / 1000
                    Text(String.format("HOLD YOUR HORSES: %02d:%02d:%02d", hours, mins, secs), 
                        color = Color(0xFFFCD34D), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { }, enabled = false, 
                        colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF451A1A), disabledContentColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("TOO LATE")
                    }
                } 
                else {
                    // Idle or Ready
                    if (status.isReady) {
                         Button(onClick = { showEntryDialog = true }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("PROVE IT'S YOU")
                        }
                    } else if (status.otpGenerated) {
                        Text("Code generated. Don't lose it.", color = Color.Gray)
                    } else {
                         Button(onClick = {
                             val check = NukeManager.canRequestNuke(ctx)
                             if (check == "OK") {
                                 generatedOtp = NukeManager.generateOtp(ctx)
                                 showOtpDialog = true
                             } else {
                                 android.widget.Toast.makeText(ctx, check, android.widget.Toast.LENGTH_LONG).show()
                             }
                         }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF450A0A), contentColor = Color(0xFFF87171)),
                            border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                            modifier = Modifier.fillMaxWidth()) {
                            Text("I WANT TO QUIT")
                        }
                    }
                }
            }
        }

        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = { Text("NO TURNING BACK") },
                text = { 
                    Column {
                        Text("Write this down. If you lose it, you're screwed for 3 hours.", color = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(generatedOtp, fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFFF87171), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                },
                confirmButton = { Button(onClick = { showOtpDialog = false }) { Text("I WROTE IT DOWN") } }
            )
        }

        if (showEntryDialog) {
            var input by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showEntryDialog = false },
                title = { Text("CONFIRM STOP") },
                text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Enter Code") }, singleLine = true) },
                confirmButton = { 
                    Button(onClick = { 
                        val res = NukeManager.verifyOtp(ctx, input)
                        if (res == "OK") {
                            NukeManager.setProtectionDisabled(ctx, true)
                            showEntryDialog = false
                        } else {
                            android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("CONFIRM") } 
                }
            )
        }
    }

    @Composable
    fun MaintenanceCard() {
        var showMainte by remember { mutableStateOf(false) }
        var pass by remember { mutableStateOf("") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("GOD MODE", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Button(onClick = { showMainte = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                    Text("CHEAT CODE")
                }
            }
        }
        if (showMainte) {
            AlertDialog(onDismissRequest = { showMainte = false }, title = { Text("Admin Access") }, text = {
                OutlinedTextField(value = pass, onValueChange = { pass = it }, visualTransformation = PasswordVisualTransformation())
            }, confirmButton = {
                Button(onClick = { if (pass == LockManager.ADMIN_PASS) { LockManager.unlock(applicationContext); showMainte = false } }) { Text("UNLOCK") }
            })
        }
    }

    @Composable
    fun FocusCard() {
        var showDialog by remember { mutableStateOf(false) }
        var mins by remember { mutableStateOf("15") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LOCK IN", style = MaterialTheme.typography.labelMedium, color = Color(0xFF818CF8))
                Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA))) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(16.dp))
                    Text(" SHUT UP & WORK")
                }
            }
        }
        if (showDialog) {
            AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Focus Session") }, text = {
                OutlinedTextField(value = mins, onValueChange = { if (it.all { c -> c.isDigit() }) mins = it }, label = { Text("Duration (Minutes)") })
            }, confirmButton = {
                Button(onClick = { LockManager.setUserLockout(applicationContext, mins.toIntOrNull() ?: 0); showDialog = false }) { Text("BEGIN") }
            })
        }
    }

    @Composable
    fun AppManagerCard() {
        var showList by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("WEAKNESSES", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Button(onClick = { showList = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                    Text("WHITELIST CRAP")
                }
            }
        }
        if (showList) SafeAppListDialog(onDismiss = { showList = false })
    }

    @Composable
    fun SafeAppListDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        var filter by remember { mutableStateOf("") }
        var appList by remember { mutableStateOf(AppCache.getCachedApps() ?: listOf()) }
        
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1E293B)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Authorized Apps", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Search...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
                    
                    val filtered = appList.filter { it.name.contains(filter, true) || it.pkg.contains(filter, true) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered.size) { index ->
                            val app = filtered[index]
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                LockManager.setSafeSession(ctx, app.pkg)
                                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                ctx.startActivity(i)
                                onDismiss()
                            }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color(0xFF334155)) {
                                    Box(contentAlignment = Alignment.Center) { Text(app.name.take(1).uppercase(), color = Color.White) }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(app.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.pkg, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("CLOSE") }
                }
            }
        }
    }

    @Composable
    fun NightPassCard() {
        val ctx = LocalContext.current
        var remaining by remember { mutableStateOf(LockManager.getRemainingNightPasses(ctx)) }
        var showConfirm by remember { mutableStateOf(false) }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("HALL PASSES", color = Color(0xFF60A5FA), style = MaterialTheme.typography.labelMedium)
                Button(onClick = { showConfirm = true },
                    enabled = LockManager.isNightPassActivationWindow() && remaining > 0 && !LockManager.isTonightPassed(ctx),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), disabledContainerColor = Color(0xFF1E3A8A))) {
                    Text("SKIP BEDTIME ($remaining LEFT)")
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Confirm Night Pass") },
                text = { Text("This will consume 1 of your 3 weekly passes.\n\nIt grants immunity from the Night Lock (11 PM - 5 AM) for tonight only.\n\nAre you sure you have a legitimate need?") },
                confirmButton = {
                    Button(onClick = {
                        if (LockManager.useNightPass(ctx)) {
                            remaining = LockManager.getRemainingNightPasses(ctx)
                        }
                        showConfirm = false
                    }) { Text("ACTIVATE") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) { Text("CANCEL") }
                }
            )
        }
    }

    @Composable
    fun DiagnosticsRow() {
        var showLogs by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { showLogs = true }) { Text("NERD LOGS", color = Color.Gray, fontSize = 12.sp) }
        }
        if (showLogs) {
            AlertDialog(onDismissRequest = { showLogs = false }, title = { Text("Logs") }, text = {
                SelectionContainer { Text(DebugLogger.getLogs(), fontSize = 10.sp, color = Color.LightGray) }
            }, confirmButton = { TextButton(onClick = { showLogs = false }) { Text("CLOSE") } })
        }
    }

    data class PermissionItem(val label: String, val action: (Context) -> Unit)

    private fun getMissingPermissions(ctx: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()
        
        if (!Settings.canDrawOverlays(ctx)) {
            list.add(PermissionItem("Display Over Apps") { 
                LockManager.startPermissionFixSession(it)
                it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) 
            })
        }
        if (!(ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)) {
             list.add(PermissionItem("Ignore Battery Opt") { 
                 it.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) 
             })
        }
        
        val expected = "$packageName/${GuardService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (!enabledServices.contains(expected)) {
            list.add(PermissionItem("Accessibility") { it.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        }
        
        if (!(ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(ComponentName(ctx, AdminReceiver::class.java))) {
            list.add(PermissionItem("Device Admin") { 
                 val comp = ComponentName(it, AdminReceiver::class.java)
                 val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                 i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                 it.startActivity(i)
            })
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channels = listOf("dns_guard_channel", "watcher_channel")
            if (channels.any { nm.getNotificationChannel(it)?.importance == android.app.NotificationManager.IMPORTANCE_NONE }) {
                list.add(PermissionItem("Notifications") { 
                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    it.startActivity(i)
                })
            }
        }

        // CRITICAL: Remote Kill Switch Permissions
        if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
             list.add(PermissionItem("Call Monitoring") { 
                 requestPermissions(arrayOf(android.Manifest.permission.READ_CALL_LOG, android.Manifest.permission.READ_PHONE_STATE), 101)
             })
        }

        return list
    }
}