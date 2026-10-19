package com.riseup.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.riseup.alarm.data.Alarm
import com.riseup.alarm.data.AlarmRepository
import com.riseup.alarm.ui.MainActivity

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE = "snooze"
    const val SNOOZE_MINUTES = 5

    private const val SNOOZE_REQUEST_OFFSET = 100_000

    fun schedule(context: Context, alarm: Alarm) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }
        setAlarmClock(context, alarm.nextTriggerMillis(), firePendingIntent(context, alarm.id, snooze = false))
    }

    fun scheduleSnooze(context: Context, alarmId: Int) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setAlarmClock(context, at, firePendingIntent(context, alarmId, snooze = true))
    }

    fun cancel(context: Context, alarmId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(firePendingIntent(context, alarmId, snooze = false))
        am.cancel(firePendingIntent(context, alarmId, snooze = true))
    }

    fun rescheduleAll(context: Context) {
        AlarmRepository.get(context).alarms.value.forEach { schedule(context, it) }
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setAlarmClock(context: Context, triggerAt: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        try {
            // setAlarmClock is exact, fires in Doze, and shows the alarm icon in the status bar.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), operation)
        } catch (e: SecurityException) {
            // Exact-alarm permission revoked by the user: best effort, may be a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    private fun firePendingIntent(context: Context, alarmId: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SNOOZE, snooze)
        val requestCode = if (snooze) alarmId + SNOOZE_REQUEST_OFFSET else alarmId
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
