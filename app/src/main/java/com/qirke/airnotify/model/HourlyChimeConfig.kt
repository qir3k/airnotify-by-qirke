package com.qirke.airnotify.model

import com.qirke.airnotify.ble.AirHapticPattern

data class HourlyChimeConfig(
    val isEnabled: Boolean = false,
    val pattern: AirHapticPattern = AirHapticPattern.GENTLE,
    // Legacy active window fields (kept for backward-compatibility; inactive/sleep
    // hours are now centrally controlled across all features by QuietHoursConfig).
    val startHour: Int = 0,
    val endHour: Int = 23
) {
    /**
     * Legacy helper to check whether a given hour (0..23) is active.
     */
    fun isHourActive(hourOfDay: Int): Boolean {
        if (!isEnabled) return false
        return if (startHour <= endHour) {
            hourOfDay in startHour..endHour
        } else {
            hourOfDay >= startHour || hourOfDay <= endHour
        }
    }
}
