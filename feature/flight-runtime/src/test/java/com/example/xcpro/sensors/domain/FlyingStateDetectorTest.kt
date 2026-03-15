package com.example.xcpro.sensors.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlyingStateDetectorTest {

    @Test
    fun `detects takeoff after sustained speed`() {
        val detector = FlyingStateDetector()
        var timeMs = 0L
        var state = detector.update(
            timestampMillis = timeMs,
            groundSpeedMs = 0.0,
            trueAirspeedMs = null,
            airspeedReal = false,
            altitudeMeters = 100.0,
            aglMeters = null
        )
        assertFalse(state.isFlying)

        repeat(10) {
            timeMs += 1_000L
            state = detector.update(
                timestampMillis = timeMs,
                groundSpeedMs = 12.0,
                trueAirspeedMs = null,
                airspeedReal = false,
                altitudeMeters = 100.0,
                aglMeters = null
            )
        }

        assertTrue(state.isFlying)
    }

    @Test
    fun `detects landing after sustained stationary`() {
        val detector = FlyingStateDetector()
        var timeMs = 0L

        // Takeoff phase
        detector.update(
            timestampMillis = timeMs,
            groundSpeedMs = 12.0,
            trueAirspeedMs = null,
            airspeedReal = false,
            altitudeMeters = 100.0,
            aglMeters = null
        )
        repeat(10) {
            timeMs += 1_000L
            detector.update(
                timestampMillis = timeMs,
                groundSpeedMs = 12.0,
                trueAirspeedMs = null,
                airspeedReal = false,
                altitudeMeters = 100.0,
                aglMeters = null
            )
        }

        // Stationary phase
        var state = detector.update(
            timestampMillis = timeMs,
            groundSpeedMs = 0.0,
            trueAirspeedMs = null,
            airspeedReal = false,
            altitudeMeters = 100.0,
            aglMeters = null
        )
        assertTrue(state.isFlying)

        repeat(35) {
            timeMs += 1_000L
            state = detector.update(
                timestampMillis = timeMs,
                groundSpeedMs = 0.0,
                trueAirspeedMs = null,
                airspeedReal = false,
                altitudeMeters = 100.0,
                aglMeters = null
            )
        }

        assertFalse(state.isFlying)
    }

    @Test
    fun `agl override allows takeoff with low ground speed`() {
        val detector = FlyingStateDetector()
        var timeMs = 0L
        var state = detector.update(
            timestampMillis = timeMs,
            groundSpeedMs = 1.0,
            trueAirspeedMs = null,
            airspeedReal = false,
            altitudeMeters = 400.0,
            aglMeters = 320.0
        )
        assertFalse(state.isFlying)

        repeat(10) {
            timeMs += 1_000L
            state = detector.update(
                timestampMillis = timeMs,
                groundSpeedMs = 1.0,
                trueAirspeedMs = null,
                airspeedReal = false,
                altitudeMeters = 400.0,
                aglMeters = 320.0
            )
        }

        assertTrue(state.isFlying)
    }
}
