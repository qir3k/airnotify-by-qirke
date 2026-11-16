package com.qirke.airnotify.ble

enum class AirHapticPattern(
    val wireName: String,
    val displayName: String,
    val description: String,
    val groupCount: Int,
    val betweenGroupDelaysMillis: List<Long>,
    val restoreDelayMillis: Long = 80L
) {
    SINGLE(
        wireName = "single",
        displayName = "Single Pulse",
        description = "One discrete vibration tap",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 80L
    ),
    DOUBLE(
        wireName = "double",
        displayName = "Double Pulse",
        description = "Two distinct vibration taps",
        groupCount = 2,
        betweenGroupDelaysMillis = listOf(450L),
        restoreDelayMillis = 80L
    ),
    TRIPLE(
        wireName = "triple",
        displayName = "Triple Pulse",
        description = "Three rapid vibration taps",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(400L, 400L),
        restoreDelayMillis = 80L
    ),
    URGENT(
        wireName = "urgent",
        displayName = "Urgent Burst",
        description = "Fast, noticeable vibration burst",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(220L, 220L),
        restoreDelayMillis = 80L
    ),
    GENTLE(
        wireName = "gentle",
        displayName = "Gentle Chime",
        description = "Short subtle tap ideal for hourly chime",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 80L
    ),
    CONTINUOUS(
        wireName = "continuous",
        displayName = "Continuous Ringing",
        description = "Repeated pulsing until call is answered or silenced",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 80L
    );

    companion object {
        fun fromWireName(wireName: String?): AirHapticPattern =
            values().firstOrNull { it.wireName.equals(wireName, ignoreCase = true) } ?: SINGLE
    }
}
