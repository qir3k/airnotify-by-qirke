package com.qirke.airnotify.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.qirke.airnotify.data.ContactHelper
import com.qirke.airnotify.data.PreferencesManager
import com.qirke.airnotify.service.AirBridgeService
import com.qirke.airnotify.service.AirNotificationTrigger
import timber.log.Timber

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = PreferencesManager.getInstance(context)
        val callConfig = prefs.getCallConfig()

        if (!callConfig.isEnabled) return

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val quietConfig = prefs.getQuietHoursConfig()
                if (quietConfig.isEnabled && quietConfig.muteCalls && quietConfig.isQuietNow()) {
                    Timber.d("Incoming call vibration muted: inside inactive/sleep window (%02d:00-%02d:00)",
                        quietConfig.startHour, quietConfig.endHour)
                    prefs.addLog("Call", "Silenced incoming call (Inactive / Sleep Window)")
                    return
                }

                // Check screen off / phone in use filter for calls
                if (prefs.isOnlyWhenScreenOffEnabled() && com.qirke.airnotify.util.DeviceStateHelper.isPhoneInActiveUse(context)) {
                    Timber.d("Incoming call vibration skipped: phone is actively in use (unlocked)")
                    prefs.addLog("Call", "Skipped band vibration (phone is in active use)")
                    return
                }

                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val contactName = ContactHelper.resolveContactName(context, incomingNumber)
                val callerIdentity = contactName ?: incomingNumber ?: "Incoming Call"

                if (callConfig.shouldAlert(callerIdentity)) {
                    Timber.i("Call alert accepted for %s", callerIdentity)
                    prefs.addLog("Call", "Incoming call ringing: $callerIdentity")

                    val trigger = AirNotificationTrigger(
                        source = "Call: $callerIdentity",
                        pattern = callConfig.pattern,
                        isCall = true,
                        isContinuousCall = callConfig.isContinuous
                    )
                    AirBridgeService.enqueue(context, trigger)
                } else {
                    Timber.d("Call alert filtered out for %s (not in favorite callers)", callerIdentity)
                    prefs.addLog("Call", "Filtered out call from '$callerIdentity'")
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Timber.d("Phone state changed to %s; cancelling ringing vibration", state)
                AirBridgeService.cancelCall(context)
            }
        }
    }
}
