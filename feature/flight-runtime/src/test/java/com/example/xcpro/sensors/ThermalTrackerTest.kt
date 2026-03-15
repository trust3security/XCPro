package com.example.xcpro.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalTrackerTest {

    @Test
    fun shortThermalDoesNotQualify() {
        val tracker = ThermalTracker()

        update(tracker, 10_000L, 100.0, isCircling = false, isTurning = true)
        update(tracker, 20_000L, 120.0, isCircling = true, isTurning = true)
        update(tracker, 30_000L, 130.0, isCircling = true, isTurning = false)
        update(tracker, 40_000L, 131.0, isCircling = false, isTurning = false)

        assertFalse(tracker.currentThermalValid)
    }

    @Test
    fun qualifyingThermalBecomesLastThermal() {
        val tracker = ThermalTracker()

        update(tracker, 10_000L, 100.0, isCircling = false, isTurning = true)
        update(tracker, 25_000L, 120.0, isCircling = true, isTurning = true)
        update(tracker, 70_000L, 160.0, isCircling = true, isTurning = false)
        update(tracker, 80_000L, 160.0, isCircling = false, isTurning = false)

        assertTrue(tracker.currentThermalValid)
        assertEquals(1.0, tracker.currentThermalLiftRate, 1e-3)
    }

    @Test
    fun shortThermalDoesNotOverwriteValidLastThermal() {
        val tracker = ThermalTracker()

        update(tracker, 10_000L, 100.0, isCircling = false, isTurning = true)
        update(tracker, 25_000L, 120.0, isCircling = true, isTurning = true)
        update(tracker, 70_000L, 160.0, isCircling = true, isTurning = false)
        update(tracker, 80_000L, 160.0, isCircling = false, isTurning = false)

        val baselineLift = tracker.currentThermalLiftRate

        update(tracker, 90_000L, 160.0, isCircling = false, isTurning = true)
        update(tracker, 100_000L, 150.0, isCircling = true, isTurning = true)
        update(tracker, 110_000L, 145.0, isCircling = true, isTurning = false)
        update(tracker, 120_000L, 145.0, isCircling = false, isTurning = false)

        assertTrue(tracker.currentThermalValid)
        assertEquals(baselineLift, tracker.currentThermalLiftRate, 1e-3)
    }

    private fun update(
        tracker: ThermalTracker,
        timestampMillis: Long,
        teAltitudeMeters: Double,
        isCircling: Boolean,
        isTurning: Boolean
    ) {
        tracker.update(
            timestampMillis = timestampMillis,
            teAltitudeMeters = teAltitudeMeters,
            verticalSpeedMs = 0.0,
            isCircling = isCircling,
            isTurning = isTurning
        )
    }
}
