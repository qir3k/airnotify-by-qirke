package com.qirke.airnotify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qirke.airnotify.chime.HourlyChimeScheduler
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("BootReceiver received action: %s", intent.action)
        HourlyChimeScheduler.scheduleNextChime(context)
    }
}
