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
        displayName = "Single Tap",
        description = "One discrete vibration tap",
        groupCount = 1,
        betweenGroupDelaysMillis = emptyList(),
        restoreDelayMillis = 80L
    ),
    HEARTBEAT(
        wireName = "heartbeat",
        displayName = "Heartbeat",
        description = "Two rapid pulses in heartbeat rhythm (da-DUM)",
        groupCount = 2,
        betweenGroupDelaysMillis = listOf(140L),
        restoreDelayMillis = 80L
    ),
    SYNCOPATED(
        wireName = "syncopated",
        displayName = "Syncopated",
        description = "One pulse, long pause, then two rapid taps (Ta-Daa)",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(500L, 140L),
        restoreDelayMillis = 80L
    ),
    URGENT(
        wireName = "urgent",
        displayName = "Urgent Burst",
        description = "Rapid machine-gun pulse burst",
        groupCount = 3,
        betweenGroupDelaysMillis = listOf(160L, 160L),
        restoreDelayMillis = 80L
    ),
    SPACED(
        wireName = "spaced",
        displayName = "Spaced Echo",
        description = "Two relaxed pulses with a long pause",
        groupCount = 2,
        betweenGroupDelaysMillis = listOf(750L),
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
