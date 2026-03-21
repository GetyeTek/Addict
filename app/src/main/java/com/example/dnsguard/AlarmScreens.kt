package com.guardian.net

import androidx.compose.foundation.* 
import androidx.compose.foundation.layout.* 
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.* 
import androidx.compose.runtime.* 
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class AlarmData(
    val id: String = java.util.UUID.randomUUID().toString(),
    var hour: Int = 6,
    var minute: Int = 0,
    var isAm: Boolean = true,
    var name: String = "Alarm",
    var enabled: Boolean = true,
    var days: Set<Int> = setOf(1, 2, 3, 4, 5) // Mon-Fri
)

@Composable
fun AlarmHubScreen(onAdd: () -> Unit, onEdit: (AlarmData) -> Unit, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alarms = remember { mutableStateListOf<AlarmData>().apply { addAll(AlarmStore.getAlarms(ctx)) } }
    
    val nextAlarmTime = AlarmScheduler.getNextAlarmTime(ctx)
    val countdownText = if (nextAlarmTime == 0L) "No upcoming\nalarms" else {
        val diff = nextAlarmTime - System.currentTimeMillis()
        val hours = (diff / (1000 * 60 * 60)).toInt()
        val mins = ((diff / (1000 * 60)) % 60).toInt()
        if (hours > 0) "Alarm in $hours hours\n$mins minutes" else "Alarm in $mins minutes"
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(60.dp))
        
        Text(
            countdownText, 
            color = Color.White, 
            fontSize = 32.sp, 
            fontWeight = FontWeight.Light, 
            lineHeight = 40.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val dateStr = java.text.SimpleDateFormat("EEE, d MMM, h:mm a", java.util.Locale.US).format(java.util.Date(if (nextAlarmTime == 0L) System.currentTimeMillis() else nextAlarmTime))
        Text(dateStr, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(40.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, null, tint = Color.White) }
            IconButton(onClick = { /* More options */ }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
        }

        // Alarm List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(alarms.size) { index ->
                val alarm = alarms[index]
                AlarmItem(alarm, onClick = { onEdit(alarm) }, onToggle = { alarm.enabled = it })
            }
        }

        // Minimalist Bottom Nav Placeholder
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            Text("Alarm", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
            Text("Settings", color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp).clickable { onBack() })
        }
    }
}

@Composable
fun AlarmItem(alarm: AlarmData, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    var isChecked by remember { mutableStateOf(alarm.enabled) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                val displayHour = if (alarm.hour == 0) 12 else if (alarm.hour > 12) alarm.hour - 12 else alarm.hour
                Text("$displayHour:${alarm.minute.toString().padStart(2, '0')}", color = if(isChecked) Color.White else Color.Gray, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                Text(if (alarm.isAm) "am" else "pm", color = if(isChecked) Color.White else Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
            val activeDays = dayNames.filterIndexed { index, _ -> alarm.days.contains(index + 1) }.joinToString(" ")
            Text("${alarm.name} | $activeDays", color = Color.Gray, fontSize = 12.sp)
        }
        Switch(checked = isChecked, onCheckedChange = { isChecked = it; onToggle(it) })
    }
}

@Composable
fun AlarmEditorScreen(alarm: AlarmData?, onSave: (AlarmData) -> Unit, onCancel: () -> Unit) {
    val workingAlarm = remember { alarm?.copy() ?: AlarmData() }
    var name by remember { mutableStateOf(workingAlarm.name) }
    val selectedDays = remember { mutableStateListOf<Int>().apply { addAll(workingAlarm.days) } }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // The Time Picker Wheel Area
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerticalWheelPicker(range = 1..12, initial = workingAlarm.hour % 12) { workingAlarm.hour = it }
                Text(":", color = Color.White, fontSize = 40.sp, modifier = Modifier.padding(horizontal = 10.dp))
                VerticalWheelPicker(range = 0..59, initial = workingAlarm.minute) { workingAlarm.minute = it }
                Spacer(Modifier.width(20.dp))
                VerticalWheelPicker(range = listOf("am", "pm"), initial = if(workingAlarm.isAm) 0 else 1) { workingAlarm.isAm = it == 0 }
            }
            // Selection indicators
            Divider(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).align(Alignment.Center).offset(y = (-25).dp), color = Color.DarkGray)
            Divider(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).align(Alignment.Center).offset(y = 25.dp), color = Color.DarkGray)
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Day Selector
        Text("Tomorrow-Sun, 22 Mar", color = Color.White, fontSize = 14.sp)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val days = listOf("M", "T", "W", "T", "F", "S", "S")
            days.forEachIndexed { index, day ->
                val dayNum = index + 1
                val isSelected = selectedDays.contains(dayNum)
                Surface(
                    modifier = Modifier.size(36.dp).clickable { if(isSelected) selectedDays.remove(dayNum) else selectedDays.add(dayNum) },
                    shape = CircleShape, 
                    color = if(isSelected) Color.Transparent else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(day, color = if(isSelected) Color(0xFFFB7185) else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Alarm name", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.DarkGray, focusedTextColor = Color.White)
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Color.White, fontSize = 18.sp) }
            TextButton(onClick = { workingAlarm.name = name; workingAlarm.days = selectedDays.toSet(); onSave(workingAlarm) }) { 
                Text("Save", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) 
            }
        }
    }
}

@Composable
fun VerticalWheelPicker(range: Any, initial: Int, onSelect: (Int) -> Unit) {
    val list = if (range is IntRange) range.toList() else range as List<*>
    val state = rememberLazyListState(initialIndex = initial)
    
    // Crude snapping logic for now
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            val index = state.firstVisibleItemIndex
            onSelect(if(list[index] is Int) list[index] as Int else index)
        }
    }

    Box(modifier = Modifier.height(150.dp).width(60.dp)) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            items(list.size) { index ->
                Text(
                    text = list[index].toString().padStart(2, '0'),
                    color = Color.White,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
            }
        }
    }
}
