package com.guardian.net

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockdownActivity : ComponentActivity() {
    private var blockTypeState = mutableStateOf("SYSTEM")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Removed TurnScreenOn and ShowWhenLocked to prevent sleep disturbance
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
                    // Background Gradient Glow
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1010), Color.Black),
                        radius = 1500f
                    )))
                    
                    LockdownContent(blockTypeState.value)
                }
            }
        }
    }

    @Composable
    fun LockdownContent(type: String) {
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = ""
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color.Red.copy(alpha = alpha)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "PROTOCOL ENFORCED",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            
            Box(modifier = Modifier.padding(vertical = 12.dp).height(1.dp).fillMaxWidth(0.3f).background(Color.Red))

            Text(
                type.replace("_", " "),
                color = Color.Red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(40.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your digital well-being is being protected. Access to this sector is currently restricted per security policy.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = { 
                    val home = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(home) 
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("RETURN TO SAFE ZONE", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        LaunchedEffect(Unit) {
            while(true) {
                if (LockManager.isUnlocked(applicationContext)) finishAffinity()
                delay(2000)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"
    }

    override fun onBackPressed() { }
}