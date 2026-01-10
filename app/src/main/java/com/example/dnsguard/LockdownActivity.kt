package com.example.dnsguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // INTRUSION: Remove system bars
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            // CyberUI Reused Theme
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050505)), // Void Black
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "CONNECTION UNSECURE",
                        color = Color(0xFFEF4565), // Neon Red
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Private DNS must be set to 'Strict'.",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { 
                            val i = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(i)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4565))
                    ) {
                        Text("FIX NOW", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // LIVE MONITOR LOOP
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                scope.launch {
                    while(true) {
                        if (DnsManager.isSecure(applicationContext)) {
                            finishAffinity() // Release lock
                        }
                        delay(1000)
                    }
                }
            }
        }
    }

    // Trap user
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { }
}