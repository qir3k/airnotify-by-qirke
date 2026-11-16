package com.qirke.airnotify.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.qirke.airnotify.data.PreferencesManager
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

class AirNotificationListenerService : NotificationListenerService() {

    private val recentAlerts = ConcurrentHashMap<String, Long>()

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Timber.i("AirNotify Notification Listener connected")
        PreferencesManager.getInstance(this).addLog("Listener", "Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        isConnected = false
        Timber.w("AirNotify Notification Listener disconnected")
        PreferencesManager.getInstance(this).addLog("Listener", "Notification Listener disconnected", isError = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, AirNotificationListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return

        // Skip group summaries and ongoing background notifications
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val prefs = PreferencesManager.getInstance(this)
        val devConfig = prefs.getDeviceConfig()
        if (!devConfig.masterEnabled) return

        val quietConfig = prefs.getQuietHoursConfig()
        if (quietConfig.isEnabled && quietConfig.muteNotifications && quietConfig.isQuietNow()) {
            Timber.d("Notification muted: inside inactive/sleep window (%02d:00-%02d:00)",
                quietConfig.startHour, quietConfig.endHour)
            return
        }

        val rule = prefs.findAppRule(sbn.packageName) ?: return
        if (!rule.isEnabled) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

        val personName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)?.name?.toString()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            extras.getParcelable<android.app.Person>(Notification.EXTRA_MESSAGING_PERSON)?.name?.toString()
        } else null

        val sender = personName ?: title ?: subText ?: ""
        val content = text ?: bigText ?: ""

        // Remember sender name for easy 1-tap VIP adding in app rules
        if (sender.isNotBlank()) {
            prefs.addRecentSender(sbn.packageName, sender)
        }

        // Check favorite persons / exclude filter
        val passed = rule.shouldAlert(sender, content)
        if (!passed) {
            Timber.d("Filter blocked notification from %s (sender: %s)", rule.appName, sender)
            prefs.addLog("Filter", "Filtered out ${rule.appName} from '$sender'")
            return
        }

        // Deduplicate rapid repeat bursts (debounce 2.5 seconds)
        val signature = "${sbn.packageName}:${sender}:${sbn.id}"
        val now = SystemClock.elapsedRealtime()
        val lastTime = recentAlerts[signature] ?: 0L
        if (now - lastTime < 2500L) {
            Timber.d("Debounced duplicate notification for %s", signature)
            return
        }
        recentAlerts[signature] = now

        // Clean old signatures
        if (recentAlerts.size > 100) {
            recentAlerts.entries.removeIf { now - it.value > 10_000L }
        }

        val displaySource = if (sender.isNotBlank()) "${rule.appName} ($sender)" else rule.appName
        val trigger = AirNotificationTrigger(
            source = displaySource,
            pattern = rule.pattern,
            isCall = false
        )

        val accepted = AirBridgeService.enqueue(this, trigger)
        if (accepted) {
            Timber.i("Alert triggered for %s pattern=%s", displaySource, rule.pattern.displayName)
            prefs.addLog("Notification", "Alerting for $displaySource [${rule.pattern.displayName}]")
        }
    }

    override fun onDestroy() {
        isConnected = false
        super.onDestroy()
    }
}
