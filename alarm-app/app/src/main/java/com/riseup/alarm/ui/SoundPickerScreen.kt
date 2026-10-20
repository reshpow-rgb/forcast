@file:OptIn(ExperimentalMaterial3Api::class)

package com.riseup.alarm.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.riseup.alarm.alarm.Sounds
import com.riseup.alarm.ui.theme.AccentColor
import com.riseup.alarm.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picks an alarm sound. Tapping a sound plays a preview; back keeps the selection. */
@Composable
fun SoundPickerScreen(
    selected: String,
    selectedTitle: String,
    onSelect: (sound: String, title: String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val preview = remember { PreviewPlayer(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    var alarmTones by remember { mutableStateOf(emptyList<Sounds.Tone>()) }
    var ringtones by remember { mutableStateOf(emptyList<Sounds.Tone>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            alarmTones = Sounds.systemTones(context, RingtoneManager.TYPE_ALARM)
            ringtones = Sounds.systemTones(context, RingtoneManager.TYPE_RINGTONE)
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access after a reboot so the alarm can still play it.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSelect(uri.toString(), displayName(context, uri))
            preview.play(uri)
        }
    }

    fun choose(sound: String, title: String) {
        onSelect(sound, title)
        val uri = Sounds.uriFor(context, sound) ?: if (sound == Sounds.DEFAULT) Sounds.fallbacks(context).firstOrNull() else null
        if (uri != null) preview.play(uri) else preview.stop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SoundRow(Sounds.DEFAULT_TITLE, selected == Sounds.DEFAULT) { choose(Sounds.DEFAULT, Sounds.DEFAULT_TITLE) } }
            item { SoundRow("Vibrate only", selected == Sounds.SILENT) { choose(Sounds.SILENT, "Vibrate only") } }
            item {
                val isFile = selected.startsWith("content://") &&
                    alarmTones.none { it.key == selected } && ringtones.none { it.key == selected }
                SoundRow(
                    if (isFile) "$selectedTitle  \u00B7  change" else "Choose from your music…",
                    isFile,
                ) { pickFile.launch(arrayOf("audio/*")) }
            }

            section("RiseUp sounds", Sounds.builtins.map { Sounds.Tone(it.key, it.title) }, selected, ::choose)
            section("Phone alarm sounds", alarmTones, selected, ::choose)
            section("Ringtones", ringtones, selected, ::choose)
        }
    }
}

private fun LazyListScope.section(
    title: String,
    tones: List<Sounds.Tone>,
    selected: String,
    onChoose: (String, String) -> Unit,
) {
    if (tones.isEmpty()) return
    item {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 6.dp),
        )
    }
    items(tones, key = { "$title/${it.key}" }) { tone ->
        SoundRow(tone.title, tone.key == selected) { onChoose(tone.key, tone.title) }
    }
}

@Composable
private fun SoundRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(title, color = if (selected) AccentColor else MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = AccentColor)
    }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.') ?: "Your music"

/** Plays a few seconds of a sound at alarm volume so people know what they're picking. */
private class PreviewPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(uri: Uri) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, uri)
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.run { runCatching { stop() }; release() }
        player = null
    }
}
