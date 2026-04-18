package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnGliderTrailMathTest {

    @Test
    fun widthMapping_sinkGetsThinnerAsSinkStrengthIncreases() {
        val weakSink = ognTrailWidthPx(-0.5)
        val mediumSink = ognTrailWidthPx(-2.0)
        val strongSink = ognTrailWidthPx(-5.0)

        assertTrue(weakSink > mediumSink)
        assertTrue(mediumSink > strongSink)
    }

    @Test
    fun widthMapping_climbGetsThickerAsClimbStrengthIncreases() {
        val weakClimb = ognTrailWidthPx(0.5)
        val mediumClimb = ognTrailWidthPx(2.0)
        val strongClimb = ognTrailWidthPx(5.0)

        assertTrue(weakClimb < mediumClimb)
        assertTrue(mediumClimb < strongClimb)
    }

    @Test
    fun widthMapping_zeroLiftUsesConfiguredBaseline() {
        val zero = ognTrailWidthPx(0.0)

        assertEquals(OGN_TRAIL_ZERO_WIDTH_PX, zero, 1e-6f)
    }

    @Test
    fun widthMapping_clampsAtPlusMinus30KtBounds() {
        val maxAbsMps = 30.0 * 0.514444
        val strongestSink = ognTrailWidthPx(-maxAbsMps - 5.0)
        val strongestClimb = ognTrailWidthPx(maxAbsMps + 5.0)

        assertEquals(OGN_TRAIL_SINK_MIN_WIDTH_PX, strongestSink, 1e-6f)
        assertEquals(OGN_TRAIL_CLIMB_MAX_WIDTH_PX, strongestClimb, 1e-6f)
    }

    @Test
    fun colorMapping_clampsToSnailPaletteBounds() {
        val sinkIndex = ognTrailColorIndex(-99.0)
        val climbIndex = ognTrailColorIndex(99.0)

        assertEquals(0, sinkIndex)
        assertEquals(OGN_THERMAL_SNAIL_COLOR_COUNT - 1, climbIndex)
    }
}
