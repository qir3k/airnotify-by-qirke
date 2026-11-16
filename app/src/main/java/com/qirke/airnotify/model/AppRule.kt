package com.qirke.airnotify.model

import com.qirke.airnotify.ble.AirHapticPattern
import java.text.Normalizer

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
                matchesSingle(target, favClean)
            }
        }
    }

    private fun matchesSingle(target: String, pattern: String): Boolean {
        if (caseSensitive) {
            if (target.contains(pattern) || pattern.contains(target)) return true
        } else {
            // Direct case-insensitive substring (handles emojis, custom symbols, etc.)
            if (target.contains(pattern, ignoreCase = true) || pattern.contains(target, ignoreCase = true)) return true

            // Diacritic-forgiving match (e.g. "Mis" matches "Miś", "Miśka ❤️", "Misica")
            val normTarget = normalize(target)
            val normPattern = normalize(pattern)
            if (normTarget.contains(normPattern) || normPattern.contains(normTarget)) return true
        }
        return false
    }

    companion object {
        /**
         * Normalizes text by decomposing diacritical marks (e.g. 'ś' -> 's', 'ą' -> 'a')
         * and converting to lower case for forgiving matching.
         */
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
            return decomposed.replace("\\p{M}".toRegex(), "").lowercase().trim()
        }
    }
}
