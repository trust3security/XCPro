package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.PressureUnit
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.convertQnhInputToHpa
import com.trust3.xcpro.formatBaroGpsDelta
import com.trust3.xcpro.formatQnhDisplay
import com.trust3.xcpro.seedQnhInputValue
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
