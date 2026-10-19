package com.riseup.alarm.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.riseup.alarm.ads.Ads
import com.riseup.alarm.ads.BannerAd
import com.riseup.alarm.alarm.AlarmScheduler
import com.riseup.alarm.billing.BillingManager
import com.riseup.alarm.data.Alarm
import com.riseup.alarm.data.AlarmRepository
import com.riseup.alarm.ui.theme.ClockStyle
import com.riseup.alarm.ui.theme.Muted
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(billing: BillingManager, refreshTick: Int) {
    val context = LocalContext.current
    val repo = remember { AlarmRepository.get(context) }
    val alarms by repo.alarms.collectAsStateWithLifecycle()
    val isPremium by billing.isPremium.collectAsStateWithLifecycle()
    val price by billing.price.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Alarm?>(null) }
    var showPremium by remember { mutableStateOf(false) }

    val save: (Alarm) -> Unit = { alarm ->
        repo.upsert(alarm)
        AlarmScheduler.schedule(context, alarm)
        if (alarm.enabled) toastTimeUntil(context, alarm.nextTriggerMillis())
    }

    val current = editing
    if (current != null) {
        val alarm = current
        BackHandler { editing = null }
        EditAlarmScreen(
            initial = alarm,
            isNew = repo.get(alarm.id) == null,
            isPremium = isPremium,
            onSave = { save(it); editing = null },
            onDelete = {
                AlarmScheduler.cancel(context, alarm.id)
                repo.delete(alarm.id)
                editing = null
            },
            onClose = { editing = null },
            onUpsell = { showPremium = true },
        )
    } else Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!isPremium && alarms.size >= Alarm.FREE_MAX_ALARMS) {
                        showPremium = true
                    } else {
                        editing = Alarm(id = repo.newId(), hour = 7, minute = 0, days = setOf(1, 2, 3, 4, 5))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
            ) { Icon(Icons.Default.Add, contentDescription = "Add alarm") }
        },
        bottomBar = {
            if (!isPremium) {
                // key on refreshTick so the banner appears once consent arrives
                key(refreshTick) {
                    BannerAd(Modifier.navigationBarsPadding())
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 24.dp, end = 24.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { Header(alarms, isPremium, onPremium = { showPremium = true }) }
            item { PermissionWarnings(refreshTick) }
            if (alarms.isEmpty()) {
                item {
                    Text(
                        "No alarms yet.\nTap + and pick a mission that gets you out of bed.",
                        color = Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                    )
                }
            }
            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    onClick = { editing = alarm },
                    onToggle = { save(alarm.copy(enabled = it)) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (Ads.privacyOptionsRequired(context)) {
                item {
                    TextButton(onClick = { (context as? Activity)?.let(Ads::showPrivacyOptions) }) {
                        Text("Privacy settings", color = Muted)
                    }
                }
            }
        }
    }

    if (showPremium) {
        PremiumDialog(
            price = price,
            isPremium = isPremium,
            onBuy = {
                val ok = (context as? Activity)?.let { billing.buy(it) } ?: false
                if (!ok) Toast.makeText(context, "Store not available right now - try again shortly", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPremium = false },
        )
    }
}

@Composable
private fun Header(alarms: List<Alarm>, isPremium: Boolean, onPremium: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val next = alarms.filter { it.enabled }.minOfOrNull { it.nextTriggerMillis() }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("RiseUp", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (!isPremium) {
            TextButton(onClick = onPremium) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Premium")
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Text(
        if (next == null) "No alarm set" else "Next alarm in",
        color = Muted,
        style = MaterialTheme.typography.bodyLarge,
    )
    if (next != null) {
        Text(formatDuration(next - now), style = MaterialTheme.typography.displayMedium)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AlarmRow(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val alpha = if (alarm.enabled) 1f else 0.4f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val (time, suffix) = formatTime(alarm.hour, alarm.minute, DateFormat.is24HourFormat(context))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, style = ClockStyle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha))
                if (suffix != null) {
                    Text(
                        suffix,
                        color = Muted.copy(alpha = alpha),
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )
                }
            }
            Text(
                listOfNotNull(alarm.label.ifBlank { null }, alarm.daysSummary(), alarm.mission.title)
                    .joinToString("  ·  "),
                color = Muted.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/** Alarms silently fail without these, so surface them clearly. */
@Composable
private fun PermissionWarnings(refreshTick: Int) {
    val context = LocalContext.current
    // Re-read on every resume (refreshTick changes) since the user may have just granted them.
    val exactOk = remember(refreshTick) { AlarmScheduler.canScheduleExact(context) }
    val nm = remember { context.getSystemService(NotificationManager::class.java) }
    val notificationsOk = remember(refreshTick) { nm.areNotificationsEnabled() }
    val fullScreenOk = remember(refreshTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || nm.canUseFullScreenIntent()
    }
    val pkg = Uri.parse("package:${context.packageName}")

    if (!notificationsOk) {
        Warning("Notifications are off, so alarms can't ring.") {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
    if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Warning("Allow \"Alarms & reminders\" so alarms ring on time.") {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
        }
    }
    if (!fullScreenOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Warning("Allow full-screen alarms so the mission shows on your lock screen.") {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
        }
    }
}

@Composable
private fun Warning(text: String, onFix: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onFix) { Text("Fix") }
        }
    }
}

@Composable
fun PremiumDialog(price: String?, isPremium: Boolean, onBuy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPremium) "You're Premium" else "RiseUp Premium") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "No ads",
                    "Unlimited alarms (free: ${Alarm.FREE_MAX_ALARMS})",
                    "Hard missions",
                    "Up to ${Alarm.PREMIUM_MAX_ROUNDS} rounds per alarm",
                ).forEach { Row { Text("✓", color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(it) } }
                if (!isPremium) Text("One-time purchase. Yours forever.", color = Muted)
            }
        },
        confirmButton = {
            if (!isPremium) {
                Button(onClick = onBuy) { Text(price?.let { "Unlock for $it" } ?: "Unlock") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isPremium) "Close" else "Not now") } },
    )
}

private fun toastTimeUntil(context: Context, triggerAt: Long) {
    val text = "Alarm set for ${formatDuration(triggerAt - System.currentTimeMillis())} from now"
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
