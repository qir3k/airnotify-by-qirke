package com.qirke.airnotify.model

import com.qirke.airnotify.ble.AirHapticPattern
import java.util.Calendar

data class HourlyChimeConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 8,  // 8 AM
    val endHour: Int = 22,   // 10 PM
    val pattern: AirHapticPattern = AirHapticPattern.GENTLE
) {
    /** Checks whether the given hour (0..23) is within the enabled active window. */
    fun isHourActive(hourOfDay: Int): Boolean {
        if (!isEnabled) return false
        return if (startHour <= endHour) {
            hourOfDay in startHour..endHour
        } else {
            // Wraps around midnight (e.g. 20..04)
            hourOfDay >= startHour || hourOfDay <= endHour
        }
    }

    fun isNowActive(): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return isHourActive(currentHour)
    }
}
