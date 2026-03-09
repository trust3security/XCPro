package com.example.xcpro.map

import com.example.xcpro.map.AdsbProximityTier
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbProximityColorPolicyTest {

    @Test
    fun colorHexFor_returnsEmergencyColorForEmergencyTier() {
        val color = AdsbProximityColorPolicy.colorHexFor(AdsbProximityTier.EMERGENCY)

        assertEquals(AdsbProximityColorPolicy.EMERGENCY_HEX, color)
    }

    @Test
    fun colorHexFor_returnsNeutralForNeutralTier() {
        val color = AdsbProximityColorPolicy.colorHexFor(AdsbProximityTier.NEUTRAL)

        assertEquals(AdsbProximityColorPolicy.NEUTRAL_HEX, color)
    }

    @Test
    fun colorHexFor_returnsRedForRedTier() {
        val color = AdsbProximityColorPolicy.colorHexFor(AdsbProximityTier.RED)

        assertEquals(AdsbProximityColorPolicy.RED_HEX, color)
    }

    @Test
    fun colorHexFor_returnsAmberForAmberTier() {
        val color = AdsbProximityColorPolicy.colorHexFor(AdsbProximityTier.AMBER)

        assertEquals(AdsbProximityColorPolicy.AMBER_HEX, color)
    }

    @Test
    fun colorHexFor_returnsGreenForGreenTier() {
        val color = AdsbProximityColorPolicy.colorHexFor(AdsbProximityTier.GREEN)

        assertEquals(AdsbProximityColorPolicy.GREEN_HEX, color)
    }
}
