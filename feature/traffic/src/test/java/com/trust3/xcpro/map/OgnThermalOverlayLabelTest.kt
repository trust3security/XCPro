package com.trust3.xcpro.map

import com.trust3.xcpro.ogn.OgnThermalHotspot
import com.trust3.xcpro.ogn.OgnThermalHotspotState
import com.trust3.xcpro.ogn.displayClimbRateMps
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnThermalOverlayLabelTest {

    @Test
    fun labelUsesAverageBottomToTopClimbWhenAvailable() {
        val hotspot = sampleHotspot(
            maxClimbRateMps = 4.2,
            averageClimbRateMps = 1.6,
            averageBottomToTopClimbRateMps = 1.8
        )

        assertEquals(1.8, hotspot.displayClimbRateMps() ?: Double.NaN, 1e-6)
        assertEquals("+1.8", thermalHotspotOverlayLabel(hotspot))
    }

    @Test
    fun labelFallsBackToAverageClimbWhenBottomToTopMissing() {
        val hotspot = sampleHotspot(
            maxClimbRateMps = 4.2,
            averageClimbRateMps = 1.6,
            averageBottomToTopClimbRateMps = null
        )

        assertEquals("+1.6", thermalHotspotOverlayLabel(hotspot))
    }

    @Test
    fun labelFallsBackToSourceLabelWhenAverageUnavailable() {
        val hotspot = sampleHotspot(
            maxClimbRateMps = 4.2,
            averageClimbRateMps = Double.NaN,
            averageBottomToTopClimbRateMps = null
        )

        assertEquals("Thermal A", thermalHotspotOverlayLabel(hotspot))
    }

    private fun sampleHotspot(
        maxClimbRateMps: Double,
        averageClimbRateMps: Double?,
        averageBottomToTopClimbRateMps: Double?
    ): OgnThermalHotspot = OgnThermalHotspot(
        id = "thermal-1",
        sourceTargetId = "UNK:ABCD01",
        sourceLabel = "Thermal A",
        latitude = -35.1,
        longitude = 149.1,
        startedAtMonoMs = 0L,
        startedAtWallMs = 0L,
        updatedAtMonoMs = 50_000L,
        updatedAtWallMs = 50_000L,
        startAltitudeMeters = 1000.0,
        maxAltitudeMeters = 1088.0,
        maxAltitudeAtMonoMs = 50_000L,
        maxClimbRateMps = maxClimbRateMps,
        averageClimbRateMps = averageClimbRateMps,
        averageBottomToTopClimbRateMps = averageBottomToTopClimbRateMps,
        snailColorIndex = 12,
        state = OgnThermalHotspotState.ACTIVE
    )
}
