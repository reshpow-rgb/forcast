package com.riseup.alarm.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.ads.AdSize
import com.riseup.alarm.ads.BannerAd
import com.riseup.alarm.data.Animal
import com.riseup.alarm.data.StreakStats
import com.riseup.alarm.data.Streaks
import com.riseup.alarm.mission.Challenge
import com.riseup.alarm.mission.ChallengeGenerator
import com.riseup.alarm.mission.Content
import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MathChallenge
import com.riseup.alarm.mission.MissionType
import com.riseup.alarm.ui.animals.SleepyAnimal
import com.riseup.alarm.ui.theme.Muted
import com.riseup.alarm.ui.theme.AccentColor
import kotlinx.coroutines.delay
import java.time.LocalTime

private enum class Phase { RINGING, MISSION, DONE }

/** Seconds without typing before the alarm gets loud again and the mission resets. */
private const val IDLE_SECONDS = 30

@Composable
fun RingScreen(
    preview: Boolean,
    label: String,
    mission: MissionType,
    difficulty: Difficulty,
    rounds: Int,
    animal: Animal,
    canShowAd: () -> Boolean,
    onMissionStarted: () -> Unit,
    onMissionAbandoned: () -> Unit,
    /** Records the wake-up and returns the updated streak. */
    onMissionComplete: () -> StreakStats,
    onSnooze: () -> Unit,
    onFinish: () -> Unit,
) {
    var phase by remember { mutableStateOf(if (preview) Phase.MISSION else Phase.RINGING) }
    var streak by remember { mutableStateOf(StreakStats.EMPTY) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding().imePadding().padding(24.dp)) {
            AnimatedContent(targetState = phase, label = "phase") { p ->
                when (p) {
                    Phase.RINGING -> Ringing(
                        label = label,
                        animal = animal,
                        mission = mission,
                        onStart = { onMissionStarted(); phase = Phase.MISSION },
                        onSnooze = onSnooze,
                    )
                    Phase.MISSION -> Mission(
                        mission = mission,
                        difficulty = difficulty,
                        rounds = rounds,
                        animal = animal,
                        idleTimeout = !preview,
                        onIdle = { onMissionAbandoned(); phase = Phase.RINGING },
                        onComplete = { streak = onMissionComplete(); phase = Phase.DONE },
                    )
                    Phase.DONE -> Done(preview = preview, animal = animal, streak = streak, showAd = remember { canShowAd() }, onFinish = onFinish)
                }
            }
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = LocalTime.now()
        }
    }
    val (time, suffix) = formatTime(now.hour, now.minute, DateFormat.is24HourFormat(context))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(time, style = MaterialTheme.typography.displayLarge)
        if (suffix != null) Text(suffix, color = Muted, modifier = Modifier.padding(start = 8.dp, bottom = 20.dp))
    }
}

/** The buddy animal, easing from [from] to [to] wakefulness over [millis]. */
@Composable
private fun Buddy(animal: Animal, from: Float, to: Float, millis: Int, modifier: Modifier) {
    var wake by remember { mutableFloatStateOf(from) }
    LaunchedEffect(to) { wake = to }
    SleepyAnimal(animal, wake, modifier, zColor = Muted, wakeMillis = millis)
}

@Composable
private fun Ringing(label: String, animal: Animal, mission: MissionType, onStart: () -> Unit, onSnooze: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(0.6f))
        Clock()
        Text(label.ifBlank { "Time to rise" }, color = Muted, style = MaterialTheme.typography.titleMedium)
        if (animal != Animal.NONE) {
            // Still asleep - it starts to stir while the alarm rings, and wakes up properly as you do the mission.
            Buddy(animal, from = 0f, to = 0.45f, millis = 25_000, modifier = Modifier.weight(2f).fillMaxWidth().padding(vertical = 16.dp))
        } else {
            Spacer(Modifier.weight(1.4f))
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("Start mission · ${mission.title}", fontSize = 18.sp)
        }
        TextButton(onClick = onSnooze, modifier = Modifier.padding(top = 8.dp)) {
            Text("Snooze 5 min", color = Muted)
        }
    }
}

@Composable
private fun Mission(
    mission: MissionType,
    difficulty: Difficulty,
    rounds: Int,
    animal: Animal,
    idleTimeout: Boolean,
    onIdle: () -> Unit,
    onComplete: () -> Unit,
) {
    var round by remember { mutableIntStateOf(1) }
    var challenge by remember { mutableStateOf<Challenge>(ChallengeGenerator.generate(mission, difficulty)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idleProgress by remember { mutableFloatStateOf(1f) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(challenge) { focus.requestFocus() }

    if (idleTimeout) {
        LaunchedEffect(lastInputAt) {
            while (true) {
                val elapsed = System.currentTimeMillis() - lastInputAt
                idleProgress = 1f - elapsed / (IDLE_SECONDS * 1000f)
                if (idleProgress <= 0f) {
                    onIdle()
                    break
                }
                delay(100)
            }
        }
    }

    fun advance() {
        if (round >= rounds) {
            onComplete()
        } else {
            round++
            input = ""
            wrong = false
            challenge = ChallengeGenerator.generate(mission, difficulty)
        }
    }

    fun submit() {
        if (challenge.check(input)) {
            advance()
        } else {
            wrong = true
            if (challenge is MathChallenge) input = ""
        }
    }

    val isMath = challenge is MathChallenge
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
        if (idleTimeout) {
            LinearProgressIndicator(progress = { idleProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            if (animal != Animal.NONE) {
                // Each round you finish wakes your buddy up a bit more.
                Buddy(
                    animal,
                    from = 0.45f,
                    to = 0.45f + 0.45f * (round - 1) / rounds,
                    millis = 2_500,
                    modifier = Modifier.size(72.dp),
                )
            }
            Text(
                "Round $round of $rounds",
                color = Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(challenge.instruction, color = Muted, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            challenge.prompt,
            style = if (isMath) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = if (isMath) it.filter(Char::isDigit).take(6) else it
                wrong = false
                lastInputAt = System.currentTimeMillis()
                // Typing missions complete as soon as the text matches - no extra tap needed.
                if (!isMath && challenge.check(input)) advance()
            },
            isError = wrong,
            supportingText = { if (wrong) Text(if (isMath) "Not quite - try again" else "Doesn't match yet") },
            singleLine = isMath,
            textStyle = if (isMath) MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center)
            else MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isMath) KeyboardType.Number else KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp)) {
            Text("Check")
        }
    }
}

@Composable
private fun Done(preview: Boolean, animal: Animal, streak: StreakStats, showAd: Boolean, onFinish: () -> Unit) {
    val quote = remember { Content.quotes.random() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.weight(1f))
        if (animal != Animal.NONE) {
            Buddy(animal, from = 0.85f, to = 1f, millis = 1_500, modifier = Modifier.size(if (showAd) 120.dp else 180.dp))
            Spacer(Modifier.height(8.dp))
        }
        if (preview) {
            Text("Mission complete", style = MaterialTheme.typography.headlineSmall)
            Text("That's what you'll do when the alarm rings.", color = Muted, textAlign = TextAlign.Center)
        } else {
            Text("Good morning", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(quote, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Text("${streak.current}", style = MaterialTheme.typography.displayMedium, color = AccentColor)
            Text(
                when {
                    Streaks.isMilestone(streak.current) -> "${streak.current}-day milestone reached!"
                    else -> "day streak"
                },
                color = if (Streaks.isMilestone(streak.current)) AccentColor else Muted,
            )
        }
        Spacer(Modifier.weight(1f))
        if (showAd) {
            BannerAd(adSize = AdSize.MEDIUM_RECTANGLE)
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(if (preview) "Close" else "Start my day")
        }
    }
}
