package com.guardian.net

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class LockdownActivity : ComponentActivity() {
    private var blockTypeState = mutableStateOf("SYSTEM")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val type = blockTypeState.value
                val uiConfig = getUiConfig(type)
                
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
                        colors = listOf(uiConfig.color.copy(alpha = 0.15f), Color.Black),
                        radius = 1800f
                    )))
                    
                    LockdownContent(uiConfig, type)
                }
            }
        }
    }

    @Composable
    fun LockdownContent(config: UiConfig, type: String) {
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = ""
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = config.color.copy(alpha = alpha)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                config.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
            
            Box(modifier = Modifier.padding(vertical = 12.dp).height(2.dp).fillMaxWidth(0.2f).background(config.color))

            Text(
                type.replace("_", " "),
                color = config.color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    config.description,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            val hasEmergencyBypass = listOf("NIGHT_LOCK", "BREAK_TIME", "PENALTY", "USER_LOCKOUT").contains(type)

            if (hasEmergencyBypass) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val i = Intent(Intent.ACTION_DIAL).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                            startActivity(i)
                            moveTaskToBack(true)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = config.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PHONE", color = Color.White)
                    }
                    
                    Button(
                        onClick = {
                            val i = packageManager.getLaunchIntentForPackage("com.sec.android.app.clockpackage") 
                                 ?: packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                                 ?: packageManager.getLaunchIntentForPackage("com.android.deskclock")
                            
                            if (i != null) {
                                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(i)
                                moveTaskToBack(true)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = config.color)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CLOCK", color = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = { 
                        val home = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(home) 
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = config.color),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ACKNOWLEDGE", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        LaunchedEffect(Unit) {
            while(true) {
                val isFixed = DnsManager.isSecure(applicationContext) || LockManager.isUnlocked(applicationContext)
                val isViolation = type.contains("VIOLATION") || type.contains("SUSPENDED") || type == "PENALTY"
                
                if (!isViolation && isFixed) finishAffinity()
                delay(2000)
            }
        }
    }

    private fun getUiConfig(type: String): UiConfig {
        return when (type) {
            "BROWSER_VIOLATION" -> UiConfig(Icons.Default.Block, Color(0xFFFF3B30), "ACCESS DENIED", "Web access restricted due to security strike. Protection is cooling down.")
            "TELEGRAM_SUSPENDED" -> UiConfig(Icons.Default.Lock, Color(0xFFFF9500), "APP SUSPENDED", "Suspicious activity detected within this application. Access temporarily revoked.")
            "NIGHT_LOCK" -> UiConfig(Icons.Default.NightsStay, Color(0xFF5856D6), "REST MODE", "Guardian has engaged night protocol. Your rest is a priority.")
            "BREAK_TIME" -> UiConfig(Icons.Default.Timer, Color(0xFF34C759), "MINDFUL BREAK", "Take a moment to step away. The screen will reactivate shortly.")
            "USER_LOCKOUT" -> UiConfig(Icons.Default.Timer, Color(0xFF007AFF), "FOCUS ACTIVE", "Deep work mode is currently engaged. Stay concentrated.")
            "PENALTY" -> UiConfig(Icons.Default.Warning, Color(0xFFFF2D55), "SYSTEM RECOVERY", "Permission tampering detected. System is locked for your protection.")
            else -> UiConfig(Icons.Default.Shield, Color(0xFFEF4565), "SYSTEM INSECURE", "Private DNS configuration is required to maintain the secure perimeter.")
        }
    }

    data class UiConfig(val icon: ImageVector, val color: Color, val title: String, val description: String)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        blockTypeState.value = intent.getStringExtra("BLOCK_TYPE") ?: "SYSTEM"
    }

    override fun onBackPressed() { }
}