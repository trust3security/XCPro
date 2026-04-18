package com.trust3.xcpro.map

import com.trust3.xcpro.map.AdsbProximityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbProximityIconOutlinePolicyTest {

    @Test
    fun haloWidthFor_returnsOutlineWidthForAllProximityTiers() {
        val green = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.GREEN)
        val amber = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.AMBER)
        val red = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.RED)
        val neutral = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.NEUTRAL)
        val emergency = AdsbProximityIconOutlinePolicy.haloWidthFor(AdsbProximityTier.EMERGENCY)

        assertEquals(0.8f, green, 0f)
        assertEquals(0.8f, amber, 0f)
        assertEquals(0.8f, red, 0f)
        assertEquals(0.8f, neutral, 0f)
        assertEquals(0.8f, emergency, 0f)
    }
}
