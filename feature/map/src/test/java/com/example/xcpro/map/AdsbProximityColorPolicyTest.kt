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
    fun colorHexFor_returnsRedAtAndBelowTwoKilometers() {
        val belowThreshold = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 1_999.0,
            hasOwnshipReference = true,
            isEmergency = false
        )
        val atThreshold = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 2_000.0,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.RED_HEX, belowThreshold)
        assertEquals(AdsbProximityColorPolicy.RED_HEX, atThreshold)
    }

    @Test
    fun colorHexFor_returnsAmberAboveTwoKilometersThroughFiveKilometers() {
        val aboveRedThreshold = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 2_001.0,
            hasOwnshipReference = true,
            isEmergency = false
        )
        val atAmberThreshold = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = 5_000.0,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.AMBER_HEX, aboveRedThreshold)
        assertEquals(AdsbProximityColorPolicy.AMBER_HEX, atAmberThreshold)
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

    @Test
    fun colorHexFor_returnsGreenWhenDistanceMissingButOwnshipReferenceExists() {
        val color = AdsbProximityColorPolicy.colorHexFor(
            distanceMeters = Double.NaN,
            hasOwnshipReference = true,
            isEmergency = false
        )

        assertEquals(AdsbProximityColorPolicy.GREEN_HEX, color)
    }
}
