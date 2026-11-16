package com.qirke.airnotify.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.qirke.airnotify.ble.AirHapticPattern
import com.qirke.airnotify.model.*
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private var cachedAppRules: List<AppRule>? = null

    companion object {
        private const val PREFS_NAME = "airnotify_preferences"
        private const val KEY_APP_RULES = "app_rules"
        private const val KEY_CALL_CONFIG = "call_config"
        private const val KEY_HOURLY_CHIME = "hourly_chime"
        private const val KEY_QUIET_HOURS = "quiet_hours"
        private const val KEY_DEVICE_CONFIG = "device_config"
        private const val KEY_LOGS = "event_logs"
        private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        private const val KEY_SYNC_PAUSE_UNTIL = "sync_pause_until"
        private const val KEY_COEXISTENCE_GUARD = "coexistence_guard"
        private const val KEY_ONLY_WHEN_SCREEN_OFF_NOTIFICATIONS = "only_screen_off_notifications"
        private const val KEY_ONLY_WHEN_SCREEN_OFF_CALLS = "only_screen_off_calls"
        private const val KEY_ALERT_ONLY_WHEN_SCREEN_OFF = "alert_only_when_screen_off"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_THEME_ACCENT = "theme_accent"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEFAULT_RULES_DISABLED_V1 = "default_rules_disabled_v1"

        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager =
            instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
    }

    // Default pre-populated apps (all disabled by default, user enables manually)
    private val defaultRules = listOf(
        AppRule(
            packageName = "com.facebook.orca",
            appName = "Messenger",
            isEnabled = false,
            pattern = AirHapticPattern.DOUBLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        ),
        AppRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            isEnabled = false,
            pattern = AirHapticPattern.DOUBLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        ),
        AppRule(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            isEnabled = false,
            pattern = AirHapticPattern.SINGLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        ),
        AppRule(
            packageName = "com.google.android.apps.messaging",
            appName = "Messages (SMS)",
            isEnabled = false,
            pattern = AirHapticPattern.SINGLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        ),
        AppRule(
            packageName = "com.instagram.android",
            appName = "Instagram",
            isEnabled = false,
            pattern = AirHapticPattern.SINGLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        )
    )

    fun getAppRules(): List<AppRule> {
        cachedAppRules?.let { return it }

        val json = prefs.getString(KEY_APP_RULES, null)
        if (json.isNullOrBlank()) {
            saveAppRules(defaultRules)
            return defaultRules
        }

        return try {
            val array = JSONArray(json)
            val list = mutableListOf<AppRule>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val favs = mutableListOf<String>()
                val favArr = obj.optJSONArray("favoritePersons")
                if (favArr != null) {
                    for (j in 0 until favArr.length()) {
                        favs.add(favArr.getString(j))
                    }
                }
                list.add(
                    AppRule(
                        packageName = obj.optString("packageName", ""),
                        appName = obj.optString("appName", "Unknown"),
                        isEnabled = obj.optBoolean("isEnabled", false),
                        pattern = AirHapticPattern.fromWireName(obj.optString("pattern", "double")),
                        filterMode = runCatching { FilterMode.valueOf(obj.optString("filterMode", "ALL_NOTIFICATIONS")) }.getOrDefault(FilterMode.ALL_NOTIFICATIONS),
                        favoritePersons = favs,
                        caseSensitive = obj.optBoolean("caseSensitive", false)
                    )
                )
            }
            val result = if (list.isEmpty()) defaultRules else list
            cachedAppRules = result
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse app rules JSON, falling back to default rules")
            defaultRules
        }
    }

    fun saveAppRules(rules: List<AppRule>) {
        cachedAppRules = rules
        val array = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("packageName", rule.packageName)
            obj.put("appName", rule.appName)
            obj.put("isEnabled", rule.isEnabled)
            obj.put("pattern", rule.pattern.wireName)
            obj.put("filterMode", rule.filterMode.name)
            val favArr = JSONArray()
            rule.favoritePersons.forEach { favArr.put(it) }
            obj.put("favoritePersons", favArr)
            obj.put("caseSensitive", rule.caseSensitive)
            array.put(obj)
        }
        prefs.edit().putString(KEY_APP_RULES, array.toString()).commit()
    }

    fun findAppRule(packageName: String): AppRule? {
        return getAppRules().firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
    }

    fun saveOrUpdateAppRule(rule: AppRule) {
        val current = getAppRules().toMutableList()
        val index = current.indexOfFirst { it.packageName.equals(rule.packageName, ignoreCase = true) }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        saveAppRules(current)
    }

    fun removeAppRule(packageName: String) {
        val current = getAppRules().filterNot { it.packageName.equals(packageName, ignoreCase = true) }
        saveAppRules(current)
    }

    fun getCallConfig(): CallConfig {
        val json = prefs.getString(KEY_CALL_CONFIG, null) ?: return CallConfig()
        return try {
            gson.fromJson(json, CallConfig::class.java) ?: CallConfig()
        } catch (e: Exception) {
            CallConfig()
        }
    }

    fun saveCallConfig(config: CallConfig) {
        prefs.edit().putString(KEY_CALL_CONFIG, gson.toJson(config)).apply()
    }

    fun getHourlyChimeConfig(): HourlyChimeConfig {
        val json = prefs.getString(KEY_HOURLY_CHIME, null) ?: return HourlyChimeConfig()
        return try {
            gson.fromJson(json, HourlyChimeConfig::class.java) ?: HourlyChimeConfig()
        } catch (e: Exception) {
            HourlyChimeConfig()
        }
    }

    fun saveHourlyChimeConfig(config: HourlyChimeConfig) {
        prefs.edit().putString(KEY_HOURLY_CHIME, gson.toJson(config)).apply()
    }

    fun getQuietHoursConfig(): QuietHoursConfig {
        val json = prefs.getString(KEY_QUIET_HOURS, null) ?: return QuietHoursConfig()
        return try {
            gson.fromJson(json, QuietHoursConfig::class.java) ?: QuietHoursConfig()
        } catch (e: Exception) {
            QuietHoursConfig()
        }
    }

    fun saveQuietHoursConfig(config: QuietHoursConfig) {
        prefs.edit().putString(KEY_QUIET_HOURS, gson.toJson(config)).apply()
    }

    fun getDeviceConfig(): DeviceConfig {
        val json = prefs.getString(KEY_DEVICE_CONFIG, null) ?: return DeviceConfig()
        return try {
            gson.fromJson(json, DeviceConfig::class.java) ?: DeviceConfig()
        } catch (e: Exception) {
            DeviceConfig()
        }
    }

    fun saveDeviceConfig(config: DeviceConfig) {
        prefs.edit().putString(KEY_DEVICE_CONFIG, gson.toJson(config)).apply()
    }

    fun saveDeviceAddress(address: String, name: String = "Fitbit Air") {
        val current = getDeviceConfig()
        saveDeviceConfig(current.copy(address = address, name = name))
    }

    fun saveLastStatus(status: String) {
        val current = getDeviceConfig()
        saveDeviceConfig(current.copy(lastStatus = status))
    }

    fun setMasterEnabled(enabled: Boolean) {
        val current = getDeviceConfig()
        saveDeviceConfig(current.copy(masterEnabled = enabled))
    }

    fun isAutoCheckUpdatesEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)

    fun setAutoCheckUpdatesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
    }

    fun isCoexistenceGuardEnabled(): Boolean =
        prefs.getBoolean(KEY_COEXISTENCE_GUARD, true)

    fun setCoexistenceGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_COEXISTENCE_GUARD, enabled).apply()
    }

    fun getAppLanguage(): String {
        return prefs.getString(KEY_APP_LANGUAGE, null) ?: run {
            val sys = java.util.Locale.getDefault().language
            if (sys.equals("pl", ignoreCase = true)) "pl" else "en"
        }
    }

    fun setAppLanguage(lang: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, lang).apply()
    }

    fun getThemeAccent(): String =
        prefs.getString(KEY_THEME_ACCENT, "system") ?: "system"

    fun setThemeAccent(accent: String) {
        prefs.edit().putString(KEY_THEME_ACCENT, accent).apply()
    }

    fun getThemeMode(): String =
        prefs.getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun isOnlyWhenScreenOffEnabled(): Boolean =
        prefs.getBoolean(KEY_ALERT_ONLY_WHEN_SCREEN_OFF, true)

    fun setOnlyWhenScreenOffEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERT_ONLY_WHEN_SCREEN_OFF, enabled)
            .putBoolean(KEY_ONLY_WHEN_SCREEN_OFF_NOTIFICATIONS, enabled)
            .putBoolean(KEY_ONLY_WHEN_SCREEN_OFF_CALLS, enabled)
            .apply()
    }

    fun isOnlyWhenScreenOffForNotifications(): Boolean = isOnlyWhenScreenOffEnabled()

    fun setOnlyWhenScreenOffForNotifications(enabled: Boolean) = setOnlyWhenScreenOffEnabled(enabled)

    fun isOnlyWhenScreenOffForCalls(): Boolean = isOnlyWhenScreenOffEnabled()

    fun setOnlyWhenScreenOffForCalls(enabled: Boolean) = setOnlyWhenScreenOffEnabled(enabled)

    fun pauseForSync(durationMinutes: Int = 5) {
        val until = System.currentTimeMillis() + (durationMinutes * 60_000L)
        prefs.edit().putLong(KEY_SYNC_PAUSE_UNTIL, until).apply()
        addLog("Sync", "Alerts paused for $durationMinutes minutes for companion sync")
    }

    fun clearSyncPause() {
        prefs.edit().remove(KEY_SYNC_PAUSE_UNTIL).apply()
        addLog("Sync", "Sync pause cleared; alerts resumed")
    }

    fun isSyncPaused(): Boolean =
        getSyncPauseRemainingMillis() > 0L

    fun getSyncPauseRemainingMillis(): Long {
        val until = prefs.getLong(KEY_SYNC_PAUSE_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (until > now) until - now else 0L
    }

    fun getLogs(): List<LogEntry> {
        val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<LogEntry>>() {}.type
            gson.fromJson<List<LogEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun addLog(tag: String, message: String, isError: Boolean = false) {
        val logs = getLogs().toMutableList()
        logs.add(0, LogEntry(System.currentTimeMillis(), tag, message, isError))
        if (logs.size > 50) {
            logs.subList(50, logs.size).clear()
        }
        prefs.edit().putString(KEY_LOGS, gson.toJson(logs)).apply()
    }

    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private val KEY_RECENT_SENDERS_PREFIX = "recent_senders_"

    fun getRecentSenders(packageName: String): List<String> {
        val json = prefs.getString("$KEY_RECENT_SENDERS_PREFIX$packageName", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecentSender(packageName: String, senderName: String) {
        val trimmed = senderName.trim()
        if (trimmed.isBlank() || trimmed.length < 2) return
        val current = getRecentSenders(packageName).toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = if (current.size > 20) current.take(20) else current
        prefs.edit().putString("$KEY_RECENT_SENDERS_PREFIX$packageName", gson.toJson(capped)).apply()
    }
}
