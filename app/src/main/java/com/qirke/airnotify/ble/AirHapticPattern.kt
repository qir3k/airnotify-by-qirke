package com.qirke.airnotify.ble

enum class AirHapticPattern(
    val wireName: String,
    val displayName: String,
    val description: String,
    val groupCount: Int,
    val betweenGroupDelaysMillis: List<Long>,
    val restoreDelayMillis: Long = 180L
) {
    SINGLE(
        wireName = "single",
        displayName = "Single Pulse",
        description = "One discrete vibration tap",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 180L
    ),
    DOUBLE(
        wireName = "double",
        displayName = "Double Pulse",
        description = "Two distinct vibration taps",
        groupCount = 2,
        betweenGroupDelaysMillis = listOf(180L),
        restoreDelayMillis = 180L
    ),
    TRIPLE(
        wireName = "triple",
        displayName = "Triple Pulse",
        description = "Three rapid vibration taps",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(180L, 180L),
        restoreDelayMillis = 180L
    ),
    URGENT(
        wireName = "urgent",
        displayName = "Urgent Burst",
        description = "Fast, noticeable vibration burst",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(120L, 120L),
        restoreDelayMillis = 150L
    ),
    GENTLE(
        wireName = "gentle",
        displayName = "Gentle Chime",
        description = "Short subtle tap ideal for hourly chime",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 120L
    ),
    CONTINUOUS(
        wireName = "continuous",
        displayName = "Continuous Ringing",
        description = "Repeated pulsing until call is answered or silenced",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 200L
    );

    companion object {
        fun fromWireName(wireName: String?): AirHapticPattern =
            values().firstOrNull { it.wireName.equals(wireName, ignoreCase = true) } ?: SINGLE
    }
}
