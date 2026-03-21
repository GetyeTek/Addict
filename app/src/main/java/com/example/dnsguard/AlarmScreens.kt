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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
@Composable
fun AlarmHubScreen(onAdd: () -> Unit, onEdit: (AlarmData) -> Unit, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val alarms = remember { mutableStateListOf<AlarmData>().apply { addAll(AlarmStore.getAlarms(ctx)) } }
    
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp)) {
    LaunchedEffect(Unit) {
        while(true) {
            nextAlarmTime = AlarmScheduler.getNextAlarmTime(ctx)
            kotlinx.coroutines.delay(60000)
        }
    }

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
            if (!isSelectionMode) {
                IconButton(onClick = onAdd) { Icon(Icons.Default.Add, null, tint = Color.White) }
            }
            Box {
                IconButton(onClick = { if (isSelectionMode) { isSelectionMode = false; selectedIds.clear() } else showMenu = true }) {
                    Icon(if (isSelectionMode) Icons.Default.Close else Icons.Default.MoreVert, null, tint = Color.White)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                    DropdownMenuItem(
                        text = { Text("Edit", color = Color.White) },
                        onClick = { isSelectionMode = true; showMenu = false }
                    )
                }
            }
        }

        // Alarm List
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(alarms.size) { index ->
                val alarm = alarms[index]
                val isSelected = selectedIds.contains(alarm.id)
                
                AlarmItem(
                    alarm = alarm,
                    isSelectionMode = isSelectionMode,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelectionMode) {
                            if (isSelected) selectedIds.remove(alarm.id) else selectedIds.add(alarm.id)
                        } else {
                            onEdit(alarm)
                        }
                    },
                    onToggle = { isEnabled ->
                        alarm.enabled = isEnabled
                        AlarmStore.saveAlarms(ctx, alarms.toList())
                        if (isEnabled) {
                             val diff = AlarmScheduler.getTimeToAlarm(alarm)
                             val mins = (diff / (1000 * 60)).toInt()
                             val hours = mins / 60
                             val m = mins % 60
                             val msg = if (hours > 0) "Alarm set for $hours h $m m" else "Alarm set for $m m"
                             android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Minimalist Bottom Nav Placeholder
        if (!isSelectionMode) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
                Text("Alarm", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                Text("Settings", color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp).clickable { onBack() })
            }
        }
    }

    // FLOATING SELECTION BAR
    if (isSelectionMode && selectedIds.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 20.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f).height(70.dp),
                shape = RoundedCornerShape(35.dp),
                color = Color(0xFF1E1E1E),
                tonalElevation = 8.dp
            ) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                         alarms.forEach { if(selectedIds.contains(it.id)) it.enabled = false }
                         AlarmStore.saveAlarms(ctx, alarms.toList())
                         isSelectionMode = false
                         selectedIds.clear()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Block, null, tint = Color.White)
                            Text("Turn off", color = Color.White, fontSize = 10.sp)
                        }
                    }
                    TextButton(onClick = {
                         alarms.removeIf { selectedIds.contains(it.id) }
                         AlarmStore.saveAlarms(ctx, alarms.toList())
                         isSelectionMode = false
                         selectedIds.clear()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                            Text("Delete", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
  }
}

@Composable
fun AlarmItem(alarm: AlarmData, isSelectionMode: Boolean = false, isSelected: Boolean = false, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val isEnabled = alarm.enabled
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected, 
                onCheckedChange = null, // Handled by row click
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFB7185))
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                val displayHour = if (alarm.hour == 0) 12 else if (alarm.hour > 12) alarm.hour - 12 else alarm.hour
                Text("$displayHour:${alarm.minute.toString().padStart(2, '0')}", color = if(isEnabled) Color.White else Color.Gray, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                Text(if (alarm.isAm) "am" else "pm", color = if(isEnabled) Color.White else Color.Gray, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
            val activeDays = when {
                alarm.days.size == 7 -> "Every day"
                alarm.days.isEmpty() -> "Once"
                else -> dayNames.filterIndexed { index, _ -> alarm.days.contains(index + 1) }.joinToString(" ")
            }
            Text("${alarm.name} | $activeDays", color = Color.Gray, fontSize = 12.sp)
        }

        if (!isSelectionMode) {
            Switch(checked = isEnabled, onCheckedChange = { onToggle(it) })
        }
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
                VerticalWheelPicker(range = 1..12, initial = if(workingAlarm.hour == 0 || workingAlarm.hour == 12) 11 else (workingAlarm.hour % 12) - 1) { workingAlarm.hour = if(workingAlarm.isAm) (if(it == 12) 0 else it) else (if(it == 12) 12 else it + 12) }
                Text(":", color = Color.White, fontSize = 40.sp, modifier = Modifier.padding(horizontal = 10.dp))
                VerticalWheelPicker(range = 0..59, initial = workingAlarm.minute) { workingAlarm.minute = it }
                Spacer(Modifier.width(20.dp))
                VerticalWheelPicker(range = listOf("am", "pm"), initial = if(workingAlarm.isAm) 0 else 1) { workingAlarm.isAm = it == 0 }
            }
            // Selection indicators
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).align(Alignment.Center).offset(y = (-25).dp), color = Color.DarkGray)
            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).align(Alignment.Center).offset(y = 25.dp), color = Color.DarkGray)
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
                    color = if(isSelected) Color(0xFFFB7185) else Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(day, color = if(isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
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
    val itemHeight = 50.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf("") }

    // Snapping logic
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress && !isEditing) {
            val layoutInfo = state.layoutInfo
            val center = layoutInfo.viewportEndOffset / 2
            val closestItem = layoutInfo.visibleItemsInfo.minByOrNull { 
                Math.abs((it.offset + it.size / 2) - center) 
            }
            closestItem?.let {
                state.animateScrollToItem(it.index)
                onSelect(if(list[it.index] is Int) list[it.index] as Int else it.index)
            }
        }
    }

    Box(modifier = Modifier.height(150.dp).width(75.dp).clickable { 
        if (list[0] is Int) {
            isEditing = true
            editValue = ""
        } else {
            // AM/PM Toggle
            val nextIndex = (state.firstVisibleItemIndex + 1) % list.size
            scope.launch { state.animateScrollToItem(nextIndex) }
            onSelect(nextIndex)
        }
    }, contentAlignment = Alignment.Center) {
        if (isEditing) {
            BasicTextField(
                value = editValue,
                onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) editValue = it },
                modifier = Modifier.focusRequester(focusRequester).width(60.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val num = editValue.toIntOrNull()
                    if (num != null) {
                        val targetIndex = list.indexOfFirst { it == num }.coerceAtLeast(0)
                        scope.launch { state.animateScrollToItem(targetIndex) }
                        onSelect(num)
                    }
                    isEditing = false
                    focusManager.clearFocus()
                })
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 50.dp), 
                userScrollEnabled = !isEditing
            ) {
                items(list.size) { index ->
                    Box(modifier = Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = list[index].toString().let { if(it.all { c -> c.isDigit() }) it.padStart(2, '0') else it },
                            color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
