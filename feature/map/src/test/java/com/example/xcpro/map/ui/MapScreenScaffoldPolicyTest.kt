package com.example.xcpro.map.ui

import com.example.xcpro.navigation.SettingsRoutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapScreenScaffoldPolicyTest {

    @Test
    fun shouldNavigateToWeatherSettings_returnsFalseWhenAlreadyOnWeatherRoute() {
        assertFalse(shouldNavigateToWeatherSettings(SettingsRoutes.WEATHER_SETTINGS))
    }

    @Test
    fun shouldNavigateToWeatherSettings_returnsTrueForOtherRoutesAndNull() {
        assertTrue(shouldNavigateToWeatherSettings("map"))
        assertTrue(shouldNavigateToWeatherSettings(SettingsRoutes.GENERAL))
        assertTrue(shouldNavigateToWeatherSettings(null))
    }
}
