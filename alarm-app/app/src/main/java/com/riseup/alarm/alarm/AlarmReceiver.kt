package com.riseup.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.riseup.alarm.data.AlarmRepository
import com.riseup.alarm.data.StreakRepository

/** Fired by AlarmManager at alarm time. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)
        val repo = AlarmRepository.get(context)
        val alarm = repo.get(alarmId) ?: return

        if (!snooze) {
            StreakRepository.get(context).recordRing()
            if (alarm.repeats) {
                AlarmScheduler.schedule(context, alarm)
            } else {
                repo.upsert(alarm.copy(enabled = false))
            }
        }

        ContextCompat.startForegroundService(
            context,
            Intent(context, AlarmService::class.java).putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId),
        )
    }
}
