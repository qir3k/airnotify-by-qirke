package com.qirke.airnotify.chime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger
import timber.log.Timber
import java.util.Calendar

class HourlyChimeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("HourlyChimeReceiver triggered")

        // Immediately schedule next chime
        HourlyChimeScheduler.scheduleNextChime(context)

        val prefs = PreferencesManager.getInstance(context)
        val chimeConfig = prefs.getHourlyChimeConfig()
        if (!chimeConfig.isEnabled) return

        // Check global inactive / sleep window
        val quietConfig = prefs.getQuietHoursConfig()
        if (quietConfig.isEnabled && quietConfig.muteChimes && quietConfig.isQuietNow()) {
            Timber.d("Hourly chime skipped: inside inactive/sleep window (%02d:00-%02d:00)",
                quietConfig.startHour, quietConfig.endHour)
            return
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hourLabel = String.format("%02d:00", hour)
        Timber.i("Delivering hourly chime for %s", hourLabel)
        prefs.addLog("Chime", "Hourly chime triggered for $hourLabel")

        val trigger = AirNotificationTrigger(
            source = "Hourly Chime ($hourLabel)",
            pattern = chimeConfig.pattern,
            isCall = false
        )
        AirBridgeService.enqueue(context, trigger)
    }
}
