package com.riseup.alarm.ui

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.riseup.alarm.RiseUpApp
import com.riseup.alarm.ads.Ads
import com.riseup.alarm.alarm.AlarmScheduler
import com.riseup.alarm.alarm.AlarmService
import com.riseup.alarm.data.AlarmRepository
import com.riseup.alarm.mission.Difficulty
import com.riseup.alarm.mission.MissionType
import com.riseup.alarm.ui.theme.RiseUpTheme

/**
 * Full-screen ringing screen, shown over the lock screen. The alarm only stops once
 * the mission is completed (or the user snoozes). Also used to preview a mission.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val preview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val alarm = AlarmRepository.get(this).get(alarmId)

        val mission = intent.getStringExtra(EXTRA_MISSION)?.let(MissionType::valueOf) ?: alarm?.mission ?: MissionType.MATH
        val difficulty = intent.getStringExtra(EXTRA_DIFFICULTY)?.let(Difficulty::valueOf) ?: alarm?.difficulty ?: Difficulty.NORMAL
        val rounds = if (preview) intent.getIntExtra(EXTRA_ROUNDS, 1) else alarm?.rounds ?: 1

        if (!preview) {
            // Back does nothing while ringing - the mission is the only way out.
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })
            Ads.initIfAllowed(this)
        }

        val isPremium = (application as RiseUpApp).billing.isPremium.value
        setContent {
            RiseUpTheme {
                RingScreen(
                    preview = preview,
                    label = alarm?.label.orEmpty(),
                    mission = mission,
                    difficulty = difficulty,
                    rounds = rounds,
                    // AdMob policy: no ads on the lock screen, and never for Premium users.
                    canShowAd = { !isPremium && !preview && !isLocked() },
                    onMissionStarted = { if (!preview) AlarmService.send(this, AlarmService.ACTION_QUIET) },
                    onMissionAbandoned = { if (!preview) AlarmService.send(this, AlarmService.ACTION_LOUD) },
                    onMissionComplete = { if (!preview) AlarmService.send(this, AlarmService.ACTION_STOP) },
                    onSnooze = {
                        AlarmService.send(this, AlarmService.ACTION_STOP)
                        AlarmScheduler.scheduleSnooze(this, alarmId)
                        finish()
                    },
                    onFinish = { finish() },
                )
            }
        }
    }

    private fun isLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_MISSION = "mission"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_ROUNDS = "rounds"
    }
}
