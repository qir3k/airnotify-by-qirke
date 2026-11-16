package com.qirke.airnotify.ble

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object BleCoexistenceManager {

    /** Delay in milliseconds to allow Android's Bluetooth daemon (Fluoride/BlueDroid) to cleanly flush L2CAP channels after disconnect */
    const val RADIO_SETTLE_MILLIS = 250L

    /** Extended delay when a busy/133 error occurred to allow other companion apps (Google Health / Pulse) to finish their sync transfer */
    const val SYNC_COLLISION_BACKOFF_MILLIS = 1500L

    private val isSyncCollisionDetected = AtomicBoolean(false)
    private val lastCollisionTime = AtomicLong(0L)

    val KNOWN_HEALTH_APPS = listOf(
        "com.fitbit.FitbitMobile" to "Fitbit / Google Health",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.google.android.apps.healthdata" to "Health Connect",
        "dev.airpulse.pulse" to "Pulse",
        "com.airpulse.pulse" to "Pulse"
    )

    fun markCollision() {
        isSyncCollisionDetected.set(true)
        lastCollisionTime.set(System.currentTimeMillis())
        Timber.w("BleCoexistenceManager: Detected BLE conflict with health sync. Applying backoff.")
    }

    fun isRecentCollision(): Boolean {
        return isSyncCollisionDetected.get() && (System.currentTimeMillis() - lastCollisionTime.get() < 10_000L)
    }

    fun clearCollision() {
        isSyncCollisionDetected.set(false)
    }

    /**
     * Finds installed companion fitness apps (Fitbit, Google Health, Pulse) on the user's phone.
     */
    fun findInstalledHealthApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val results = mutableListOf<Pair<String, String>>()
        for ((pkg, label) in KNOWN_HEALTH_APPS) {
            try {
                pm.getPackageInfo(pkg, 0)
                results.add(pkg to label)
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return results
    }

    /**
     * Launches the companion fitness app so the user can easily sync.
     */
    fun launchHealthApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            Timber.e(e, "Could not launch health app $packageName")
            false
        }
    }
}
