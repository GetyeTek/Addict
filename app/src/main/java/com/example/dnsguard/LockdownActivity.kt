package com.guardian.net

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
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

    private var blockTypeState = mutableStateOf("UNKNOWN")

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 11+ Lockscreen support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        
        super.onCreate(savedInstanceState)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    LockdownUI(blockTypeState.value)
                }
            }
        }
    }

    @Composable
    fun LockdownUI(type: String) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("GUARDIAN LOCK", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Type: $type", color = Color.White, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Security policy requires your attention.", color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val home = Intent(Intent.ACTION_MAIN)
                    home.addCategory(Intent.CATEGORY_HOME)
                    home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(home)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("EXIT TO HOME", color = Color.White)
            }
        }
        
        // Background Monitor
        LaunchedEffect(Unit) {
            while(true) {
                val isFixed = DnsManager.isSecure(applicationContext) || LockManager.isUnlocked(applicationContext)
                if (type == "DNS" && isFixed) finishAffinity()
                delay(2000)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }
}