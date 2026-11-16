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
    fun testAppRuleDisabled() {
        val rule = AppRule(
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            isEnabled = false,
            pattern = AirHapticPattern.SINGLE,
            filterMode = FilterMode.ALL_NOTIFICATIONS
        )

        assertFalse("Disabled rule should never alert", rule.shouldAlert("Anyone"))
    }

    @Test
    fun testHourlyChimeActiveWindow() {
        val chime = HourlyChimeConfig(
            isEnabled = true,
            startHour = 8,
            endHour = 22,
            pattern = AirHapticPattern.GENTLE
        )

        assertTrue("Hour 8 (start) should be active", chime.isHourActive(8))
        assertTrue("Hour 14 (afternoon) should be active", chime.isHourActive(14))
        assertTrue("Hour 22 (end) should be active", chime.isHourActive(22))
        assertFalse("Hour 7 (morning before start) should be inactive", chime.isHourActive(7))
        assertFalse("Hour 23 (night after end) should be inactive", chime.isHourActive(23))
        assertFalse("Hour 2 (midnight) should be inactive", chime.isHourActive(2))
    }

    @Test
    fun testHourlyChimeOvernightWindow() {
        val chime = HourlyChimeConfig(
            isEnabled = true,
            startHour = 22,
            endHour = 4,
            pattern = AirHapticPattern.GENTLE
        )

        assertTrue("Hour 22 should be active", chime.isHourActive(22))
        assertTrue("Hour 23 should be active", chime.isHourActive(23))
        assertTrue("Hour 0 should be active", chime.isHourActive(0))
        assertTrue("Hour 2 should be active", chime.isHourActive(2))
        assertTrue("Hour 4 should be active", chime.isHourActive(4))
        assertFalse("Hour 12 should be inactive", chime.isHourActive(12))
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
