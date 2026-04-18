package com.trust3.xcpro.ogn

import com.trust3.xcpro.common.units.AltitudeUnit
import com.trust3.xcpro.common.units.DistanceUnit
import com.trust3.xcpro.common.units.SpeedUnit
import com.trust3.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnThermalDetailsSheetFormattingTest {

    @Test
    fun formatThermalAge_usesCompactElapsedLabels() {
        assertEquals("<1s", formatThermalAge(500L))
        assertEquals("12s", formatThermalAge(12_000L))
        assertEquals("2m 05s", formatThermalAge(125_000L))
        assertEquals("1h 02m", formatThermalAge(3_720_000L))
    }

    @Test
    fun formatThermalDuration_usesCompactElapsedLabels() {
        assertEquals("45s", formatThermalDuration(45_000L))
        assertEquals("9m 03s", formatThermalDuration(543_000L))
        assertEquals("1h 10m", formatThermalDuration(4_200_000L))
    }

    @Test
    fun formatThermalDrift_usesCardinalDirectionAndDistance() {
        val context = SelectedOgnThermalContext(
            hotspot = hotspot(),
            hotspotPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
            highlightedSegments = emptyList(),
            loopPoints = emptyList(),
            occupancyHullPoints = emptyList(),
            startPoint = OgnThermalPoint(latitude = -35.0000, longitude = 149.0000),
            latestPoint = OgnThermalPoint(latitude = -34.9950, longitude = 149.0100),
            driftBearingDeg = 78.0,
            driftDistanceMeters = 1_200.0,
            ageMs = 10_000L,
            durationMs = 20_000L,
            altitudeGainMeters = 140.0
        )

        assertEquals("ENE, 1.2 km", formatThermalDrift(context, unitsPreferences()))
    }

    @Test
    fun cardinalDirectionLabel_mapsSixteenPointCompass() {
        assertEquals("N", cardinalDirectionLabel(0.0))
        assertEquals("NNE", cardinalDirectionLabel(20.0))
        assertEquals("ENE", cardinalDirectionLabel(78.0))
        assertEquals("SW", cardinalDirectionLabel(225.0))
    }

    private fun unitsPreferences(): UnitsPreferences = UnitsPreferences(
        altitude = AltitudeUnit.METERS,
        speed = SpeedUnit.METERS_PER_SECOND,
        distance = DistanceUnit.KILOMETERS
    )

    private fun hotspot(): OgnThermalHotspot = OgnThermalHotspot(
        id = "thermal-1",
        sourceTargetId = "pilot-1",
        sourceLabel = "pilot-1",
        latitude = -35.0000,
        longitude = 149.0000,
        startedAtMonoMs = 100L,
        startedAtWallMs = 200L,
        updatedAtMonoMs = 200L,
        updatedAtWallMs = 300L,
        startAltitudeMeters = 900.0,
        maxAltitudeMeters = 1_040.0,
        maxAltitudeAtMonoMs = 200L,
        maxClimbRateMps = 2.4,
        averageClimbRateMps = 1.8,
        averageBottomToTopClimbRateMps = 1.9,
        snailColorIndex = 12,
        state = OgnThermalHotspotState.ACTIVE
    )
}
