package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbProximityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbProximityIconOutlinePolicyTest {

    @Test
    fun haloWidthFor_returnsOutlineWidthForGreenAmberAndRed() {
        val green = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.GREEN)
        val amber = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.AMBER)
        val red = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.RED)

        assertEquals(0.8f, green, 0f)
        assertEquals(0.8f, amber, 0f)
        assertEquals(0.8f, red, 0f)
    }

    @Test
    fun haloWidthFor_returnsNoOutlineForNeutralAndEmergency() {
        val neutral = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.NEUTRAL)
        val emergency = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.EMERGENCY)

        assertEquals(0f, neutral, 0f)
        assertEquals(0f, emergency, 0f)
    }
}
