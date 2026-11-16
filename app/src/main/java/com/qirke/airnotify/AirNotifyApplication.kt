package com.qirke.airnotify

import android.app.Application
import com.qirke.airnotify.ble.AirRuntime
import com.qirke.airnotify.chime.HourlyChimeScheduler
import com.qirke.airnotify.data.PreferencesManager
import timber.log.Timber

class AirNotifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        try {
            AirRuntime.ensureInitialized(this)
            Timber.i("AirRuntime initialized successfully")
        } catch (e: Throwable) {
            Timber.e(e, "Error initializing AirRuntime")
        }

        val prefs = PreferencesManager.getInstance(this)
        if (prefs.getHourlyChimeConfig().isEnabled) {
            HourlyChimeScheduler.scheduleNextChime(this)
        }
    }
}
