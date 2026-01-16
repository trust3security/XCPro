package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconHeadingSmootherTest {

    @Test
    fun heading_valid_gate_and_fallback() {
        val config = IconRotationConfig(
            enterSpeedMs = 2.0,
            exitSpeedMs = 1.0,
            maxAngularVelocityDegPerSec = 3600.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        val out1 = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 45.0,
                headingValid = true,
                trackDeg = 90.0,
                mapBearing = 90.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 2.5,
                nowMs = 1000L
            )
        )
        assertEquals(45.0, out1, 1e-6)

        val out2 = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 80.0,
                headingValid = false,
                trackDeg = 90.0,
                mapBearing = 90.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 0.5,
                nowMs = 1100L
            )
        )
        assertEquals(90.0, out2, 1e-6)
    }

    @Test
    fun heading_hysteresis_keeps_mode_active_between_thresholds() {
        val config = IconRotationConfig(
            enterSpeedMs = 2.0,
            exitSpeedMs = 1.0,
            maxAngularVelocityDegPerSec = 3600.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 30.0,
                headingValid = true,
                trackDeg = 120.0,
                mapBearing = 120.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 2.5,
                nowMs = 1000L
            )
        )

        val out = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 60.0,
                headingValid = true,
                trackDeg = 120.0,
                mapBearing = 120.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 1.5,
                nowMs = 1100L
            )
        )

        assertEquals(60.0, out, 1e-6)
    }

    @Test
    fun rotation_rate_is_clamped() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 5.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 350.0,
                headingValid = true,
                trackDeg = 350.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )

        val out = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 10.0,
                headingValid = true,
                trackDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        assertEquals(355.0, out, 1e-6)
    }

    @Test
    fun deadband_ignores_small_rotation() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 3600.0,
            deadbandDeg = 2.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 10.0,
                headingValid = true,
                trackDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 2.0,
                nowMs = 0L
            )
        )

        val out = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 11.0,
                headingValid = true,
                trackDeg = 11.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 2.0,
                nowMs = 100L
            )
        )

        assertEquals(10.0, out, 1e-6)
    }

    @Test
    fun orientation_mode_change_snaps_to_target() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 1.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 45.0,
                headingValid = true,
                trackDeg = 90.0,
                mapBearing = 90.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        val out = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 45.0,
                headingValid = false,
                trackDeg = 90.0,
                mapBearing = 90.0,
                orientationMode = MapOrientationMode.HEADING_UP,
                speedMs = 0.0,
                nowMs = 1100L
            )
        )

        assertEquals(90.0, out, 1e-6)
    }

    @Test
    fun north_up_prefers_track_when_speed_is_stable() {
        val config = IconRotationConfig(
            enterSpeedMs = 2.0,
            exitSpeedMs = 1.0,
            maxAngularVelocityDegPerSec = 3600.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L
        )
        val smoother = IconHeadingSmoother(config)

        smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 40.0,
                headingValid = true,
                trackDeg = 100.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 3.0,
                nowMs = 1000L
            )
        )

        val out = smoother.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 80.0,
                headingValid = false,
                trackDeg = 120.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.NORTH_UP,
                speedMs = 3.0,
                nowMs = 1100L
            )
        )

        assertEquals(120.0, out, 1e-6)
    }

    @Test
    fun config_zero_min_speed_disables_hysteresis() {
        val config = IconRotationConfig.fromMinSpeedThreshold(0.0)
        assertEquals(0.0, config.enterSpeedMs, 1e-6)
        assertEquals(0.0, config.exitSpeedMs, 1e-6)
    }

    @Test
    fun deadband_grows_with_bearing_accuracy_when_using_track() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 3600.0,
            deadbandDeg = 1.0,
            maxDtMs = 1000L,
            accuracyDeadbandScaleDegPerDeg = 0.1,
            accuracyMaxTurnScale = 100.0
        )

        val good = IconHeadingSmoother(config)
        good.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 0.0,
                bearingAccuracyDeg = 1.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val goodOut = good.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 1.4,
                bearingAccuracyDeg = 1.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 100L
            )
        )

        val poor = IconHeadingSmoother(config)
        poor.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 0.0,
                bearingAccuracyDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val poorOut = poor.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 1.4,
                bearingAccuracyDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 100L
            )
        )

        assertTrue(goodOut > 0.5)
        assertEquals(0.0, poorOut, 1e-6)
    }

    @Test
    fun max_turn_rate_decreases_with_bearing_accuracy_when_using_track() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 10.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L,
            accuracyDeadbandScaleDegPerDeg = 0.0,
            accuracyMaxTurnScale = 5.0
        )

        val good = IconHeadingSmoother(config)
        good.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 0.0,
                bearingAccuracyDeg = 0.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val goodOut = good.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 90.0,
                bearingAccuracyDeg = 0.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        val poor = IconHeadingSmoother(config)
        poor.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 0.0,
                bearingAccuracyDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val poorOut = poor.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = false,
                trackDeg = 90.0,
                bearingAccuracyDeg = 10.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        assertEquals(10.0, goodOut, 1e-6)
        assertTrue(poorOut < goodOut)
    }

    @Test
    fun heading_valid_ignores_bearing_accuracy() {
        val config = IconRotationConfig(
            enterSpeedMs = 0.0,
            exitSpeedMs = 0.0,
            maxAngularVelocityDegPerSec = 10.0,
            deadbandDeg = 0.0,
            maxDtMs = 1000L,
            accuracyDeadbandScaleDegPerDeg = 0.2,
            accuracyMaxTurnScale = 1.0
        )

        val baseline = IconHeadingSmoother(config)
        baseline.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = true,
                trackDeg = 0.0,
                bearingAccuracyDeg = 1.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val baseOut = baseline.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 90.0,
                headingValid = true,
                trackDeg = 90.0,
                bearingAccuracyDeg = 1.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        val noisy = IconHeadingSmoother(config)
        noisy.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 0.0,
                headingValid = true,
                trackDeg = 0.0,
                bearingAccuracyDeg = 30.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 0L
            )
        )
        val noisyOut = noisy.update(
            IconHeadingSmoother.IconHeadingInput(
                headingDeg = 90.0,
                headingValid = true,
                trackDeg = 90.0,
                bearingAccuracyDeg = 30.0,
                mapBearing = 0.0,
                orientationMode = MapOrientationMode.TRACK_UP,
                speedMs = 5.0,
                nowMs = 1000L
            )
        )

        assertEquals(baseOut, noisyOut, 1e-6)
    }
}
