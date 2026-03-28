package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.ogn.OgnTrafficIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class OgnTrafficOverlayFeatureLabelsTest {

    @Test
    fun featureGeneration_keepsBothLabelsAvailableForLayerVisibilityGating() {
        val feature = buildFeature(
            target = target(
                id = "T1",
                altitudeMeters = 1_040.0
            )
        )

        assertEquals("+40 m", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    @Test
    fun featureGeneration_preservesMapperSlotOrderingWhenTargetIsBelowOwnship() {
        val feature = buildFeature(
            target = target(
                id = "T2",
                altitudeMeters = 960.0
            )
        )

        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals("-40 m", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    private fun buildFeature(
        target: OgnTrafficTarget
    ) = buildOgnTrafficOverlayFeatures(
        nowMonoMs = 10_000L,
        targets = listOf(target),
        ownshipAltitudeMeters = 1_000.0,
        visibleBounds = null,
        altitudeUnit = AltitudeUnit.METERS,
        useSatelliteContrastIcons = false,
        unitsPreferences = UnitsPreferences(),
        maxTargets = MAX_TARGETS,
        staleVisualAfterMs = 5_000L,
        liveAlpha = 1.0,
        staleAlpha = 0.5
    ).single()

    private fun target(
        id: String,
        altitudeMeters: Double
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = altitudeMeters,
        trackDegrees = 90.0,
        groundSpeedMps = 35.0,
        verticalSpeedMps = 1.2,
        deviceIdHex = "ABC123",
        signalDb = -12.0,
        displayLabel = id,
        identity = OgnTrafficIdentity(
            registration = "VH-XYZ",
            competitionNumber = "AB1",
            aircraftModel = "Glider",
            tracked = true,
            identified = true,
            aircraftTypeCode = null
        ),
        rawComment = null,
        rawLine = "raw-$id",
        timestampMillis = 10_000L,
        lastSeenMillis = 10_000L,
        distanceMeters = 1_200.0,
        addressType = OgnAddressType.FLARM,
        addressHex = "ABC123",
        canonicalKey = "FLARM:ABC123"
    )
}
