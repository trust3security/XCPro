package com.trust3.xcpro.ogn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OgnSelectedThermalContextProjectorTest {

    @Test
    fun buildSelectedOgnThermalContext_matchesSelectedHotspotSegments_andDerivesMetrics() {
        val hotspot = hotspot(
            id = "H1",
            sourceTargetId = "pilot-1",
            startedAtMonoMs = 100L,
            updatedAtMonoMs = 200L,
            updatedAtWallMs = 1_900L,
            startAltitudeMeters = 900.0,
            maxAltitudeMeters = 1_050.0
        )
        val context = buildSelectedOgnThermalContext(
            selectedId = hotspot.id,
            hotspots = listOf(
                hotspot,
                hotspot(
                    id = "H2",
                    sourceTargetId = "pilot-2",
                    startedAtMonoMs = 100L,
                    updatedAtMonoMs = 200L
                )
            ),
            rawSegments = listOf(
                segment(
                    id = "before-window",
                    sourceTargetId = "pilot-1",
                    startLatitude = -35.20,
                    startLongitude = 149.20,
                    endLatitude = -35.19,
                    endLongitude = 149.20,
                    timestampMonoMs = 90L
                ),
                segment(
                    id = "loop-1",
                    sourceTargetId = "pilot-1",
                    startLatitude = -35.0000,
                    startLongitude = 149.0000,
                    endLatitude = -34.9900,
                    endLongitude = 149.0000,
                    timestampMonoMs = 120L
                ),
                segment(
                    id = "other-aircraft",
                    sourceTargetId = "pilot-2",
                    startLatitude = -35.0000,
                    startLongitude = 149.0000,
                    endLatitude = -34.9800,
                    endLongitude = 149.0200,
                    timestampMonoMs = 140L
                ),
                segment(
                    id = "loop-2",
                    sourceTargetId = "pilot-1",
                    startLatitude = -34.9900,
                    startLongitude = 149.0000,
                    endLatitude = -34.9900,
                    endLongitude = 149.0100,
                    timestampMonoMs = 150L
                ),
                segment(
                    id = "loop-3",
                    sourceTargetId = "pilot-1",
                    startLatitude = -34.9900,
                    startLongitude = 149.0100,
                    endLatitude = -35.0000,
                    endLongitude = 149.0100,
                    timestampMonoMs = 180L
                ),
                segment(
                    id = "after-window",
                    sourceTargetId = "pilot-1",
                    startLatitude = -35.0000,
                    startLongitude = 149.0100,
                    endLatitude = -35.0100,
                    endLongitude = 149.0100,
                    timestampMonoMs = 250L
                )
            ),
            nowWallMs = 2_000L
        )

        requireNotNull(context)
        assertEquals(hotspot, context.hotspot)
        assertEquals(listOf("loop-1", "loop-2", "loop-3"), context.highlightedSegments.map { it.id })
        assertEquals(4, context.loopPoints.size)
        assertEquals(-35.0000, context.startPoint?.latitude ?: Double.NaN, 1e-6)
        assertEquals(149.0000, context.startPoint?.longitude ?: Double.NaN, 1e-6)
        assertEquals(-35.0000, context.latestPoint?.latitude ?: Double.NaN, 1e-6)
        assertEquals(149.0100, context.latestPoint?.longitude ?: Double.NaN, 1e-6)
        assertEquals(4, context.occupancyHullPoints.size)
        assertEquals(90.0, context.driftBearingDeg ?: Double.NaN, 1.0)
        assertTrue((context.driftDistanceMeters ?: 0.0) > 800.0)
        assertEquals(100L, context.ageMs)
        assertEquals(100L, context.durationMs)
        assertEquals(150.0, context.altitudeGainMeters ?: Double.NaN, 1e-6)
    }

    @Test
    fun buildSelectedOgnThermalContext_suppressesHullAndDriftWhenDistinctPointsAreInsufficient() {
        val hotspot = hotspot(
            id = "H1",
            sourceTargetId = "pilot-1",
            startedAtMonoMs = 100L,
            updatedAtMonoMs = 150L
        )

        val context = buildSelectedOgnThermalContext(
            selectedId = hotspot.id,
            hotspots = listOf(hotspot),
            rawSegments = listOf(
                segment(
                    id = "loop-1",
                    sourceTargetId = "pilot-1",
                    startLatitude = -35.0000,
                    startLongitude = 149.0000,
                    endLatitude = -35.0000,
                    endLongitude = 149.0000,
                    timestampMonoMs = 120L
                )
            ),
            nowWallMs = 500L
        )

        requireNotNull(context)
        assertEquals(2, context.loopPoints.size)
        assertTrue(context.occupancyHullPoints.isEmpty())
        assertEquals(context.startPoint, context.latestPoint)
        assertNull(context.driftBearingDeg)
        assertNull(context.driftDistanceMeters)
    }

    private fun hotspot(
        id: String,
        sourceTargetId: String,
        startedAtMonoMs: Long,
        updatedAtMonoMs: Long,
        updatedAtWallMs: Long = 400L,
        startAltitudeMeters: Double? = 900.0,
        maxAltitudeMeters: Double? = 950.0
    ): OgnThermalHotspot = OgnThermalHotspot(
        id = id,
        sourceTargetId = sourceTargetId,
        sourceLabel = sourceTargetId,
        latitude = -35.0000,
        longitude = 149.0000,
        startedAtMonoMs = startedAtMonoMs,
        startedAtWallMs = 300L,
        updatedAtMonoMs = updatedAtMonoMs,
        updatedAtWallMs = updatedAtWallMs,
        startAltitudeMeters = startAltitudeMeters,
        maxAltitudeMeters = maxAltitudeMeters,
        maxAltitudeAtMonoMs = updatedAtMonoMs,
        maxClimbRateMps = 2.4,
        averageClimbRateMps = 1.8,
        averageBottomToTopClimbRateMps = 1.9,
        snailColorIndex = 12,
        state = OgnThermalHotspotState.ACTIVE
    )

    private fun segment(
        id: String,
        sourceTargetId: String,
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double,
        timestampMonoMs: Long
    ): OgnGliderTrailSegment = OgnGliderTrailSegment(
        id = id,
        sourceTargetId = sourceTargetId,
        sourceLabel = sourceTargetId,
        startLatitude = startLatitude,
        startLongitude = startLongitude,
        endLatitude = endLatitude,
        endLongitude = endLongitude,
        colorIndex = 8,
        widthPx = 2f,
        timestampMonoMs = timestampMonoMs
    )
}
