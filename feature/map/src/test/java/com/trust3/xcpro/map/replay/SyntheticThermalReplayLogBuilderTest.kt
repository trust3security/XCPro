package com.trust3.xcpro.map.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticThermalReplayLogBuilderTest {

    @Test
    fun build_cleanVariantProducesMonotonicClimbingNorthDriftingLog() {
        val builder = SyntheticThermalReplayLogBuilder()

        val log = builder.build(SyntheticThermalReplayVariant.CLEAN)

        assertEquals(601, log.points.size)
        assertEquals(1735689600000L, log.points.first().timestampMillis)
        assertEquals(1735690200000L, log.points.last().timestampMillis)
        assertEquals(304.8, log.points.first().gpsAltitude, 1e-6)
        assertEquals(1828.8, log.points.last().gpsAltitude, 1e-6)
        assertTrue(log.points.zipWithNext().all { (previous, current) ->
            current.timestampMillis > previous.timestampMillis
        })
        assertTrue(log.points.last().latitude > log.points.first().latitude)
        assertTrue(log.points.all { point ->
            point.indicatedAirspeedKmh == 78.0 && point.trueAirspeedKmh == 84.0
        })
    }

    @Test
    fun build_sameVariantTwiceIsDeterministic() {
        val builder = SyntheticThermalReplayLogBuilder()

        val first = builder.build(SyntheticThermalReplayVariant.WIND_NOISY)
        val second = builder.build(SyntheticThermalReplayVariant.WIND_NOISY)

        assertEquals(first, second)
    }

    @Test
    fun build_windNoisyVariantKeepsReplayShapeStableButChangesGroundTrack() {
        val builder = SyntheticThermalReplayLogBuilder()

        val clean = builder.build(SyntheticThermalReplayVariant.CLEAN)
        val noisy = builder.build(SyntheticThermalReplayVariant.WIND_NOISY)

        assertEquals(clean.points.map { it.timestampMillis }, noisy.points.map { it.timestampMillis })
        assertEquals(clean.points.map { it.gpsAltitude }, noisy.points.map { it.gpsAltitude })
        assertNotEquals(clean.points.map { it.longitude }, noisy.points.map { it.longitude })
        assertTrue(noisy.points.last().latitude > noisy.points.first().latitude)
    }
}
