@file:OptIn(ExperimentalMaterial3Api::class)

package com.riseup.alarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riseup.alarm.data.DayStatus
import com.riseup.alarm.data.StreakRepository
import com.riseup.alarm.data.StreakStats
import com.riseup.alarm.data.Streaks
import com.riseup.alarm.ui.theme.Muted
import com.riseup.alarm.ui.theme.AccentColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val WEEKS_SHOWN = 5

@Composable
fun StreaksScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { StreakRepository.get(context) }
    val stats by repo.stats.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Streak") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("${stats.current}", style = MaterialTheme.typography.displayLarge, color = AccentColor)
            Text("day streak", color = Muted)
            Spacer(Modifier.height(8.dp))
            Text(milestoneLine(stats), color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Best", "${stats.best}", Modifier.weight(1f))
                Stat("Wake-ups", "${stats.totalWakeUps}", Modifier.weight(1f))
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "LAST $WEEKS_SHOWN WEEKS",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            History(repo.rang, repo.woke)
            Spacer(Modifier.height(16.dp))
            Legend()
            Spacer(Modifier.height(24.dp))
            Text(
                "Days without an alarm don't break your streak.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun milestoneLine(stats: StreakStats): String {
    val next = stats.nextMilestone
    return when {
        stats.totalWakeUps == 0 -> "Complete a wake-up mission to start your streak."
        next == null -> "Legendary. Keep it going."
        else -> "${next - stats.current} more to reach $next days"
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, style = MaterialTheme.typography.displaySmall)
        Text(label, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun History(rang: Set<LocalDate>, woke: Set<LocalDate>) {
    val today = LocalDate.now()
    val firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks((WEEKS_SHOWN - 1).toLong())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(WEEKS_SHOWN) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { d ->
                    val day = firstMonday.plusDays((week * 7 + d).toLong())
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        if (!day.isAfter(today)) {
                            DayDot(Streaks.status(day, rang, woke, today), isToday = day == today)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDot(status: DayStatus, isToday: Boolean, size: Int = 28) {
    val base = Modifier.size(size.dp)
    val ring = if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground) else null
    val m = when (status) {
        DayStatus.WOKE -> base.background(AccentColor, CircleShape)
        DayStatus.MISSED -> base.border(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f), CircleShape)
        DayStatus.PENDING -> base.border(1.5.dp, AccentColor, CircleShape)
        DayStatus.NO_ALARM -> base.padding((size / 2 - 2).dp).background(MaterialTheme.colorScheme.outline, CircleShape)
    }
    Box(if (ring != null && status != DayStatus.NO_ALARM) m.border(ring, CircleShape) else m)
}

@Composable
private fun Legend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        listOf(DayStatus.WOKE to "Woke up", DayStatus.MISSED to "Missed", DayStatus.NO_ALARM to "No alarm").forEach { (s, label) ->
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { DayDot(s, isToday = false, size = 12) }
            Spacer(Modifier.width(6.dp))
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(16.dp))
        }
    }
}
