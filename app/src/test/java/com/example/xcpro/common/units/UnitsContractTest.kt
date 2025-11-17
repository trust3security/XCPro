package com.example.xcpro.common.units

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnitsContractTest {
    @Test
    fun altitudeRoundTrip() {
        val si = AltitudeM(1234.5)
        val display = AltitudeUnit.FEET.fromSi(si)
        val back = AltitudeUnit.FEET.toSi(display)
        assertEquals(si.value, back.value, 1e-3)
    }

    @Test
    fun verticalSpeedRoundTrip() {
        val si = VerticalSpeedMs(-2.7)
        val fpm = VerticalSpeedUnit.FEET_PER_MINUTE.fromSi(si)
        val back = VerticalSpeedUnit.FEET_PER_MINUTE.toSi(fpm)
        assertEquals(si.value, back.value, 1e-3)
    }

    @Test
    fun speedRoundTrip() {
        val si = SpeedMs(42.0)
        val knots = SpeedUnit.KNOTS.fromSi(si)
        val back = SpeedUnit.KNOTS.toSi(knots)
        assertEquals(si.value, back.value, 1e-6)
    }

    @Test
    fun distanceRoundTrip() {
        val si = DistanceM(15000.0)
        val miles = DistanceUnit.STATUTE_MILES.fromSi(si)
        val back = DistanceUnit.STATUTE_MILES.toSi(miles)
        assertEquals(si.value, back.value, 1e-6)
    }

    @Test
    fun pressureRoundTrip() {
        val si = PressureHpa(1005.3)
        val inhg = PressureUnit.INHG.fromSi(si)
        val back = PressureUnit.INHG.toSi(inhg)
        assertEquals(si.value, back.value, 1e-6)
    }

    @Test
    fun temperatureRoundTrip() {
        val si = TemperatureC(-12.4)
        val fahrenheit = TemperatureUnit.FAHRENHEIT.fromSi(si)
        val back = TemperatureUnit.FAHRENHEIT.toSi(fahrenheit)
        assertEquals(si.value, back.value, 1e-6)
    }

    @Test
    fun defaultPreferencesAreSi() {
        val prefs = UnitsPreferences()
        assertTrue(prefs.altitude == AltitudeUnit.METERS)
        assertTrue(prefs.verticalSpeed == VerticalSpeedUnit.METERS_PER_SECOND)
        assertTrue(prefs.speed == SpeedUnit.KILOMETERS_PER_HOUR)
        assertTrue(prefs.distance == DistanceUnit.KILOMETERS)
        assertTrue(prefs.pressure == PressureUnit.HECTOPASCAL)
        assertTrue(prefs.temperature == TemperatureUnit.CELSIUS)
    }
}

