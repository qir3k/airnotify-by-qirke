package com.qirke.airnotify.model

enum class FilterMode(val displayName: String, val description: String) {
    ALL_NOTIFICATIONS(
        displayName = "All Notifications",
        description = "Vibrate for all alerts from this app"
    ),
    FAVORITES_ONLY(
        displayName = "Favorite Persons Only",
        description = "Only vibrate when sender matches your favorite list (e.g. girlfriend, family)"
    ),
    EXCLUDE_SPECIFIED(
        displayName = "Exclude Persons",
        description = "Vibrate for everyone except specified persons/chats"
    )
}
