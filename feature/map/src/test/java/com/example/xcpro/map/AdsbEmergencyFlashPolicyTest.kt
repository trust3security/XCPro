package com.example.xcpro.map

import com.example.xcpro.map.AdsbProximityTier
import com.example.xcpro.map.AdsbTrafficUiModel
import com.example.xcpro.map.Icao24
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbEmergencyFlashPolicyTest {

    @Test
    fun alphaForTarget_returnsLiveAlpha_forNonEmergencyLiveTarget() {
        val alpha = AdsbEmergencyFlashPolicy.alphaForTarget(
            target = sampleTarget(proximityTier = AdsbProximityTier.AMBER, isStale = false),
            nowMonoMs = 100L,
            liveAlpha = 0.90,
            staleAlpha = 0.45
        )

        assertEquals(0.90, alpha, 1e-6)
    }

    @Test
    fun alphaForTarget_returnsStaleAlpha_forStaleEmergencyTarget() {
        val alpha = AdsbEmergencyFlashPolicy.alphaForTarget(
            target = sampleTarget(proximityTier = AdsbProximityTier.EMERGENCY, isStale = true),
            nowMonoMs = 100L,
            liveAlpha = 0.90,
            staleAlpha = 0.45
        )

        assertEquals(0.45, alpha, 1e-6)
    }

    @Test
    fun alphaForTarget_pulses_forEmergencyLiveTarget() {
        val target = sampleTarget(proximityTier = AdsbProximityTier.EMERGENCY, isStale = false)
        val atStart = AdsbEmergencyFlashPolicy.alphaForTarget(
            target = target,
            nowMonoMs = 0L,
            liveAlpha = 0.90,
            staleAlpha = 0.45
        )
        val atHalfPeriod = AdsbEmergencyFlashPolicy.alphaForTarget(
            target = target,
            nowMonoMs = 400L,
            liveAlpha = 0.90,
            staleAlpha = 0.45
        )

        assertTrue(atStart in 0.35..0.90)
        assertTrue(atHalfPeriod in 0.35..0.90)
        assertNotEquals(atStart, atHalfPeriod)
    }

    @Test
    fun alphaForTarget_returnsLiveAlpha_whenEmergencyFlashDisabled() {
        val alpha = AdsbEmergencyFlashPolicy.alphaForTarget(
            target = sampleTarget(proximityTier = AdsbProximityTier.EMERGENCY, isStale = false),
            nowMonoMs = 200L,
            liveAlpha = 0.90,
            staleAlpha = 0.45,
            emergencyFlashEnabled = false
        )

        assertEquals(0.90, alpha, 1e-6)
    }

    @Test
    fun emergencyPulseAlpha_isDeterministic_forSameInput() {
        val first = AdsbEmergencyFlashPolicy.emergencyPulseAlpha(
            nowMonoMs = 1_234L,
            maxAlpha = 0.90
        )
        val second = AdsbEmergencyFlashPolicy.emergencyPulseAlpha(
            nowMonoMs = 1_234L,
            maxAlpha = 0.90
        )

        assertEquals(first, second, 1e-9)
    }

    private fun sampleTarget(
        proximityTier: AdsbProximityTier,
        isStale: Boolean
    ): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = Icao24.from("abc123") ?: error("invalid id"),
        callsign = "ABC123",
        lat = -33.8688,
        lon = 151.2093,
        altitudeM = 1_000.0,
        speedMps = 30.0,
        trackDeg = 180.0,
        climbMps = 0.5,
        ageSec = if (isStale) 120 else 1,
        isStale = isStale,
        distanceMeters = 900.0,
        bearingDegFromUser = 90.0,
        usesOwnshipReference = true,
        positionSource = 0,
        category = 2,
        lastContactEpochSec = 1_710_000_000L,
        proximityTier = proximityTier
    )
}
