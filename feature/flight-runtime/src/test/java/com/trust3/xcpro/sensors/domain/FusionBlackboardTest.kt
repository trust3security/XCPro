package com.trust3.xcpro.sensors.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionBlackboardTest {

    @Test
    fun averages_reset_when_thermal_toggles() {
        val bb = FusionBlackboard()

        // steady climb samples with thermal inactive
        var outputs = bb.updateAveragesAndDisplay(
            currentTime = 0L,
            tc30TimeMillis = 0L,
            bruttoSample = 2.0,
            nettoSample = 1.5,
            thermalActive = false,
            nettoValue = 1.5,
            nettoValid = true
        )
        assertEquals(2.0, outputs.bruttoAverage30s, 0.01)

        outputs = bb.updateAveragesAndDisplay(
            currentTime = 1_000L,
            tc30TimeMillis = 1_000L,
            bruttoSample = 2.0,
            nettoSample = 1.5,
            thermalActive = false,
            nettoValue = 1.5,
            nettoValid = true
        )
        assertTrue(outputs.bruttoAverage30s in 1.9..2.1)

        // toggle circling flag -> windows should reset to new value, not mix with old
        outputs = bb.updateAveragesAndDisplay(
            currentTime = 2_000L,
            tc30TimeMillis = 2_000L,
            bruttoSample = 5.0,
            nettoSample = 4.0,
            thermalActive = true,
            nettoValue = 4.0,
            nettoValid = true
        )
        assertEquals(5.0, outputs.bruttoAverage30s, 0.01)
        assertEquals(4.0, outputs.nettoAverage30s, 0.01)
    }

    @Test
    fun netto_fallback_uses_last_valid_value() {
        val bb = FusionBlackboard()

        // First valid netto stores the fallback
        bb.updateAveragesAndDisplay(
            currentTime = 0L,
            tc30TimeMillis = 0L,
            bruttoSample = 0.0,
            nettoSample = 1.0,
            thermalActive = false,
            nettoValue = 1.0,
            nettoValid = true
        )
        val resolved = bb.resolveNettoSampleValue(rawNetto = Double.NaN, nettoValid = false)
        assertEquals(1.0, resolved, 0.001)
    }

    @Test
    fun netto_average_validity_requires_a_recent_valid_sample_in_window() {
        val bb = FusionBlackboard()

        var outputs = bb.updateAveragesAndDisplay(
            currentTime = 0L,
            tc30TimeMillis = 0L,
            bruttoSample = 0.0,
            nettoSample = 1.0,
            thermalActive = false,
            nettoValue = 1.0,
            nettoValid = true
        )
        assertTrue(outputs.nettoAverage30sValid)

        outputs = bb.updateAveragesAndDisplay(
            currentTime = 31_000L,
            tc30TimeMillis = 31_000L,
            bruttoSample = 0.0,
            nettoSample = 0.0,
            thermalActive = false,
            nettoValue = 0.0,
            nettoValid = false
        )
        assertFalse(outputs.nettoAverage30sValid)
    }

    @Test
    fun airspeed_hold_returns_last_within_window() {
        val bb = FusionBlackboard()
        val now = 0L
        val estimate = AirspeedEstimate(indicatedMs = 12.0, trueMs = 20.0, source = AirspeedSource.WIND_VECTOR)

        // Remember a valid estimate
        val first = bb.resolveAirspeedHold(estimate, now)
        assertEquals(estimate, first)

        // Within hold window with no new estimate -> returns cached
        val held = bb.resolveAirspeedHold(airspeedEstimate = null, now = FlightMetricsConstants.SPEED_HOLD_MS - 500)
        assertEquals(estimate, held)

        // Beyond hold window -> clears
        val expired = bb.resolveAirspeedHold(airspeedEstimate = null, now = FlightMetricsConstants.SPEED_HOLD_MS + 1_000)
        assertNull(expired)
    }

    @Test
    fun airspeed_hold_rejects_negative_age_when_time_goes_backwards() {
        val bb = FusionBlackboard()
        val estimate = AirspeedEstimate(indicatedMs = 12.0, trueMs = 20.0, source = AirspeedSource.WIND_VECTOR)
        bb.resolveAirspeedHold(estimate, now = 5_000L)

        val backwards = bb.resolveAirspeedHold(airspeedEstimate = null, now = 4_000L)
        assertNull(backwards)
    }
}
