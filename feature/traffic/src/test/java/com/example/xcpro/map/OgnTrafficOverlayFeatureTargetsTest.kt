package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.ogn.OgnTrafficIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OgnTrafficOverlayFeatureTargetsTest {

    @Test
    fun featureGeneration_writesTargetIdentityAndResolvesTargetKey() {
        val target = target("A1")
        val feature = buildFeatures(targets = listOf(target)).single()

        assertEquals(target.canonicalKey, feature.getStringProperty(PROP_TARGET_KEY))
        assertEquals(target.id, feature.getStringProperty(PROP_TARGET_ID))
        assertEquals(target.canonicalKey, resolveOgnTrafficTargetKey(feature))
    }

    @Test
    fun featureGeneration_skipsInvalidTargetsAndRespectsMaxTargets() {
        val features = buildFeatures(
            targets = listOf(
                target("BAD", latitude = 120.0),
                target("A1"),
                target("B2")
            ),
            maxTargets = 1
        )

        assertEquals(1, features.size)
        assertEquals("FLARM:A1", resolveOgnTrafficTargetKey(features.single()))
    }

    @Test
    fun resolveOgnTrafficTargetKey_returnsNullWhenFeatureHasNoTargetIdentity() {
        val feature = org.maplibre.geojson.Feature.fromGeometry(
            org.maplibre.geojson.Point.fromLngLat(149.0, -35.0)
        )

        assertNull(resolveOgnTrafficTargetKey(feature))
    }

    private fun buildFeatures(
        targets: List<OgnTrafficTarget>,
        maxTargets: Int = MAX_TARGETS
    ) = buildOgnTrafficOverlayFeatures(
        nowMonoMs = 10_000L,
        targets = targets,
        ownshipAltitudeMeters = 1_000.0,
        visibleBounds = null,
        altitudeUnit = AltitudeUnit.METERS,
        useSatelliteContrastIcons = false,
        unitsPreferences = UnitsPreferences(),
        maxTargets = maxTargets,
        staleVisualAfterMs = 5_000L,
        liveAlpha = 1.0,
        staleAlpha = 0.5
    )

    private fun target(
        id: String,
        latitude: Double = -35.0
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = latitude,
        longitude = 149.0,
        altitudeMeters = 1_050.0,
        trackDegrees = 90.0,
        groundSpeedMps = 35.0,
        verticalSpeedMps = 1.2,
        deviceIdHex = "HEX$id",
        signalDb = -12.0,
        displayLabel = id,
        identity = OgnTrafficIdentity(
            registration = "VH-$id",
            competitionNumber = id,
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
        addressHex = id,
        canonicalKey = "FLARM:$id"
    )
}
