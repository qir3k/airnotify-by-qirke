package com.qirke.airnotify.model

import com.qirke.airnotify.ble.AirHapticPattern

data class AppRule(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val pattern: AirHapticPattern = AirHapticPattern.DOUBLE,
    val filterMode: FilterMode = FilterMode.ALL_NOTIFICATIONS,
    val favoritePersons: List<String> = emptyList(),
    val caseSensitive: Boolean = false
) {
    /**
     * Determines whether an incoming notification from [senderName] (and optionally [contentText])
     * passes the filter rule.
     */
    fun shouldAlert(senderName: String?, contentText: String? = null): Boolean {
        if (!isEnabled) return false

        when (filterMode) {
            FilterMode.ALL_NOTIFICATIONS -> return true

            FilterMode.FAVORITES_ONLY -> {
                if (favoritePersons.isEmpty()) return false
                return matchesAnyPerson(senderName, contentText)
            }

            FilterMode.EXCLUDE_SPECIFIED -> {
                if (favoritePersons.isEmpty()) return true
                return !matchesAnyPerson(senderName, contentText)
            }
        }
    }

    private fun matchesAnyPerson(senderName: String?, contentText: String?): Boolean {
        val targets = listOfNotNull(senderName, contentText).filter { it.isNotBlank() }
        if (targets.isEmpty()) return false

        return favoritePersons.any { fav ->
            val favClean = fav.trim()
            if (favClean.isEmpty()) return@any false

            targets.any { target ->
                if (caseSensitive) {
                    target.contains(favClean) || favClean.contains(target)
                } else {
                    target.contains(favClean, ignoreCase = true) || favClean.contains(target, ignoreCase = true)
                }
            }
        }
    }
}
