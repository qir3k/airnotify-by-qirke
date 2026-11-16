package com.qirke.airnotify.model

import java.util.Calendar

data class QuietHoursConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 23,   // 11 PM
    val startMinute: Int = 0,
    val endHour: Int = 7,      // 7 AM
    val endMinute: Int = 0
) {
    /** Returns true if current time falls within quiet hours (so alerts should be muted). */
    fun isQuietNow(): Boolean {
        if (!isEnabled) return false
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Wraps over midnight (e.g. 23:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
