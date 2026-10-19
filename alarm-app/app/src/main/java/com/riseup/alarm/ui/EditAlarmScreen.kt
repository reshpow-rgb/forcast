@file:OptIn(ExperimentalMaterial3Api::class)

package com.riseup.alarm.ui

import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riseup.alarm.data.Alarm
import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MissionType
import com.riseup.alarm.ui.theme.Muted
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun EditAlarmScreen(
    initial: Alarm,
    isNew: Boolean,
    isPremium: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    onUpsell: () -> Unit,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    var days by remember { mutableStateOf(initial.days) }
    var mission by remember { mutableStateOf(initial.mission) }
    var difficulty by remember { mutableStateOf(initial.difficulty) }
    var rounds by remember { mutableIntStateOf(initial.rounds) }
    var label by remember { mutableStateOf(initial.label) }

    fun current() = initial.copy(
        hour = timeState.hour,
        minute = timeState.minute,
        days = days,
        mission = mission,
        difficulty = difficulty,
        rounds = rounds,
        label = label.trim(),
        enabled = true,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (!isNew) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Button(
                onClick = { onSave(current()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            TimePicker(
                state = timeState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                    timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            SectionTitle("Repeat")
            DayPicker(days) { days = it }

            SectionTitle("Wake-up mission")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MissionType.entries.forEach { type ->
                    OptionCard(
                        title = type.title,
                        subtitle = type.blurb,
                        selected = mission == type,
                        onClick = { mission = type },
                    )
                }
            }

            SectionTitle("Difficulty")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Difficulty.entries.forEachIndexed { i, d ->
                    val locked = d.premium && !isPremium
                    SegmentedButton(
                        selected = difficulty == d,
                        onClick = { if (locked) onUpsell() else difficulty = d },
                        shape = SegmentedButtonDefaults.itemShape(i, Difficulty.entries.size),
                        icon = {
                            if (locked) Icon(Icons.Default.Lock, null, Modifier.padding(end = 2.dp))
                            else SegmentedButtonDefaults.Icon(difficulty == d)
                        },
                    ) { Text(d.title) }
                }
            }

            SectionTitle("Rounds")
            val maxRounds = Alarm.PREMIUM_MAX_ROUNDS
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..maxRounds).forEach { n ->
                    val locked = n > Alarm.FREE_MAX_ROUNDS && !isPremium
                    SegmentedButton(
                        selected = rounds == n,
                        onClick = { if (locked) onUpsell() else rounds = n },
                        shape = SegmentedButtonDefaults.itemShape(n - 1, maxRounds),
                        icon = { if (locked) Icon(Icons.Default.Lock, null) },
                    ) { if (!locked) Text("$n") }
                }
            }

            SectionTitle("Label")
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("Optional") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(context, RingActivity::class.java)
                            .putExtra(RingActivity.EXTRA_PREVIEW, true)
                            .putExtra(RingActivity.EXTRA_MISSION, mission.name)
                            .putExtra(RingActivity.EXTRA_DIFFICULTY, difficulty.name)
                            .putExtra(RingActivity.EXTRA_ROUNDS, rounds),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try this mission") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = Muted,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}

@Composable
private fun DayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        (1..7).forEach { day ->
            val selected = day in days
            Surface(
                onClick = { onChange(if (selected) days - day else days + day) },
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(2.dp),
            ) {
                Text(
                    DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
