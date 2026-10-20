package com.riseup.alarm.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.riseup.alarm.R
import com.riseup.alarm.RiseUpApp
import com.riseup.alarm.data.AlarmRepository
import com.riseup.alarm.ui.RingActivity

/**
 * Plays the alarm sound and vibration until the mission is completed (or snoozed).
 * Runs in the foreground so the system won't kill it, and posts a full-screen
 * notification that opens [RingActivity] over the lock screen.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUIET -> {
                player?.setVolume(QUIET_VOLUME, QUIET_VOLUME)
                vibrator?.cancel()
                if (ringingAlarmId == null) stopSelf()
                return START_NOT_STICKY
            }
            ACTION_LOUD -> {
                player?.setVolume(1f, 1f)
                startVibrating()
                if (ringingAlarmId == null) stopSelf()
                return START_NOT_STICKY
            }
        }

        val alarmId = intent?.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1) ?: -1
        val notification = buildNotification(alarmId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        ringingAlarmId = alarmId
        startRinging(alarmId)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun buildNotification(alarmId: Int): Notification {
        val alarm = AlarmRepository.get(this).get(alarmId)
        val ringIntent = Intent(this, RingActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pending = PendingIntent.getActivity(
            this, alarmId, ringIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, RiseUpApp.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm?.label?.ifBlank { null } ?: "Wake up!")
            .setContentText("Complete your mission to turn off the alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .build()
    }

    private fun startRinging(alarmId: Int) {
        if (player != null) return

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RiseUp:ringing")
            .apply { acquire(MAX_RING_MS) }

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val sound = AlarmRepository.get(this).get(alarmId)?.sound ?: Sounds.DEFAULT
        // A picked file can disappear (deleted, SD card removed) - fall back to the phone's alarm sound.
        val candidates = if (sound == Sounds.SILENT) emptyList()
        else listOfNotNull(Sounds.uriFor(this, sound)) + Sounds.fallbacks(this)
        for (uri in candidates) {
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
            if (player != null) break
        }
        startVibrating()
    }

    private fun startVibrating() {
        val v = vibrator ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
    }

    private fun stopRinging() {
        ringingAlarmId = null
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "com.riseup.alarm.STOP"
        const val ACTION_QUIET = "com.riseup.alarm.QUIET"
        const val ACTION_LOUD = "com.riseup.alarm.LOUD"

        private const val NOTIFICATION_ID = 42
        private const val QUIET_VOLUME = 0.08f
        private const val MAX_RING_MS = 30 * 60 * 1000L

        /** Id of the alarm currently ringing, or null. Lets the main screen send the user back to the mission. */
        @Volatile
        var ringingAlarmId: Int? = null
            private set

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
