package com.trust3.xcpro.map.trail

import com.trust3.xcpro.map.trail.domain.LiveTrailInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTrailInterpolatorTest {

    @Test
    fun expand_addsIntermediateSamplesAtConfiguredCadence() {
        val interpolator = LiveTrailInterpolator(stepMillis = 200L)
        val first = TrailSample(
            latitude = 46.0,
            longitude = 7.0,
            timestampMillis = 1_000L,
            altitudeMeters = 1000.0,
            varioMs = 0.0,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0
        )
        val second = TrailSample(
            latitude = 46.0002,
            longitude = 7.0002,
            timestampMillis = 1_600L,
            altitudeMeters = 1000.0,
            varioMs = 0.0,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0
        )

        val firstExpanded = interpolator.expand(first)
        val secondExpanded = interpolator.expand(second)

        assertEquals(1, firstExpanded.size)
        assertTrue(secondExpanded.size > 1)
        assertEquals(1_200L, secondExpanded[0].timestampMillis)
        assertEquals(1_400L, secondExpanded[1].timestampMillis)
        assertEquals(1_600L, secondExpanded.last().timestampMillis)
    }

    @Test
    fun expand_usesCurvedPathWhenTrackTurns() {
        val interpolator = LiveTrailInterpolator(stepMillis = 200L, turnAngleThresholdDeg = 5.0)
        val first = TrailSample(
            latitude = 46.0,
            longitude = 7.0,
            timestampMillis = 1_000L,
            altitudeMeters = 1000.0,
            varioMs = 0.0,
            trackDegrees = 0.0,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0
        )
        val second = TrailSample(
            latitude = 46.0004,
            longitude = 7.0004,
            timestampMillis = 1_900L,
            altitudeMeters = 1000.0,
            varioMs = 0.0,
            trackDegrees = 90.0,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0
        )

        interpolator.expand(first)
        val expanded = interpolator.expand(second)

        assertTrue(expanded.size > 4)
        assertTrue(expanded.size > 1)
        assertTrue(
            "Expected at least one interpolated point to deviate from linear interpolation path.",
            expanded.dropLast(1).any { point ->
                val ratio = (point.timestampMillis - first.timestampMillis).toDouble() /
                    (second.timestampMillis - first.timestampMillis).toDouble()
                val expectedLat = first.latitude + (second.latitude - first.latitude) * ratio
                val expectedLon = first.longitude + (second.longitude - first.longitude) * ratio
                val linearError = com.trust3.xcpro.map.trail.TrailGeo.distanceMeters(
                    expectedLat,
                    expectedLon,
                    point.latitude,
                    point.longitude
                )
                linearError > 1.0
            }
        )
    }

    @Test
    fun shouldReset_onLargeGapOrDistanceJump() {
        val interpolator = LiveTrailInterpolator(
            stepMillis = 200L,
            resetDistanceM = 250.0,
            resetBackstepMs = 1_000L
        )

        interpolator.expand(
            TrailSample(
                latitude = 46.0,
                longitude = 7.0,
                timestampMillis = 1_000L,
                altitudeMeters = 1000.0,
                varioMs = 0.0,
                windSpeedMs = 0.0,
                windDirectionFromDeg = 0.0
            )
        )

        val farSample = TrailSample(
            latitude = 47.0,
            longitude = 8.0,
            timestampMillis = 1_200L,
            altitudeMeters = 1000.0,
            varioMs = 0.0,
            windSpeedMs = 0.0,
            windDirectionFromDeg = 0.0
        )
        assertTrue(interpolator.shouldReset(farSample))
    }
}
