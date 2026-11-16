package com.qirke.airnotify.ble

import android.content.Context
import com.fitbit.goldengate.GoldenGateConnectionManagerModule
import com.qirke.airnotify.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

object AirRuntime {
    private val initialized = AtomicBoolean(false)

    fun isInitialized(): Boolean = initialized.get()

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized.get()) return
        try {
            GoldenGateConnectionManagerModule.init(
                context.applicationContext,
                loggingEnabled = BuildConfig.DEBUG,
                isBleCentralRole = true
            )
            initialized.set(true)
        } catch (throwable: Throwable) {
            initialized.set(false)
            throw throwable
        }
    }
}
