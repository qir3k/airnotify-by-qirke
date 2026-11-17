package com.qirke.airnotify.ble

import android.content.Context
import com.fitbit.bluetooth.fbgatt.BitGattHelper
import com.fitbit.bluetooth.fbgatt.FitbitGatt
import com.fitbit.goldengate.GoldenGateConnectionManagerModule
import com.qirke.airnotify.BuildConfig
import timber.log.Timber
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
            // Immediately stop background scanners:
            // GoldenGate init starts AlwaysConnectedScanner by default, which runs continuous scans
            // and reconnects in background, locking out Google Health!
            // AirNotify connects strictly on-demand when an alert is enqueued.
            runCatching {
                val fg = FitbitGatt.getInstance()
                BitGattHelper.stopAllScanners(fg, context.applicationContext)
            }
        } catch (throwable: Throwable) {
            initialized.set(false)
            throw throwable
        }
    }
}
