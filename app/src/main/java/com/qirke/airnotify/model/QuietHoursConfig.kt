package com.qirke.airnotify.model

import java.util.Calendar

data class QuietHoursConfig(
    val isEnabled: Boolean = true,
    val startHour: Int = 23,   // 23:00 (11 PM) - Start of inactive / sleep window
    val startMinute: Int = 0,
    val endHour: Int = 7,      // 07:00 (7 AM) - End of inactive / sleep window (wake time)
    val endMinute: Int = 0,
    val muteNotifications: Boolean = true,
    val muteCalls: Boolean = true,
    val muteChimes: Boolean = true
) {
    /**
     * Returns true if the given calendar time (or now) falls within the inactive/sleep window.
     */
    fun isQuietNow(calendar: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled) return false
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return isMinutesQuiet(currentMinutes)
    }

    /**
     * Returns true if a specific hour (0..23) falls within the inactive/sleep window.
     */
    fun isHourQuiet(hourOfDay: Int): Boolean {
        if (!isEnabled) return false
        return isMinutesQuiet(hourOfDay * 60)
    }

    private fun isMinutesQuiet(currentMinutes: Int): Boolean {
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes until endMinutes
        } else {
            // Wraps over midnight (e.g. 23:00 to 07:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }
}
