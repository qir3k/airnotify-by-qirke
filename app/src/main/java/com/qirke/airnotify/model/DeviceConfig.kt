package com.qirke.airnotify.model

data class DeviceConfig(
    val address: String? = null,
    val name: String = "Fitbit Air",
    val masterEnabled: Boolean = true,
    val lastStatus: String = "Ready (Idle)"
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val message: String,
    val isError: Boolean = false
)
