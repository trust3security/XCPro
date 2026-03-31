package com.example.xcpro.map

import com.example.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.maplibre.geojson.LineString

class AdsbTrafficOverlayFeatureProjectionTest {

    @Test
    fun selectRenderableAdsbTargets_skipsInvalidEarlyTargetAndKeepsLaterValidUnderMaxTargets() {
        val invalid = target(id = "bad111", lat = Double.NaN)
        val valid = target(id = "abc123")
        val later = target(id = "def456")

        val renderTargets = selectRenderableAdsbTargets(
            targets = listOf(invalid, valid, later),
            maxTargets = 1
        )

        assertEquals(listOf(valid.id), renderTargets.map { it.id })
    }

    @Test
    fun buildAdsbTrafficOverlayFeatures_skipsInvalidEarlyTargetAndRendersLaterValidUnderMaxTargets() {
        val invalid = target(id = "bad111", lat = Double.NaN)
        val valid = target(id = "abc123", altitudeM = 1_100.0)

        val feature = buildAdsbTrafficOverlayFeatures(
            nowMonoMs = 10_000L,
            targets = listOf(invalid, valid),
            fullLabelKeys = setOf(valid.id.raw),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences(),
            iconStyleIdOverrides = emptyMap(),
            emergencyFlashEnabled = false,
            maxTargets = 1,
            liveAlpha = 1.0,
            staleAlpha = 0.5
        ).single()

        assertEquals(valid.id.raw, feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICAO24))
        assertFalse(feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP).isBlank())
    }

    @Test
    fun buildAdsbTrafficLeaderLineFeatures_usesLaterValidTargetWhenEarlierTargetIsInvalid() {
        val invalid = target(id = "bad111", lat = Double.NaN)
        val valid = target(id = "abc123")

        val feature = buildAdsbTrafficLeaderLineFeatures(
            targets = listOf(invalid, valid),
            displayCoordinatesByKey = mapOf(
                valid.id.raw to TrafficDisplayCoordinate(
                    latitude = -35.01,
                    longitude = 149.02
                )
            ),
            maxTargets = 1
        ).single()

        val geometry = feature.geometry() as LineString
        assertEquals(valid.id.raw, feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICAO24))
        assertEquals(valid.lon, geometry.coordinates()[0].longitude(), 0.0)
        assertEquals(valid.lat, geometry.coordinates()[0].latitude(), 0.0)
        assertEquals(149.02, geometry.coordinates()[1].longitude(), 0.0)
        assertEquals(-35.01, geometry.coordinates()[1].latitude(), 0.0)
    }

    private fun target(
        id: String,
        lat: Double = -35.0,
        lon: Double = 149.0,
        altitudeM: Double = 1_000.0
    ) = AdsbTrafficUiModel(
        id = Icao24.from(id) ?: error("invalid ICAO24"),
        callsign = "TEST$id",
        lat = lat,
        lon = lon,
        altitudeM = altitudeM,
        speedMps = 40.0,
        trackDeg = 90.0,
        climbMps = 0.0,
        ageSec = 1,
        isStale = false,
        distanceMeters = 1_200.0,
        bearingDegFromUser = 90.0,
        positionSource = 0,
        category = 0,
        lastContactEpochSec = null
    )
}
