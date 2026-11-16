package com.qirke.airnotify

import com.qirke.airnotify.ble.AirDirectSettingsProtocol
import com.qirke.airnotify.ble.AirHapticPattern
import com.qirke.airnotify.model.AppRule
import com.qirke.airnotify.model.FilterMode
import com.qirke.airnotify.model.HourlyChimeConfig
import com.qirke.airnotify.model.QuietHoursConfig
import org.junit.Assert.*
import org.junit.Test

class AirNotifyUnitTest {

    @Test
    fun testAppRuleFavoritesOnlyFiltering() {
        val rule = AppRule(
            packageName = "com.facebook.orca",
            appName = "Messenger",
            isEnabled = true,
            pattern = AirHapticPattern.DOUBLE,
            filterMode = FilterMode.FAVORITES_ONLY,
            favoritePersons = listOf("girlfriend", "Anna")
        )

        // Matching cases (case-insensitive substring)
        assertTrue("Exact match should pass", rule.shouldAlert("girlfriend"))
        assertTrue("Capitalized girlfriend should pass", rule.shouldAlert("Girlfriend"))
        assertTrue("Name with suffix should pass", rule.shouldAlert("Girlfriend (Mobile)"))
        assertTrue("Anna should pass", rule.shouldAlert("Anna"))
        assertTrue("Content text with name should pass", rule.shouldAlert("Chat Group", "Hey from girlfriend!"))

        // Non-matching cases
        assertFalse("Other sender should be rejected", rule.shouldAlert("Bob"))
        assertFalse("Work colleague should be rejected", rule.shouldAlert("Boss"))
        assertFalse("Null sender and content should be rejected", rule.shouldAlert(null, null))
        assertFalse("Empty sender and content should be rejected", rule.shouldAlert("", ""))
    }

    @Test
    fun testAppRuleAllNotificationsMode() {
        val rule = AppRule(
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            isEnabled = true,
            pattern = AirHapticPattern.SINGLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS,
            favoritePersons = emptyList()
        )

        assertTrue("Any sender should pass in ALL mode", rule.shouldAlert("Random Sender"))
        assertTrue("Null sender still alerts if enabled in ALL mode", rule.shouldAlert(null))
    }

    @Test
    fun testAppRuleSubstringAndEmojiAndDiacriticMatching() {
        val rule = AppRule(
            packageName = "com.facebook.orca",
            appName = "Messenger",
            isEnabled = true,
            pattern = AirHapticPattern.DOUBLE,
            filterMode = FilterMode.FAVORITES_ONLY,
            favoritePersons = listOf("Mis")
        )

        // User specific question cases
        assertTrue("'Mis' should match 'Misica'", rule.shouldAlert("Misica"))
        assertTrue("'Mis' should match 'Misica ❤️' with emojis", rule.shouldAlert("Misica ❤️"))
        assertTrue("'Mis' should match '❤️ Misica ❤️' surrounded by symbols", rule.shouldAlert("❤️ Misica ❤️"))
        assertTrue("'Mis' should match Polish diacritic 'Miś'", rule.shouldAlert("Miś"))
        assertTrue("'Mis' should match 'Miśka ✨'", rule.shouldAlert("Miśka ✨"))
        assertTrue("'Mis' should match in chat message 'Misica: Cześć!'", rule.shouldAlert("Messenger", "Misica: Cześć!"))

        // Unrelated persons should NOT match
        assertFalse("'Mis' should not match 'Tomasz'", rule.shouldAlert("Tomasz"))
        assertFalse("'Mis' should not match 'Anna'", rule.shouldAlert("Anna"))
        assertFalse("'Mis' should not match 'Mama'", rule.shouldAlert("Mama"))
    }

    @Test
    fun testQuietHoursInactiveWindowOvernight() {
        val quiet = QuietHoursConfig(
            isEnabled = true,
            startHour = 23,
            startMinute = 0,
            endHour = 7,
            endMinute = 0,
            muteNotifications = true,
            muteCalls = true,
            muteChimes = true
        )

        // Hours inside inactive / sleep window (23:00 to 07:00)
        assertTrue("Hour 23 (bedtime) should be quiet/inactive", quiet.isHourQuiet(23))
        assertTrue("Hour 0 (midnight) should be quiet/inactive", quiet.isHourQuiet(0))
        assertTrue("Hour 3 (deep sleep) should be quiet/inactive", quiet.isHourQuiet(3))
        assertTrue("Hour 6 (early morning) should be quiet/inactive", quiet.isHourQuiet(6))

        // Hours outside inactive window (awake hours: 07:00 to 23:00)
        assertFalse("Hour 7 (wake hour) should be active (not quiet)", quiet.isHourQuiet(7))
        assertFalse("Hour 8 should be active (not quiet)", quiet.isHourQuiet(8))
        assertFalse("Hour 12 (noon) should be active (not quiet)", quiet.isHourQuiet(12))
        assertFalse("Hour 18 (evening) should be active (not quiet)", quiet.isHourQuiet(18))
        assertFalse("Hour 22 should be active (not quiet)", quiet.isHourQuiet(22))
    }

    @Test
    fun testQuietHoursDaytimeWindow() {
        val quiet = QuietHoursConfig(
            isEnabled = true,
            startHour = 13,
            startMinute = 0,
            endHour = 15,
            endMinute = 0
        )

        assertFalse("Hour 12 should be active", quiet.isHourQuiet(12))
        assertTrue("Hour 13 should be quiet", quiet.isHourQuiet(13))
        assertTrue("Hour 14 should be quiet", quiet.isHourQuiet(14))
        assertFalse("Hour 15 should be active", quiet.isHourQuiet(15))
        assertFalse("Hour 20 should be active", quiet.isHourQuiet(20))
    }

    @Test
    fun testQuietHoursDisabled() {
        val quiet = QuietHoursConfig(
            isEnabled = false,
            startHour = 23,
            endHour = 7
        )

        assertFalse("Disabled quiet hours should never silence hour 23", quiet.isHourQuiet(23))
        assertFalse("Disabled quiet hours should never silence hour 2", quiet.isHourQuiet(2))
        assertFalse("Disabled quiet hours should never silence hour 12", quiet.isHourQuiet(12))
    }

    @Test
    fun testQuietHoursFeatureFlags() {
        val quiet = QuietHoursConfig()
        assertTrue(quiet.isEnabled)
        assertEquals(23, quiet.startHour)
        assertEquals(7, quiet.endHour)
        assertTrue("Notifications should be muted by default during sleep", quiet.muteNotifications)
        assertTrue("Calls should be muted by default during sleep", quiet.muteCalls)
        assertTrue("Chimes should be muted by default during sleep", quiet.muteChimes)
    }

    @Test
    fun testAirDirectSettingsProtocolCodec() {
        val req = AirDirectSettingsProtocol.encodeHapticsSettingsRequest()
        assertNotNull(req)
        assertTrue(req.isNotEmpty())

        val highResp = AirDirectSettingsProtocol.encodeHapticsSettingsResponse(AirDirectSettingsProtocol.HAPTICS_HIGH)
        val parsedHigh = AirDirectSettingsProtocol.parseHapticsIntensity(highResp)
        assertEquals(AirDirectSettingsProtocol.HAPTICS_HIGH, parsedHigh)

        val lowResp = AirDirectSettingsProtocol.encodeHapticsSettingsResponse(AirDirectSettingsProtocol.HAPTICS_LOW)
        val parsedLow = AirDirectSettingsProtocol.parseHapticsIntensity(lowResp)
        assertEquals(AirDirectSettingsProtocol.HAPTICS_LOW, parsedLow)
    }

    @Test
    fun testHapticPatternLookup() {
        assertEquals(AirHapticPattern.SINGLE, AirHapticPattern.fromWireName("single"))
        assertEquals(AirHapticPattern.DOUBLE, AirHapticPattern.fromWireName("double"))
        assertEquals(AirHapticPattern.TRIPLE, AirHapticPattern.fromWireName("triple"))
        assertEquals(AirHapticPattern.URGENT, AirHapticPattern.fromWireName("urgent"))
        assertEquals(AirHapticPattern.GENTLE, AirHapticPattern.fromWireName("gentle"))
        assertEquals(AirHapticPattern.CONTINUOUS, AirHapticPattern.fromWireName("continuous"))
        assertEquals(AirHapticPattern.SINGLE, AirHapticPattern.fromWireName("unknown"))
    }

    @Test
    fun testBleCoexistenceConstants() {
        assertTrue(com.qirke.airnotify.ble.BleCoexistenceManager.RADIO_SETTLE_MILLIS >= 200L)
        assertTrue(com.qirke.airnotify.ble.BleCoexistenceManager.SYNC_COLLISION_BACKOFF_MILLIS >= 1000L)
        assertTrue(com.qirke.airnotify.ble.BleCoexistenceManager.KNOWN_HEALTH_APPS.isNotEmpty())
    }
}
