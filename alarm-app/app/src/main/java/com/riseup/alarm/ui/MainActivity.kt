package com.riseup.alarm.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.riseup.alarm.RiseUpApp
import com.riseup.alarm.ads.Ads
import com.riseup.alarm.alarm.AlarmScheduler
import com.riseup.alarm.alarm.AlarmService
import com.riseup.alarm.ui.theme.RiseUpTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }

    /** Bumped on resume / consent so permission warnings and ads re-evaluate. */
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Ads.gatherConsent(this) { refreshTick++ }
        AlarmScheduler.rescheduleAll(this)

        val billing = (application as RiseUpApp).billing
        setContent {
            RiseUpTheme {
                HomeScreen(billing = billing, refreshTick = refreshTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
        (application as RiseUpApp).billing.refreshPurchases()
        // No escaping the mission by opening the app from the launcher.
        AlarmService.ringingAlarmId?.let { id ->
            startActivity(
                Intent(this, RingActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
