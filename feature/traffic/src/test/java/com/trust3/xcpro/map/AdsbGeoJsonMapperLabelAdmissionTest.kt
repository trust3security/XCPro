package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Point

class AdsbGeoJsonMapperLabelAdmissionTest {

    @Test
    fun toFeatureInternal_keepsLabelsWhenAdmitted() {
        val feature = AdsbGeoJsonMapper.toFeatureInternal(
            target = target(),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences(),
            showFullLabel = true
        ) ?: error("feature expected")

        assertEquals("+50 m", feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP))
        assertEquals("1.2 km", feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM))
    }

    @Test
    fun toFeatureInternal_blanksLabelsWhenSuppressed() {
        val feature = AdsbGeoJsonMapper.toFeatureInternal(
            target = target(),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences(),
            showFullLabel = false
        ) ?: error("feature expected")

        assertEquals("", feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_TOP))
        assertEquals("", feature.getStringProperty(AdsbGeoJsonMapper.PROP_LABEL_BOTTOM))
    }

    @Test
    fun toFeatureInternal_usesDisplayCoordinateWhenProvided() {
        val feature = AdsbGeoJsonMapper.toFeatureInternal(
            target = target(),
            ownshipAltitudeMeters = 1_000.0,
            unitsPreferences = UnitsPreferences(),
            displayCoordinate = TrafficDisplayCoordinate(
                latitude = -35.01,
                longitude = 149.02
            ),
            showFullLabel = false
        ) ?: error("feature expected")

        val geometry = feature.geometry() as Point
        assertEquals(149.02, geometry.longitude(), 0.0)
        assertEquals(-35.01, geometry.latitude(), 0.0)
    }

    private fun target(): AdsbTrafficUiModel = AdsbTrafficUiModel(
        id = Icao24.from("abc123") ?: error("invalid ICAO24"),
        callsign = "TEST",
        lat = -35.0,
        lon = 149.0,
        altitudeM = 1_050.0,
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
