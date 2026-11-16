package com.qirke.airnotify.ble

import android.content.Context
import timber.log.Timber

object PendingHapticRecovery {
    private const val PREFS_NAME = "air_haptic_recovery"
    private const val KEY_PREFIX = "pending_intensity_"

    fun mark(context: Context, address: String, originalIntensity: Int) {
        val key = keyFor(address)
        Timber.i("PendingHapticRecovery: Checkpointing %s to %d", address, originalIntensity)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(key, originalIntensity)
            .apply()
    }

    fun get(context: Context, address: String): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = keyFor(address)
        return if (prefs.contains(key)) prefs.getInt(key, AirDirectSettingsProtocol.HAPTICS_HIGH) else null
    }

    fun clear(context: Context, address: String) {
        val key = keyFor(address)
        Timber.i("PendingHapticRecovery: Clearing recovery for %s", address)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun keyFor(address: String): String = KEY_PREFIX + address.uppercase()
}
