package com.qirke.airnotify.service

import android.os.SystemClock
import com.qirke.airnotify.ble.AirHapticPattern

data class AirNotificationTrigger(
    val source: String,
    val pattern: AirHapticPattern,
    val isCall: Boolean = false,
    val isContinuousCall: Boolean = false,
    val isConnectionCheck: Boolean = false,
    val isManual: Boolean = false,
    val createdAtElapsed: Long = SystemClock.elapsedRealtime(),
    val callSessionId: Long = 0L
)
