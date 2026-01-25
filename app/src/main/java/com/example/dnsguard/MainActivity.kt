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
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFFBB86FC),
                    error = Color(0xFFCF6679),
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
        val ctx = LocalContext.current
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
                hasAdmin = (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(ComponentName(ctx, AdminReceiver::class.java))
                hasPhonePermission = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasOverlay && hasBattery && hasAccessibility && hasAdmin && hasPhonePermission) {
                    LockManager.setSetupComplete(ctx)
                    onSetupComplete()
                }
                delay(1000)
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFCF6679), modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("MANDATORY SETUP", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Permissions required for system protection", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(32.dp))

                PermissionRow("Appear on Top", hasOverlay, Icons.Filled.Layers) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                PermissionRow("Battery Immunity", hasBattery, Icons.Filled.BatteryAlert) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                }
                PermissionRow("Accessibility Service", hasAccessibility, Icons.Filled.Visibility) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                PermissionRow("Device Admin", hasAdmin, Icons.Filled.AdminPanelSettings) {
                    val comp = ComponentName(ctx, AdminReceiver::class.java)
                    val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                    startActivity(i)
                }
                PermissionRow("Kill-Switch (Phone)", hasPhonePermission, Icons.Filled.Phone) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            }
        }
    }

    @Composable
    fun DashboardMain() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("GUARDIAN ACTIVE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            SetupCard()
            Spacer(modifier = Modifier.height(16.dp))
            FocusCard()
            Spacer(modifier = Modifier.height(16.dp))
            AppManagerCard()
            Spacer(modifier = Modifier.height(16.dp))
            NightPassCard()
            Spacer(modifier = Modifier.height(16.dp))
            DangerZoneCard()
            Spacer(modifier = Modifier.height(24.dp))
            DiagnosticsRow()
        }
    }

    @Composable
    fun SetupCard() {
        val ctx = LocalContext.current
        var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
        var hasAccessibility by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SYSTEM INTEGRITY", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                PermissionRow("Accessibility", hasAccessibility, Icons.Filled.Visibility) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                PermissionRow("Overlay", hasOverlay, Icons.Filled.Layers) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            }
        }
    }

    @Composable
    fun PermissionRow(label: String, isGranted: Boolean, icon: ImageVector, onClick: () -> Unit) {
        val color = if (isGranted) Color(0xFF00E676) else Color(0xFFCF6679)
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !isGranted) { onClick() },
            shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E1E), border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(label, color = if (isGranted) Color.Gray else Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Icon(if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.ChevronRight, null, tint = color)
            }
        }
    }

    @Composable
    fun FocusCard() {
        var showDialog by remember { mutableStateOf(false) }
        var mins by remember { mutableStateOf("15") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("SELF-CONTROL", style = MaterialTheme.typography.labelLarge, color = Color(0xFF818CF8))
                Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(16.dp))
                    Text(" INITIATE LOCKOUT")
                }
            }
        }
        if (showDialog) {
            AlertDialog(onDismissRequest = { showDialog = false }, title = { Text("Focus Mode") }, text = {
                OutlinedTextField(value = mins, onValueChange = { if (it.all { c -> c.isDigit() }) mins = it }, label = { Text("Minutes") })
            }, confirmButton = {
                Button(onClick = { LockManager.setUserLockout(applicationContext, mins.toIntOrNull() ?: 0); showDialog = false }) { Text("START") }
            })
        }
    }



    @Composable
    fun NightPassCard() {
        val ctx = LocalContext.current
        var remaining by remember { mutableStateOf(LockManager.getRemainingNightPasses(ctx)) }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("NIGHT PROTOCOL", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelLarge)
                Button(onClick = { if (LockManager.useNightPass(ctx)) remaining = LockManager.getRemainingNightPasses(ctx) },
                    enabled = LockManager.isNightPassActivationWindow() && remaining > 0 && !LockManager.isTonightPassed(ctx),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("USE NIGHT PASS ($remaining Left)")
                }
            }
        }
    }

    @Composable
    fun AppManagerCard() {
        var showList by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("MAINTENANCE PATH", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                Button(onClick = { showList = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))) {
                    Icon(Icons.Filled.SettingsSuggest, null, modifier = Modifier.size(16.dp))
                    Text(" OPEN SAFE APP LIST")
                }
            }
        }
        if (showList) {
            SafeAppListDialog(onDismiss = { showList = false })
        }
    }

    @Composable
    fun SafeAppListDialog(onDismiss: () -> Unit) {
        val ctx = LocalContext.current
        var filter by remember { mutableStateOf("") }
        var appList by remember { mutableStateOf(AppCache.getCachedApps() ?: listOf()) }
        
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = RoundedCornerShape(28.dp), color = Color(0xFF1C1B1F)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Select Verified App", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text("Search...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
                    
                    val filtered = appList.filter { it.name.contains(filter, true) || it.pkg.contains(filter, true) }
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filtered.size) { index ->
                            val app = filtered[index]
                            AppListRow(app) { 
                                LockManager.setSafeSession(ctx, app.pkg)
                                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                ctx.startActivity(i)
                                onDismiss()
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("CLOSE") }
                }
            }
        }
    }

    @Composable
    fun AppListRow(app: AppItem, onClick: () -> Unit) {
        Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color(0xFF333333)) {
                Box(contentAlignment = Alignment.Center) { Text(app.name.take(1).uppercase(), color = Color.White) }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.pkg, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF00E676).copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }

    @Composable
    fun DangerZoneCard() {
        var showMainte by remember { mutableStateOf(false) }
        var pass by remember { mutableStateOf("") }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1212)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("CONTROL PROTOCOLS", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF5252))
                Button(onClick = { showMainte = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Text("MAINTENANCE UNLOCK")
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
    fun DiagnosticsRow() {
        var showLogs by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = { showLogs = true }) { Text("SYSTEM LOGS", color = Color.Gray, fontSize = 12.sp) }
        }
        if (showLogs) {
            AlertDialog(onDismissRequest = { showLogs = false }, title = { Text("Logs") }, text = {
                SelectionContainer { Text(DebugLogger.getLogs(), fontSize = 10.sp, color = Color.LightGray) }
            }, confirmButton = { TextButton(onClick = { showLogs = false }) { Text("CLOSE") } })
        }
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = "$packageName/${GuardService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return enabled.contains(expected)
    }
}
