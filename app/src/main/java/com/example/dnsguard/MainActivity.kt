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
import androidx.compose.material3.Switch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            DebugLogger.logCrash(throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate(savedInstanceState)
        
        // PRE-FETCH APPS IMMEDIATELY
        kotlinx.coroutines.MainScope().launch {
            AppCache.loadApps(applicationContext)
        }
        
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
        val ctx = applicationContext
        // Use mutable state to trigger immediate recomposition
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
            DashboardMain(padding)
        }
    }

    @Composable
    fun OnboardingGate(onSetupComplete: () -> Unit) {
        val ctx = applicationContext
        var hasOverlay by remember { mutableStateOf(false) }
        var hasBattery by remember { mutableStateOf(false) }
        var hasAccessibility by remember { mutableStateOf(false) }
        var hasAdmin by remember { mutableStateOf(false) }
        var hasPhonePermission by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                hasOverlay = Settings.canDrawOverlays(ctx)
                hasBattery = (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                hasAccessibility = isAccessibilityEnabled(ctx)
                hasAdmin = (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager).isAdminActive(android.content.ComponentName(ctx, AdminReceiver::class.java))
                hasPhonePermission = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasOverlay && hasBattery && hasAccessibility && hasAdmin && hasPhonePermission) {
                    LockManager.setSetupComplete(ctx)
                    onSetupComplete()
                }
                delay(1000)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = if (hasOverlay && hasBattery && hasAccessibility && hasAdmin && hasPhonePermission) Color(0xFF00E676) else Color.Red, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("MANDATORY SETUP", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("All permissions must be granted to continue", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(32.dp))

                PermissionRow("Appear on Top", hasOverlay, Icons.Filled.Layers) {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(i)
                }
                PermissionRow("Battery Immunity", hasBattery, Icons.Filled.BatteryAlert) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    startActivity(i)
                }
                PermissionRow("Accessibility Service", hasAccessibility, Icons.Filled.Visibility) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionRow("Device Admin", hasAdmin, Icons.Filled.AdminPanelSettings) {
                    val comp = android.content.ComponentName(ctx, AdminReceiver::class.java)
                    val i = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    startActivity(i)
                }
                PermissionRow("Kill-Switch Permission", hasPhonePermission, Icons.Filled.Phone) {
                    // Manual permission trigger for developer convenience
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    @Composable
    fun PermissionRow(label: String, isGranted: Boolean, icon: ImageVector, onClick: () -> Unit) {
        val color = if (isGranted) Color(0xFF00E676) else Color(0xFFCF6679)
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable(enabled = !isGranted) { onClick() },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(label, color = if (isGranted) Color.Gray else Color.White, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                if (isGranted) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color)
                } else {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.DarkGray)
                }
            }
        }
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = "$packageName/${GuardService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabledServices.contains(expected)
    }

    @Composable
    fun DashboardMain(padding: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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

            // CARD 2: FOCUS MODE
            FocusCard()

            Spacer(modifier = Modifier.height(16.dp))
            
            // CARD 2.5: APP MANAGER (Safe Path)
            AppManagerCard()
            
            Spacer(modifier = Modifier.height(16.dp))

            // CARD 2.6: NIGHT PASS
            NightPassCard()

            Spacer(modifier = Modifier.height(16.dp))

            // CARD 3: DANGER ZONE (Maintenance & Nuke)
            DangerZoneCard()

            Spacer(modifier = Modifier.weight(1f))

            // FOOTER: DIAGNOSTICS
            DiagnosticsRow()
        }
    }

    @Composable
    fun SetupCard() {
        val ctx = applicationContext
        var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
        var hasBattery by remember { mutableStateOf((ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)) }
        var hasAccessibility by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }
        var hasAdmin by remember { mutableStateOf((ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager).isAdminActive(android.content.ComponentName(ctx, AdminReceiver::class.java))) }

        LaunchedEffect(Unit) {
            while(true) {
                hasOverlay = Settings.canDrawOverlays(ctx)
                hasBattery = (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                hasAccessibility = isAccessibilityEnabled(ctx)
                hasAdmin = (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager).isAdminActive(android.content.ComponentName(ctx, AdminReceiver::class.java))
                delay(2000)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SYSTEM INTEGRITY", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                
                PermissionRow("Device Admin", hasAdmin, Icons.Filled.AdminPanelSettings) {
                    val comp = ComponentName(this@MainActivity, AdminReceiver::class.java)
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    startActivity(i)
                }
                PermissionRow("Accessibility Service", hasAccessibility, Icons.Filled.Visibility) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionRow("Overlay Permission", hasOverlay, Icons.Filled.Layers) {
                    val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(i)
                }
                PermissionRow("Battery Immunity", hasBattery, Icons.Filled.BatteryAlert) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    startActivity(i)
                }
            }
        }
    }



    @Composable
    fun NightPassCard() {
        val ctx = applicationContext
        var remaining by remember { mutableStateOf(LockManager.getRemainingNightPasses(ctx)) }
        var isWindow by remember { mutableStateOf(LockManager.isNightPassActivationWindow()) }
        var isTonightUsed by remember { mutableStateOf(LockManager.isTonightPassed(ctx)) }
        var showConfirm by remember { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("NIGHT PROTOCOL", style = MaterialTheme.typography.labelLarge, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Usage blocked 23:00 - 05:00 unless pass is used.", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showConfirm = true },
                        enabled = isWindow && remaining > 0 && !isTonightUsed,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                    ) {
                        Text(if (isTonightUsed) "PASS ACTIVE" else "USE NIGHT PASS ($remaining Left)")
                    }
                }

                if (!isWindow) {
                    Text("Decision window opens at 10:00 AM", fontSize = 10.sp, color = Color(0xFFEF4444), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Are you sure?") },
                text = { Text("This will use 1 of your 3 weekly passes. You only get this many to prevent sleep deprivation.") },
                confirmButton = {
                    Button(onClick = {
                        if (LockManager.useNightPass(ctx)) {
                            remaining = LockManager.getRemainingNightPasses(ctx)
                            isTonightUsed = true
                        }
                        showConfirm = false
                    }) { Text("I UNDERSTAND") }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("CANCEL") } }
            )
        }
    }

    @Composable
    fun LadderCard() {
        var enabled by remember { mutableStateOf(LockManager.isLadderEnabled(applicationContext)) }
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("USAGE LADDER", style = MaterialTheme.typography.labelLarge, color = Color(0xFF34D399))
                    Text("Auto-breaks every 20/40/60/90m", fontSize = 12.sp, color = Color.LightGray)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        LockManager.setLadderEnabled(applicationContext, it)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF34D399))
                )
            }
        }
    }

    data class AppItem(val name: String, val pkg: String)

    @Composable
    fun AppManagerCard() {
        var showList by remember { mutableStateOf(false) }
        val ctx = LocalContext.current

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MAINTENANCE PATH", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Manage other apps without triggering security traps.", fontSize = 12.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { showList = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                ) {
                    Icon(Icons.Filled.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN SAFE APP LIST")
                }
            }
        }

        if (showList) {
            var filter by remember { mutableStateOf("") }
            var isLoading by remember { mutableStateOf(true) }
            var appList by remember { mutableStateOf(listOf<AppItem>()) }

            LaunchedEffect(Unit) {
                val cached = AppCache.getCachedApps()
                if (cached != null) {
                    appList = cached
                    isLoading = false
                } else {
                    AppCache.loadApps(ctx)
                    appList = AppCache.getCachedApps() ?: listOf()
                    isLoading = false
                }
            }

            AlertDialog(
                onDismissRequest = { showList = false },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                content = {
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xFF1C1B1F),
                        tonalElevation = 6.dp
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                "Select Verified App",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White
                            )
                            Text(
                                "Bypass security traps for specific maintenance tasks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = filter,
                                onValueChange = { filter = it },
                                placeholder = { Text("Search installed apps...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                trailingIcon = {
                                    if (filter.isNotEmpty()) {
                                        IconButton(onClick = { filter = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFBB86FC),
                                    unfocusedBorderColor = Color(0xFF333333)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color(0xFFBB86FC))
                                }
                            } else {
                                val filtered = remember(filter, appList) {
                                    if (filter.isBlank()) appList 
                                    else appList.filter { it.name.contains(filter, ignoreCase = true) || it.pkg.contains(filter, ignoreCase = true) }
                                }

                                if (filtered.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        Text("No apps found", color = Color.DarkGray)
                                    }
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                                        items(filtered.size) { index ->
                                            val app = filtered[index]
                                            AppListRow(app) {
                                                LockManager.setSafeSession(ctx, app.pkg)
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                intent.data = Uri.parse("package:${app.pkg}")
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                ctx.startActivity(intent)
                                                showList = false
                                            }
                                            if (index < filtered.size - 1) {
                                                HorizontalDivider(modifier = Modifier.padding(horizontal = 56.dp), thickness = 0.5.dp, color = Color(0xFF333333))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = { showList = false },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("DISMISS", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            )
        }
        }
    }

    @Composable
    fun AppListRow(app: AppItem, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monogram Placeholder for App Icon
            Surface(
                modifier = Modifier.size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = Color(0xFF333333)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = app.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = app.pkg,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = Color(0xFF00E676).copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }

    @Composable
    fun FocusCard() {
        var showDialog by remember { mutableStateOf(false) }
        var minutesInput by remember { mutableStateOf("15") }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SELF-CONTROL", style = MaterialTheme.typography.labelLarge, color = Color(0xFF818CF8))
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = { showDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOCK ME OUT")
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Initiate Lockout") },
                text = {
                    Column {
                        Text("How many minutes of focus? (Max 1440)", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { if (it.all { char -> char.isDigit() }) minutesInput = it },
                            label = { Text("Minutes") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val mins = minutesInput.toIntOrNull() ?: 0
                            if (mins > 0) {
                                LockManager.setUserLockout(applicationContext, mins)
                                showDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) { Text("CONFIRM") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("CANCEL") }
                }
            )
        }
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
                var nukeRequestEnabled by remember { mutableStateOf(false) }
                var nukeConfirmEnabled by remember { mutableStateOf(false) }

                // REFRESH STATES EVERY 5 SECONDS
                LaunchedEffect(Unit) {
                    while(true) {
                        nukeRequestEnabled = NukeManager.canRequestNuke(applicationContext) == "OK"
                        nukeConfirmEnabled = NukeManager.isNukeReadyToConfirm(applicationContext)
                        kotlinx.coroutines.delay(5000)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                   OutlinedButton(
                       onClick = { showNukeRequestDialog = true },
                       enabled = nukeRequestEnabled,
                       colors = ButtonDefaults.outlinedButtonColors(
                           contentColor = Color(0xFFFF5252),
                           disabledContentColor = Color.DarkGray
                       ),
                       modifier = Modifier.weight(1f).padding(end = 4.dp),
                       shape = RoundedCornerShape(8.dp)
                   ) { Text("INITIATE NUKE") }
                   
                   OutlinedButton(
                       onClick = { showNukeConfirmDialog = true; nukeError = "" },
                       enabled = nukeConfirmEnabled,
                       colors = ButtonDefaults.outlinedButtonColors(
                           contentColor = Color.White,
                           disabledContentColor = Color.DarkGray
                       ),
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
