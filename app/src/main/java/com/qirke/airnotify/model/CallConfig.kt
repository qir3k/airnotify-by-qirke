package com.qirke.airnotify.model

import com.qirke.airnotify.ble.AirHapticPattern

data class CallConfig(
    val isEnabled: Boolean = true,
    val pattern: AirHapticPattern = AirHapticPattern.URGENT,
    val filterMode: FilterMode = FilterMode.ALL_NOTIFICATIONS,
    val favoritePersons: List<String> = emptyList(),
    val isContinuous: Boolean = true,
    val ringTimeoutSeconds: Int = 45
) {
    fun shouldAlert(callerNameOrNumber: String?): Boolean {
        if (!isEnabled) return false
        when (filterMode) {
            FilterMode.ALL_NOTIFICATIONS -> return true
            FilterMode.FAVORITES_ONLY -> {
                if (favoritePersons.isEmpty()) return false
                val target = callerNameOrNumber?.trim() ?: return false
                return favoritePersons.any { fav ->
                    target.contains(fav.trim(), ignoreCase = true) || fav.trim().contains(target, ignoreCase = true)
                }
            }
            FilterMode.EXCLUDE_SPECIFIED -> {
                if (favoritePersons.isEmpty()) return true
                val target = callerNameOrNumber?.trim() ?: return true
                return !favoritePersons.any { fav ->
                    target.contains(fav.trim(), ignoreCase = true) || fav.trim().contains(target, ignoreCase = true)
                }
            }
        }
    }
}
