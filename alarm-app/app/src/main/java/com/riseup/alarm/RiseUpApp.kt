package com.riseup.alarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.riseup.alarm.billing.BillingManager

class RiseUpApp : Application() {

    lateinit var billing: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        billing = BillingManager(this).also { it.start() }

        val channel = NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Ringing alarms"
            setSound(null, null) // AlarmService plays the sound itself
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ALARMS = "alarms"
    }
}
