package com.example.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Test

class OgnThermalColorScaleTest {

    @Test
    fun climbRateColorIndex_clampsAtPlusMinus30Kt() {
        val maxAbsMps = 30.0 * 0.514444

        assertEquals(0, climbRateToSnailColorIndex(-maxAbsMps))
        assertEquals(0, climbRateToSnailColorIndex(-maxAbsMps - 5.0))
        assertEquals(OGN_THERMAL_SNAIL_COLOR_COUNT - 1, climbRateToSnailColorIndex(maxAbsMps))
        assertEquals(OGN_THERMAL_SNAIL_COLOR_COUNT - 1, climbRateToSnailColorIndex(maxAbsMps + 5.0))
    }

    @Test
    fun climbRateColorIndex_zeroAndNonFiniteMapToCenter() {
        val center = OGN_THERMAL_SNAIL_COLOR_COUNT / 2
        assertEquals(center, climbRateToSnailColorIndex(0.0))
        assertEquals(center, climbRateToSnailColorIndex(Double.NaN))
        assertEquals(center, climbRateToSnailColorIndex(Double.POSITIVE_INFINITY))
    }
}
