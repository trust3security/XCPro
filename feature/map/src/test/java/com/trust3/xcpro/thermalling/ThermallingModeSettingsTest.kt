package com.trust3.xcpro.thermalling

import org.junit.Assert.assertEquals
import org.junit.Test

class ThermallingModeSettingsTest {

    @Test
    fun clampThermallingDelaySeconds_respectsBounds() {
        assertEquals(THERMALLING_DELAY_MIN_SECONDS, clampThermallingDelaySeconds(-99))
        assertEquals(THERMALLING_DELAY_MAX_SECONDS, clampThermallingDelaySeconds(999))
        assertEquals(12, clampThermallingDelaySeconds(12))
    }

    @Test
    fun clampThermallingZoomLevel_respectsBounds() {
        assertEquals(THERMALLING_ZOOM_LEVEL_MIN, clampThermallingZoomLevel(1.0f), 0.0001f)
        assertEquals(THERMALLING_ZOOM_LEVEL_MAX, clampThermallingZoomLevel(99.0f), 0.0001f)
        assertEquals(13.7f, clampThermallingZoomLevel(13.7f), 0.0001f)
    }
}
