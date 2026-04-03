package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.SpeedUnit
import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.ogn.OgnTrafficIdentity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class OgnTrafficOverlayFeatureLabelsTest {

    @Test
    fun featureGeneration_keepsBothLabelsAvailableForLayerVisibilityGating() {
        val feature = buildFeature(
            target = target(
                id = "T1",
                altitudeMeters = 1_040.0
            ),
            fullLabelKeys = setOf("FLARM:ABC123")
        )

        assertEquals(
            "+40 m | ${UnitsFormatter.speed(SpeedMs(35.0), UnitsPreferences()).text}",
            feature.getStringProperty(PROP_TOP_LABEL)
        )
        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    @Test
    fun featureGeneration_preservesMapperSlotOrderingWhenTargetIsBelowOwnship() {
        val feature = buildFeature(
            target = target(
                id = "T2",
                altitudeMeters = 960.0
            ),
            fullLabelKeys = setOf("FLARM:ABC123")
        )

        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals(
            "-40 m | ${UnitsFormatter.speed(SpeedMs(35.0), UnitsPreferences()).text}",
            feature.getStringProperty(PROP_BOTTOM_LABEL)
        )
    }

    @Test
    fun featureGeneration_blanksLabelsWhenTargetIsNotAdmitted() {
        val feature = buildFeature(
            target = target(
                id = "T3",
                altitudeMeters = 1_040.0
            ),
            fullLabelKeys = emptySet()
        )

        assertEquals("", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals("", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    @Test
    fun featureGeneration_usesUnitsPreferenceForSpeedOnRelativeLabel() {
        val unitsPreferences = UnitsPreferences(speed = SpeedUnit.KNOTS)
        val feature = buildFeature(
            target = target(
                id = "T4A",
                altitudeMeters = 1_040.0
            ),
            fullLabelKeys = setOf("FLARM:ABC123"),
            unitsPreferences = unitsPreferences
        )

        assertEquals(
            "+40 m | ${UnitsFormatter.speed(SpeedMs(35.0), unitsPreferences).text}",
            feature.getStringProperty(PROP_TOP_LABEL)
        )
        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    @Test
    fun featureGeneration_keepsDeltaOnlyWhenSpeedUnavailable() {
        val feature = buildFeature(
            target = target(
                id = "T4B",
                altitudeMeters = 1_040.0,
                groundSpeedMps = null
            ),
            fullLabelKeys = setOf("FLARM:ABC123")
        )

        assertEquals("+40 m", feature.getStringProperty(PROP_TOP_LABEL))
        assertEquals("AB1 1.2 km", feature.getStringProperty(PROP_BOTTOM_LABEL))
    }

    @Test
    fun featureGeneration_usesDisplayCoordinateWhenTargetIsFannedOut() {
        val feature = buildFeature(
            target = target(
                id = "T4",
                altitudeMeters = 1_040.0
            ),
            fullLabelKeys = emptySet(),
            displayCoordinatesByKey = mapOf(
                "FLARM:ABC123" to TrafficDisplayCoordinate(
                    latitude = -35.01,
                    longitude = 149.02
                )
            )
        )

        val geometry = feature.geometry() as Point
        assertEquals(149.02, geometry.longitude(), 0.0)
        assertEquals(-35.01, geometry.latitude(), 0.0)
    }

    @Test
    fun leaderLineGeneration_mapsTrueCoordinateToDisplayCoordinate() {
        val target = target(
            id = "T5",
            altitudeMeters = 1_040.0
        )

        val lineFeature = buildOgnTrafficLeaderLineFeatures(
            targets = listOf(target),
            displayCoordinatesByKey = mapOf(
                target.canonicalKey to TrafficDisplayCoordinate(
                    latitude = -35.01,
                    longitude = 149.02
                )
            ),
            visibleBounds = null,
            maxTargets = MAX_TARGETS
        ).single()

        val geometry = lineFeature.geometry() as LineString
        assertEquals(target.longitude, geometry.coordinates()[0].longitude(), 0.0)
        assertEquals(target.latitude, geometry.coordinates()[0].latitude(), 0.0)
        assertEquals(149.02, geometry.coordinates()[1].longitude(), 0.0)
        assertEquals(-35.01, geometry.coordinates()[1].latitude(), 0.0)
    }

    private fun buildFeature(
        target: OgnTrafficTarget,
        fullLabelKeys: Set<String>,
        displayCoordinatesByKey: Map<String, TrafficDisplayCoordinate> = emptyMap(),
        unitsPreferences: UnitsPreferences = UnitsPreferences()
    ) = buildOgnTrafficOverlayFeatures(
        nowMonoMs = 10_000L,
        targets = listOf(target),
        fullLabelKeys = fullLabelKeys,
        displayCoordinatesByKey = displayCoordinatesByKey,
        ownshipAltitudeMeters = 1_000.0,
        visibleBounds = null,
        altitudeUnit = AltitudeUnit.METERS,
        useSatelliteContrastIcons = false,
        unitsPreferences = unitsPreferences,
        maxTargets = MAX_TARGETS,
        staleVisualAfterMs = 5_000L,
        liveAlpha = 1.0,
        staleAlpha = 0.5
    ).single()

    private fun target(
        id: String,
        altitudeMeters: Double,
        groundSpeedMps: Double? = 35.0
    ): OgnTrafficTarget = OgnTrafficTarget(
        id = id,
        callsign = "CALL$id",
        destination = "APRS",
        latitude = -35.0,
        longitude = 149.0,
        altitudeMeters = altitudeMeters,
        trackDegrees = 90.0,
        groundSpeedMps = groundSpeedMps,
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
