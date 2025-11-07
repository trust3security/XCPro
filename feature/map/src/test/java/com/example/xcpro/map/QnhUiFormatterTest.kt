package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.PressureUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.convertQnhInputToHpa
import com.example.xcpro.formatBaroGpsDelta
import com.example.xcpro.formatQnhDisplay
import com.example.xcpro.seedQnhInputValue
import org.junit.Assert.assertEquals
import org.junit.Test

class QnhUiFormatterTest {

    @Test
    fun seedQnhInputValue_usesPreferredPressureUnits() {
        val defaultPrefs = UnitsPreferences()
        val inHgPrefs = UnitsPreferences(pressure = PressureUnit.INHG)

        assertEquals("1013.3", seedQnhInputValue(1013.25, defaultPrefs))
        assertEquals("29.92", seedQnhInputValue(1013.25, inHgPrefs))
    }

    @Test
    fun formatQnhDisplay_matchesUnitsFormatterOutput() {
        val prefs = UnitsPreferences(pressure = PressureUnit.INHG)
        assertEquals("29.92 inHg", formatQnhDisplay(1013.25, prefs))
    }

    @Test
    fun convertQnhInputToHpa_convertsBackToSi() {
        val prefs = UnitsPreferences(pressure = PressureUnit.INHG)
        val converted = convertQnhInputToHpa(29.92, prefs)
        assertEquals(1013.25, converted, 0.05)
    }

    @Test
    fun formatBaroGpsDelta_respectsAltitudeUnits() {
        val prefs = UnitsPreferences(altitude = AltitudeUnit.FEET)
        assertEquals("+50 ft", formatBaroGpsDelta(15.24, prefs))
        val metricPrefs = UnitsPreferences(altitude = AltitudeUnit.METERS)
        assertEquals("+15 m", formatBaroGpsDelta(15.24, metricPrefs))
    }
}
