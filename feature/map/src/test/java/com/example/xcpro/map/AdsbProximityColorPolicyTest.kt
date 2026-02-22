package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbProximityColorPolicyTest {

    @Test
    fun colorHexFor_returnsEmergencyColorWhenEmergencyFlagSet() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 10_000.0,
            hasOwnshipReference = false,
            isEmergency = true
        )

        assertEquals(AdsbProximityColorPolicy.EMERGENCY_HEX, color)
    }

    @Test
    fun colorHexFor_returnsNeutralWhenOwnshipReferenceUnavailable() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 1_000.0,
            hasOwnshipReference = false,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.NEUTRAL_HEX, color)
    }

    @Test
    fun colorHexFor_returnsRedWithinTwoKilometers() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 1_999.0,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.RED_HEX, color)
    }

    @Test
    fun colorHexFor_returnsAmberBetweenTwoAndFiveKilometers() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 3_500.0,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.AMBER_HEX, color)
    }

    @Test
    fun colorHexFor_returnsGreenBeyondFiveKilometers() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 5_001.0,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.GREEN_HEX, color)
    }
}
