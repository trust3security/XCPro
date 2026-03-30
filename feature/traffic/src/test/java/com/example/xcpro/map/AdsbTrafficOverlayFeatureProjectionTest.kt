package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.LineString

class AdsbTrafficOverlayFeatureProjectionTest {

    @Test
    fun buildAdsbTrafficLeaderLineFeatures_mapsTrueCoordinateToDisplayCoordinate() {
        val target = AdsbTrafficUiModel(
            id = Icao24.from("abc123") ?: error("invalid ICAO24"),
            callsign = "TEST",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1_000.0,
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

        val feature = buildAdsbTrafficLeaderLineFeatures(
            targets = listOf(target),
            displayCoordinatesByKey = mapOf(
                target.id.raw to TrafficDisplayCoordinate(
                    latitude = -35.01,
                    longitude = 149.02
                )
            ),
            maxTargets = 1
        ).single()

        val geometry = feature.geometry() as LineString
        assertEquals(target.lon, geometry.coordinates()[0].longitude(), 0.0)
        assertEquals(target.lat, geometry.coordinates()[0].latitude(), 0.0)
        assertEquals(149.02, geometry.coordinates()[1].longitude(), 0.0)
        assertEquals(-35.01, geometry.coordinates()[1].latitude(), 0.0)
    }
}
