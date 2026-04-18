package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnDisplayUpdateModeTest {

    @Test
    fun fromStorage_unknownFallsBackToDefault() {
        assertEquals(
            OgnDisplayUpdateMode.DEFAULT,
            OgnDisplayUpdateMode.fromStorage("unknown_mode")
        )
    }

    @Test
    fun sliderIndex_roundTripsForAllModes() {
        OgnDisplayUpdateMode.sliderModes.forEach { mode ->
            val index = OgnDisplayUpdateMode.toSliderIndex(mode)
            assertEquals(mode, OgnDisplayUpdateMode.fromSliderIndex(index))
        }
    }

    @Test
    fun fromSliderIndex_outOfRangeFallsBackToDefault() {
        assertEquals(
            OgnDisplayUpdateMode.DEFAULT,
            OgnDisplayUpdateMode.fromSliderIndex(99)
        )
    }
}
