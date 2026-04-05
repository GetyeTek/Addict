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
        
        // Update Detection Logic
        try {
            val prefs = getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
            val savedVersion = prefs.getInt("last_run_version", -1)
            
            if (currentVersion > savedVersion && savedVersion != -1) {
                DebugLogger.log("UPGRADE", "System upgraded from v$savedVersion to v$currentVersion")
                // You could trigger a cleanup or a specific "Thank you for updating" nag here
            }
            prefs.edit().putInt("last_run_version", currentVersion).apply()
        } catch (e: Exception) { }

        MainScope().launch {
            AppCache.loadApps(applicationContext)
            
            // Programmatically manage Quick Settings Tiles
            val pm = applicationContext.packageManager

            // Enable the Kill Switch (Debug Mode Active)
            pm.setComponentEnabledSetting(
                android.content.ComponentName(applicationContext, DebugKillTileService::class.java),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )

            // Keep Whisper Tile enabled
            pm.setComponentEnabledSetting(
                android.content.ComponentName(applicationContext, WhisperTileService::class.java),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
        
        setContent {
            val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
            var isDark by remember { mutableStateOf(prefs.getBoolean("is_dark", true)) }

            val colorScheme = if (isDark) {
                darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF121212),
                    primary = Color(0xFF00FFFF),
                    error = Color(0xFFFF0055),
                    onSurface = Color.White,
                    outline = Color(0xFF333333)
                )
            } else {
                lightColorScheme(
                    background = Color.White,
                    surface = Color(0xFFF9FAFB),
                    primary = Color(0xFF4F46E5),
                    error = Color(0xFFB91C1C),
                    onSurface = Color.Black,
                    outline = Color(0xFFE5E7EB)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DashboardContent(isDark) { 
                        isDark = it
                        prefs.edit().putBoolean("is_dark", it).apply()
                    }
                }
            }
        }
    }

    @Composable
    fun DashboardContent(isDark: Boolean, onThemeToggle: (Boolean) -> Unit) {
        val ctx = LocalContext.current
        var setupDone by remember { mutableStateOf(LockManager.isSetupComplete(ctx)) }
        var currentScreen by remember { mutableStateOf("DASHBOARD") }
        var editingAlarm by remember { mutableStateOf<AlarmData?>(null) }

        if (!setupDone) {
            OnboardingGate(onSetupComplete = { setupDone = true })
        } else {
            // Keep Dashboard alive if permissions are broken (and accessibility is already granted)
            LaunchedEffect(Unit) {
                while(true) {
                    // DASHBOARD NAG: Only force to front during the 20m Boot Grace.
                    if (LockManager.isBootGraceActive() && 
                        LockManager.isSystemCompromised(applicationContext) && 
                        !LockManager.isPermissionFixActive(applicationContext)) {
                         val intent = Intent(applicationContext, MainActivity::class.java).apply {
                             addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                         }
                         startActivity(intent)
                    }
                    delay(2000)
                }
            }

            LaunchedEffect(Unit) {
                val intent = Intent(ctx, WatcherService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            }

            when (currentScreen) {
                "ALARM_HUB" -> AlarmHubScreen(
                    onAdd = { editingAlarm = null; currentScreen = "ALARM_EDITOR" },
                    onEdit = { alarm -> editingAlarm = alarm; currentScreen = "ALARM_EDITOR" },
                    onBack = { currentScreen = "DASHBOARD" }
                )
                "ALARM_EDITOR" -> AlarmEditorScreen(
                    alarm = editingAlarm,
                    onCancel = { currentScreen = "ALARM_HUB" },
                    onSave = { updated -> 
                        val list = AlarmStore.getAlarms(ctx).toMutableList()
                        list.removeIf { it.id == updated.id }
                        list.add(updated)
                        AlarmStore.saveAlarms(ctx, list)
                        
                        // Show Toast with remaining time
                        val diff = AlarmScheduler.getTimeToAlarm(updated)
                        val hours = (diff / (1000 * 60 * 60)).toInt()
                        val mins = ((diff / (1000 * 60)) % 60).toInt()
                        val toastMsg = if (hours > 0) "Alarm set for $hours hours and $mins minutes from now" 
                                       else "Alarm set for $mins minutes from now"
                        android.widget.Toast.makeText(ctx, toastMsg, android.widget.Toast.LENGTH_LONG).show()
                        
                        currentScreen = "ALARM_HUB" 
                    }
                )
                else -> DashboardMain(isDark, onThemeToggle) { currentScreen = "ALARM_HUB" }
            }
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
                Text("SUBMIT OR UNINSTALL", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Give me total control or get lost. Your choice.", color = Color.Gray, fontSize = 14.sp)
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
    fun DashboardMain(isDark: Boolean, onThemeToggle: (Boolean) -> Unit, onAlarmClick: () -> Unit) {
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
                     Text("YOU'RE BEHAVING... FOR NOW", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            FocusCard()
            Spacer(modifier = Modifier.height(16.dp))
            DeepFocusCard()
            Spacer(modifier = Modifier.height(16.dp))
            AlarmCard(onAlarmClick)
            Spacer(modifier = Modifier.height(16.dp))
            AppManagerCard()
            Spacer(modifier = Modifier.height(16.dp))
            AppVaultCard()
            Spacer(modifier = Modifier.height(16.dp))
            BrowserApprovalCard()
            Spacer(modifier = Modifier.height(16.dp))
            NightPassCard()
            Spacer(modifier = Modifier.height(16.dp))
            EmergencyProtocolCard()
            Spacer(modifier = Modifier.height(16.dp))
            MaintenanceCard()
            Spacer(modifier = Modifier.height(16.dp))
            ExperimentalFeaturesCard()
            Spacer(modifier = Modifier.height(24.dp))
            DiagnosticsRow()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            var showWhisperConfirm by remember { mutableStateOf(false) }
            Button(
                onClick = { showWhisperConfirm = true },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Warning, null)
                Spacer(Modifier.width(12.dp))
                Text("THE DEVIL IS WHISPERING", fontWeight = FontWeight.Black)
            }

            if (showWhisperConfirm) {
                AlertDialog(
                    onDismissRequest = { showWhisperConfirm = false },
                    title = { Text("Initiate Exorcism?") },
                    text = { Text("This will lock your phone and start a 60-second timer. If you don't take 30 steps, the penalty music will play at max volume.") },
                    confirmButton = { 
                        Button(onClick = { 
                            LockManager.startWhisperMode(ctx)
                            showWhisperConfirm = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("I'M READY") } 
                    },
                    dismissButton = { TextButton(onClick = { showWhisperConfirm = false }) { Text("CANCEL") } }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { onThemeToggle(!isDark) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isDark) "BLIND ME (LIGHT MODE)" else "BACK TO THE SHADOWS",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
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
                    HorizontalDivider(color = Color(0xFF7F1D1D))
                }
            }
        }
    }

    @Composable
    fun EmergencyProtocolCard() {
        val ctx = LocalContext.current
        val isDark = MaterialTheme.colorScheme.background == Color.Black
        var status by remember { mutableStateOf(NukeManager.getStatus(ctx)) }
        var showConfirmRequestDialog by remember { mutableStateOf(false) }

        // Poll Status
        LaunchedEffect(Unit) {
            while(true) {
                status = NukeManager.getStatus(ctx)
                delay(1000)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF271A1A) else Color(0xFFFEE2E2)),
            border = BorderStroke(2.dp, Color(0xFFF87171)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Dangerous, null, tint = Color(0xFFF87171))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THE LOSER'S EMERGENCY EXIT", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                                if (status.isWaiting) {
                    // PHASE 1: Waiting for 2-hour fuse
                    val hours = status.remainingWaitMs / 3600000
                    val mins = (status.remainingWaitMs % 3600000) / 60000
                    val secs = (status.remainingWaitMs % 60000) / 1000
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format("%02d:%02d:%02d", hours, mins, secs), 
                            color = Color(0xFFFCD34D), fontSize = 32.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("PROTOCOL FUSE BURNING", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Guard will drop automatically when timer hits zero.", color = Color.DarkGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                } 
                else if (status.isProtectionDisabled) {
                    // PHASE 2: Freedom window (or manual Debug kill)
                    val mins = (status.remainingWaitMs / 60000)
                    val secs = (status.remainingWaitMs % 60000) / 1000
                    
                    Text("PROTECTION DROPPED", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    if (status.remainingWaitMs > 0) {
                        Text(String.format("Restoring in %02d:%02d", mins, secs), color = Color.Gray, fontSize = 14.sp)
                    } else {
                        Text("MANUAL OVERRIDE ACTIVE", color = Color.Gray, fontSize = 14.sp)
                    }
                }
                else {
                    // Completely Idle State
                    Column {
                        if (!status.isWindowOpen) {
                            Text("Window opens at 06:00", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Button(onClick = { 
                            val check = NukeManager.canRequestNuke(ctx)
                            if (check == "OK") {
                                showConfirmRequestDialog = true
                            } else {
                                android.widget.Toast.makeText(ctx, check, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }, 
                        enabled = status.isWindowOpen,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF450A0A), 
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF1A1A1A),
                            disabledContentColor = Color(0xFF333333)
                        ),
                        border = BorderStroke(1.dp, if(status.isWindowOpen) Color(0xFF7F1D1D) else Color(0xFF222222)),
                        modifier = Modifier.fillMaxWidth()) {
                            Text("I'M A QUITTER")
                        }
                    }
                }
            }
        }

        if (showConfirmRequestDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmRequestDialog = false },
                title = { Text("EXTREME CAUTION") },
                text = { Text("You are initiating the Nuclear Option.\n\n1. You MUST wait 2 hours while the fuse burns.\n2. Protection will automatically drop for exactly 1 hour.\n3. Do not do this unless it is a genuine emergency.", color = Color.White) },
                confirmButton = { 
                    Button(onClick = { 
                        NukeManager.startQuitterTimer(ctx)
                        showConfirmRequestDialog = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("INITIATE PROTOCOL")
                    }
                },
                dismissButton = { TextButton(onClick = { showConfirmRequestDialog = false }) { Text("CANCEL") } }
            )
        }


    }

    @Composable
    fun ExperimentalFeaturesCard() {
        val ctx = LocalContext.current
        var resurrect by remember { mutableStateOf(LockManager.isExpEnabled(ctx, "resurrect")) }
        var noDns by remember { mutableStateOf(LockManager.isExpEnabled(ctx, "no_dns")) }

        // RESET: Ensure hidden experiments revert to strict mode
        LaunchedEffect(Unit) {
            LockManager.setExpEnabled(ctx, "no_app_info", false)
            LockManager.setExpEnabled(ctx, "no_perm_scan", false)
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)), border = BorderStroke(1.dp, Color(0xFF374151)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("EXPERIMENTAL PROTOCOLS", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                
                ExpToggle("Resurrection Protocol", "App fights back against Nuke/Kill signals.", resurrect) { resurrect = it; LockManager.setExpEnabled(ctx, "resurrect", it) }
                ExpToggle("Silence DNS Warnings", "Disables the fix-it overlay for DNS.", noDns) { noDns = it; LockManager.setExpEnabled(ctx, "no_dns", it) }
                
                var ytEnabled by remember { mutableStateOf(ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).getBoolean("yt_guard_enabled", false)) }
                ExpToggle("YouTube Viewing Request", "Wait 30m to watch for 60m.", ytEnabled) { 
                    ytEnabled = it
                    LockManager.setYouTubeGuard(ctx, it)
                }
            }
        }
    }

    @Composable
    fun ExpToggle(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(desc, color = Color.Gray, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00FFFF)))
        }
    }

    @Composable
    fun MaintenanceCard() {
        var showMainte by remember { mutableStateOf(false) }
        var pass by remember { mutableStateOf("") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SYSTEM MAINTENANCE", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Button(onClick = { showMainte = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White)) {
                    Text("DNS REPAIR MODE")
                }
            }
        }
        if (showMainte) {
            AlertDialog(onDismissRequest = { showMainte = false }, title = { Text("DNS Access (5 Mins)") }, text = {
                OutlinedTextField(value = pass, onValueChange = { pass = it }, visualTransformation = PasswordVisualTransformation(), placeholder = { Text("Password") })
            }, confirmButton = {
                Button(onClick = { if (pass == LockManager.ADMIN_PASS) { LockManager.unlock(applicationContext); showMainte = false } }) { Text("START WINDOW") }
            })
        }
    }

    @Composable
    fun AlarmCard(onClick: () -> Unit) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black), 
            border = BorderStroke(2.dp, Color(0xFFFF5252)),
            modifier = Modifier.fillMaxWidth().clickable { onClick() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("THE RECKONING", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color(0xFFFF5252))
                Text("Wake up or meet the devil.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }

    @Composable
    fun DeepFocusCard() {
        var showDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        var remaining by remember { mutableStateOf(LockManager.getDeepFocusRemaining(ctx)) }

        LaunchedEffect(Unit) {
            while(true) {
                remaining = LockManager.getDeepFocusRemaining(ctx)
                kotlinx.coroutines.delay(1000)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black), 
            border = BorderStroke(2.dp, Color(0xFFFACC15)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SOLITARY CONFINEMENT", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color(0xFFFACC15))
                if (remaining > 0) {
                    val mins = (remaining / 60000) + 1
                    Text("$mins MINS REMAINING", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                } else {
                    Button(
                        onClick = { showDialog = true }, 
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF854D0E), contentColor = Color.White)
                    ) {
                        Icon(Icons.Filled.CenterFocusStrong, null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Text(" START WHITELIST", fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }
        if (showDialog) DeepFocusDialog(onDismiss = { showDialog = false })
    }

    @Composable
    fun DeepFocusDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        var filter by remember { mutableStateOf("") }
        val appList = AppCache.getCachedApps() ?: listOf()
        val selectedApps = remember { mutableStateListOf<String>() }
        var duration by remember { mutableStateOf("60") }

        Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1E293B)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Zen Mode Whitelist", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("Only these apps will work. Everything else is blocked.", color = Color.Gray, fontSize = 12.sp)
                    
                    OutlinedTextField(value = duration, onValueChange = { if (it.all { c -> c.isDigit() }) duration = it },
                        label = { Text("Duration (Minutes)") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))

                    OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Search allowed apps...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    
                    val filtered = appList.filter { it.name.contains(filter, true) || it.pkg.contains(filter, true) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered.size) { index ->
                            val app = filtered[index]
                            val isSelected = selectedApps.contains(app.pkg)
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                if (isSelected) selectedApps.remove(app.pkg) else selectedApps.add(app.pkg)
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(app.name, color = Color.White)
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Button(onClick = { 
                            LockManager.startDeepFocus(ctx, selectedApps.toSet(), duration.toIntOrNull() ?: 60)
                            android.widget.Toast.makeText(ctx, "Zen Mode Activated!", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss() 
                        }, enabled = selectedApps.isNotEmpty()) { Text("GO ZEN") }
                    }
                }
            }
        }
    }

    @Composable
    fun FocusCard() {
        var showDialog by remember { mutableStateOf(false) }
        var mins by remember { mutableStateOf("15") }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black), 
            border = BorderStroke(2.dp, Color(0xFF6366F1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CHAIN TO THE DESK", fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, color = Color(0xFF6366F1))
                Button(
                    onClick = { showDialog = true }, 
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color.White),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA), contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Text(" ACTIVATE", fontWeight = FontWeight.Black, color = Color.White)
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
    fun BrowserApprovalCard() {
        var showDialog by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)), border = BorderStroke(2.dp, Color(0xFF10B981)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BROWSER APPROVAL", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
                Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46))) {
                    Text("MANAGE LEARNED APPS")
                }
            }
        }
        if (showDialog) BrowserApprovalDialog(onDismiss = { showDialog = false })
    }

    @Composable
    fun BrowserApprovalDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        var learnedApps by remember { mutableStateOf(LockManager.getLearnedApps(ctx)) }
        
        LaunchedEffect(Unit) {
            while(true) {
                learnedApps = LockManager.getLearnedApps(ctx)
                delay(1000)
            }
        }

        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF0F172A)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Learned Web Apps", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        IconButton(onClick = {
                            val approvedList = learnedApps.keys.filter { LockManager.isAppApproved(ctx, it) }
                            val textToCopy = if (approvedList.isEmpty()) "No approved learned apps." else approvedList.joinToString("\n")
                            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("ApprovedApps", textToCopy))
                            android.widget.Toast.makeText(ctx, "Copied ${approvedList.size} approved apps", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, null, tint = Color(0xFF10B981))
                        }
                    }
                    Text("Apps caught using WebViews. 1-hour quarantine required.", color = Color.Gray, fontSize = 12.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        val list = learnedApps.toList()
                        items(list.size) { index ->
                            val (pkg, _) = list[index]
                            val isApproved = LockManager.isAppApproved(ctx, pkg)
                            val isBanned = LockManager.isAppPermanentlyBanned(ctx, pkg)
                            val remaining = LockManager.getQuarantineRemaining(ctx, pkg)
                            
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pkg.substringAfterLast("."), color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(pkg, color = Color.Gray, fontSize = 10.sp)
                                }
                                
                                when {
                                    isApproved -> {
                                        Text("APPROVED", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    isBanned -> {
                                        Text("PERM-BANNED", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    remaining > 0 -> {
                                        val m = remaining / 60000
                                        val s = (remaining % 60000) / 1000
                                        Text(String.format("%02d:%02d", m, s), color = Color(0xFFF87171), fontWeight = FontWeight.Black)
                                    }
                                    else -> {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { LockManager.banAppPermanently(ctx, setOf(pkg)); learnedApps = LockManager.getLearnedApps(ctx) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text("DENY", color = Color.White, fontSize = 10.sp)
                                            }
                                            Button(
                                                onClick = { LockManager.approveApp(ctx, pkg); learnedApps = LockManager.getLearnedApps(ctx) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                contentPadding = PaddingValues(horizontal = 12.dp)
                                            ) {
                                                Text("APPROVE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF1E293B))
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("CLOSE") }
                }
            }
        }
    }

    @Composable
    fun AppVaultCard() {
        var showVault by remember { mutableStateOf(false) }
        val isDark = MaterialTheme.colorScheme.background == Color.Black
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFE0E7FF)),
            border = BorderStroke(2.dp, Color(0xFF818CF8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THE BLACKLIST", style = MaterialTheme.typography.labelMedium, color = Color(0xFF818CF8))
                }
                Button(onClick = { showVault = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA), contentColor = Color.White)) {
                    Text("MANAGE BAN LIST")
                }
            }
        }
        if (showVault) AppVaultDialog(onDismiss = { showVault = false })
    }

    @Composable
    fun AppVaultDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        var filter by remember { mutableStateOf("") }
        val appList = AppCache.getCachedApps() ?: listOf()
        
        // PERSISTENCE: Load existing locks with CLEANUP
        // We filter out expired temporary locks so the checkboxes reflect reality
        val prefs = ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        val existingPerm = prefs.getStringSet("perm_banned_apps", emptySet()) ?: emptySet()
        val rawTemp = prefs.getStringSet("temp_locked_apps", emptySet()) ?: emptySet()
        
        val now = System.currentTimeMillis()
        val activeTemp = rawTemp.filter {
            val ts = it.substringAfter(":").toLongOrNull() ?: 0L
            ts > now
        }
        
        val initialSelected = (existingPerm + activeTemp.map { it.substringBefore(":") }).toSet()

        val selectedApps = remember { mutableStateListOf<String>().apply { addAll(initialSelected) } }
        var lockMinutes by remember { mutableStateOf("30") }
        var isPermanent by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1E293B)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("App Vault", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    
                    Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPermanent, onCheckedChange = { isPermanent = it })
                        Text("PERMANENT BAN", color = if(isPermanent) Color.Red else Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (!isPermanent) {
                        OutlinedTextField(
                            value = lockMinutes, 
                            onValueChange = { if (it.all { c -> c.isDigit() }) lockMinutes = it },
                            label = { Text("Lock Duration (Minutes)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Search apps...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    
                    val filtered = appList.filter { it.name.contains(filter, true) || it.pkg.contains(filter, true) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered.size) { index ->
                            val app = filtered[index]
                            val isSelected = selectedApps.contains(app.pkg)
                            Row(modifier = Modifier.fillMaxWidth().clickable { 
                                if (isSelected) selectedApps.remove(app.pkg) else selectedApps.add(app.pkg)
                            }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = isSelected, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(app.name, color = Color.White)
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("CANCEL") }
                        Button(
                            onClick = {
                                if (isPermanent) {
                                    LockManager.banAppPermanently(ctx, selectedApps.toSet())
                                    android.widget.Toast.makeText(ctx, "Permanently Banned ${selectedApps.size} apps", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    LockManager.lockAppsTemporarily(ctx, selectedApps.toSet(), lockMinutes.toIntOrNull() ?: 0)
                                    android.widget.Toast.makeText(ctx, "Locked ${selectedApps.size} apps for $lockMinutes mins", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            },
                            enabled = selectedApps.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = if(isPermanent) Color.Red else Color(0xFF6366F1))
                        ) { Text(if(isPermanent) "EXECUTE BAN" else "LOCK APPS") }
                    }
                }
            }
        }
    }

    @Composable
    fun AppManagerCard() {
        var showList by remember { mutableStateOf(false) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("WEAKNESSES", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Button(onClick = { showList = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White)) {
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
        var isWindowOpen by remember { mutableStateOf(LockManager.isNightPassActivationWindow()) }
        var isUsedToday by remember { mutableStateOf(LockManager.isTonightPassed(ctx)) }
        var showConfirm by remember { mutableStateOf(false) }

        // Reactivity Heartbeat
        LaunchedEffect(Unit) {
            while(true) {
                remaining = LockManager.getRemainingNightPasses(ctx)
                isWindowOpen = LockManager.isNightPassActivationWindow()
                isUsedToday = LockManager.isTonightPassed(ctx)
                kotlinx.coroutines.delay(5000)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("LAME EXCUSES", color = Color(0xFF60A5FA), style = MaterialTheme.typography.labelMedium)
                
                val canUse = isWindowOpen && remaining > 0 && !isUsedToday
                
                Button(onClick = { showConfirm = true },
                    enabled = canUse,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), 
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB), 
                        contentColor = Color.White, 
                        disabledContainerColor = Color(0xFF1E3A8A)
                    )) {
                    val label = when {
                        isUsedToday -> "ALREADY ACTIVATED"
                        !isWindowOpen -> "WINDOW OPENS AT 06:00"
                        remaining == 0 -> "NO PASSES LEFT"
                        else -> "SKIP BEDTIME ($remaining LEFT)"
                    }
                    Text(label)
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
        var showStats by remember { mutableStateOf(false) }
        val ctx = LocalContext.current

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showStats = true }, border = BorderStroke(1.dp, Color(0xFF00FFFF)), shape = RoundedCornerShape(4.dp)) {
                Text("SYSTEM AUTOPSY", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            TextButton(onClick = { showLogs = true }) {
                Text("VIEW LOGS", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }

        if (showLogs) {
            var logText by remember { mutableStateOf(DebugLogger.getLogs()) }
            AlertDialog(
                onDismissRequest = { showLogs = false },
                title = { 
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Security Logs")
                        Text("${logText.lines().size} entries", fontSize = 10.sp, color = Color.Gray)
                    }
                },
                text = {
                    Box(modifier = Modifier.height(450.dp).background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(8.dp)) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(
                                    text = if (logText.isEmpty()) "No logs recorded yet." else logText,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(modifier = Modifier.weight(1f), onClick = { DebugLogger.clear(); logText = "" }) { Text("CLEAR", color = Color.Red) }
                        Button(modifier = Modifier.weight(1f), onClick = { 
                            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("Logs", logText))
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("COPY") }
                        Button(modifier = Modifier.weight(1f), onClick = { showLogs = false }) { Text("DONE") }
                    }
                }
            )
        }
        if (showStats) StatsDialog(onDismiss = { showStats = false })
    }

    @Composable
    fun StatsDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        val onSurface = MaterialTheme.colorScheme.onSurface
        var cpu by remember { mutableStateOf(StatsManager.currentCpu) }
        var mem by remember { mutableStateOf(StatsManager.currentMem) }
        var uptime by remember { mutableStateOf(StatsManager.getFormattedUptime()) }
        
        LaunchedEffect(Unit) {
            while(true) {
                StatsManager.update(ctx)
                cpu = StatsManager.currentCpu
                mem = StatsManager.currentMem
                uptime = StatsManager.getFormattedUptime()
                kotlinx.coroutines.delay(1000)
            }
        }

        Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, Color(0xFF00FFFF)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("GUARDIAN CORE STATS", color = Color(0xFF00FFFF), fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 20.sp)
                    HorizontalDivider(color = Color(0xFF00FFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                    
                    StatRow("RUNTIME", uptime, onSurface)
                    StatRow("CPU LOAD", String.format("%.1f%%", cpu), if (cpu > 5) Color.Yellow else Color(0xFF10B981))
                    StatRow("MEM USAGE", "$mem MB", Color(0xFF00FFFF))
                    StatRow("BATTERY", StatsManager.getBatteryImpact(), onSurface)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("SECURITY AUDIT", color = Color(0xFFFF0055), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    HorizontalDivider(color = Color(0xFFFF0055), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                    
                    val dns = if (DnsManager.isSecure(ctx)) "SECURE" else "HIJACKED"
                    StatRow("DNS STATUS", dns, if (dns == "SECURE") Color(0xFF10B981) else Color.Red)
                    
                    val totalPens = ctx.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE).getLong("total_penalties_served", 0L)
                    StatRow("TAMPER EVENTS", "$totalPens Detected", if (totalPens > 0) Color.Yellow else Color(0xFF10B981))
                    
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFFF), contentColor = Color.Black), shape = RoundedCornerShape(4.dp)) {
                        Text("RETURN TO TERMINAL", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }

    @Composable
    fun StatRow(label: String, value: String, color: Color) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
    }

    data class PermissionItem(val label: String, val action: (Context) -> Unit)

    private fun getMissingPermissions(ctx: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. LOCATION FIRST (Requires Scavenger Hunt for 'Allow all the time')
        val hasFine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasBackground = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasFine || !hasBackground) {
            list.add(PermissionItem("Location (Set to 'Allow all the time')") { 
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                i.data = Uri.parse("package:$packageName")
                it.startActivity(i)
                android.widget.Toast.makeText(it, "Go to Permissions -> Location -> Allow all the time", android.widget.Toast.LENGTH_LONG).show()
            })
        }
        
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

        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName)
        }
        if (mode != android.app.AppOpsManager.MODE_ALLOWED) {
            list.add(PermissionItem("Usage Access (Wellbeing)") { 
                it.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                android.widget.Toast.makeText(it, "Find Guardian and allow Usage Access", android.widget.Toast.LENGTH_LONG).show()
            })
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                list.add(PermissionItem("Physical Activity") { 
                    (it as MainActivity).requestPermissions(arrayOf(android.Manifest.permission.ACTIVITY_RECOGNITION), 102)
                })
            }
        }

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!nm.isNotificationPolicyAccessGranted) {
                list.add(PermissionItem("Do Not Disturb Access") { 
                    it.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                })
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!am.canScheduleExactAlarms()) {
                list.add(PermissionItem("Exact Alarms (Allow in Settings)") { 
                    it.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                })
            }
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