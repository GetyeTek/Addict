package com.guardian.net

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

class AlarmRingerActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) 
        
        // 1. Play Default Alarm Sound
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, alarmUri)
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            isLooping = true
            prepare()
            start()
        }

        // 2. Start Vibration
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator?.vibrate(longArrayOf(0, 500, 500), 0)

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val now = Calendar.getInstance()
                    Text(
                        String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)),
                        color = Color.White, fontSize = 80.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(100.dp))
                    
                    Button(
                        onClick = { snoozeAndPants() },
                        modifier = Modifier.size(200.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("SNOOZE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Text(
                        "3 MINS TO WEAR PANTS", 
                        color = Color.Gray, 
                        modifier = Modifier.padding(top = 24.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    private fun snoozeAndPants() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()

        // Schedule Exorcism in 3 minutes
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(this, AlarmReceiver::class.java).apply {
            action = "ACTION_EXORCISE"
        }
        val pending = android.app.PendingIntent.getBroadcast(
            this, 1, intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (3 * 60 * 1000L)
        am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pending)
        
        DebugLogger.log("ALARM", "Snoozed. Exorcism in 3 mins.")
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        vibrator?.cancel()
    }
}