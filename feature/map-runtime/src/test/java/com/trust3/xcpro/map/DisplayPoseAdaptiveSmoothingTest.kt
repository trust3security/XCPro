package com.trust3.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPoseAdaptiveSmoothingTest {

    @Test
    fun returnsBaseConfigWhenInputsInvalid() {
        val base = DisplayPoseSmoothingConfig()
        val invalidSpeed = Double.NaN
        val invalidAccuracy = Double.NaN

        val result = DisplayPoseAdaptiveSmoothing.effectiveConfig(base, invalidSpeed, invalidAccuracy)
        assertEquals(base, result)
    }

    @Test
    fun reducesSmoothingAtHighSpeedGoodAccuracy() {
        val base = DisplayPoseSmoothingConfig()
        val result = DisplayPoseAdaptiveSmoothing.effectiveConfig(
            base = base,
            speedMs = 60.0,
            accuracyM = 3.0
        )

        assertTrue(result.posSmoothMs < base.posSmoothMs)
        assertTrue(result.headingSmoothMs < base.headingSmoothMs)
        assertEquals(base.deadReckonLimitMs, result.deadReckonLimitMs)
    }

    @Test
    fun increasesSmoothingAndReducesPredictionWhenAccuracyPoor() {
        val base = DisplayPoseSmoothingConfig()
        val result = DisplayPoseAdaptiveSmoothing.effectiveConfig(
            base = base,
            speedMs = 0.0,
            accuracyM = 20.0
        )

        assertTrue(result.posSmoothMs > base.posSmoothMs)
        assertTrue(result.headingSmoothMs > base.headingSmoothMs)
        assertTrue(result.deadReckonLimitMs < base.deadReckonLimitMs)
    }

    @Test
    fun preservesExplicitFrameActiveWindow() {
        val base = DisplaySmoothingProfile.CADENCE_BRIDGE.config
        val result = DisplayPoseAdaptiveSmoothing.effectiveConfig(
            base = base,
            speedMs = 60.0,
            accuracyM = 3.0
        )

        assertEquals(base.frameActiveWindowMs, result.frameActiveWindowMs)
    }
}
