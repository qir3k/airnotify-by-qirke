package com.qirke.airnotify.util

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * Utility helper to detect device display and lock status.
 * Used to determine if the phone is actively in use (unlocked and screen on)
 * versus locked/in pocket/screen off.
 */
object DeviceStateHelper {

    /**
     * Returns true if the user is actively using the phone (screen is ON and device is UNLOCKED).
     *
     * Returns false when:
     * - Screen is completely OFF (in pocket, sitting on desk)
     * - Screen is locked (lockscreen, Always-On-Display, incoming call ringing on lockscreen)
     */
    fun isPhoneInActiveUse(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isScreenInteractive = powerManager?.isInteractive ?: false

            if (!isScreenInteractive) {
                // Screen is OFF: Phone is definitely not in active use
                return false
            }

            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = keyguardManager?.isKeyguardLocked ?: false

            if (isLocked) {
                // Screen is lit (e.g. lockscreen, AOD, or call screen), but device is locked.
                // Not in active user hands-on use.
                return false
            }

            // Screen is ON AND device is UNLOCKED -> User is actively using the phone
            true
        } catch (e: Exception) {
            Timber.w(e, "Error checking device state; assuming phone is not in active use")
            false
        }
    }
}
