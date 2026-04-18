package com.trust3.xcpro.adsb

import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.units.VerticalSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbDetailsFormatterTest {

    @Test
    fun formatVerticalRate_whenMissing_returnsPlaceholder() {
        val formatted = AdsbDetailsFormatter.formatVerticalRate(
            climbMps = null,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals("--", formatted)
    }

    @Test
    fun formatVerticalRate_feetPerMinute_usesIntegerWithFtPerMinSuffix() {
        val units = UnitsPreferences(verticalSpeed = VerticalSpeedUnit.FEET_PER_MINUTE)

        val formatted = AdsbDetailsFormatter.formatVerticalRate(
            climbMps = 2.0,
            unitsPreferences = units
        )

        assertEquals("+394 ft/min", formatted)
    }

    @Test
    fun formatVerticalRate_metersPerSecond_keepsOneDecimal() {
        val units = UnitsPreferences(verticalSpeed = VerticalSpeedUnit.METERS_PER_SECOND)

        val formatted = AdsbDetailsFormatter.formatVerticalRate(
            climbMps = 2.0,
            unitsPreferences = units
        )

        assertEquals("+2.0 m/s", formatted)
    }
}
