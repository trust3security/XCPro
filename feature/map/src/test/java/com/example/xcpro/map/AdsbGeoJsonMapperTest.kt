package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.adsb.Icao24
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbGeoJsonMapperTest {

    @Test
    fun toFeature_includesIconIdAndTrackWhenTrackPresent() {
        val target = sampleTarget(category = 10, trackDeg = 273.5)

        val feature = AdsbGeoJsonMapper.toFeature(target)

        assertNotNull(feature)
        feature ?: return
        assertEquals("abc123", feature.id())
        assertEquals(
            "adsb_icon_balloon",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertTrue(feature.hasProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG))
        assertTrue(feature.hasProperty(AdsbGeoJsonMapper.PROP_DISTANCE_M))
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_HAS_OWNSHIP_REF).toDouble(),
            1e-6
        )
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY).toDouble(),
            1e-6
        )
        assertEquals(
            273.5,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG).toDouble(),
            1e-6
        )
    }

    @Test
    fun toFeature_omitsTrackWhenTrackMissing() {
        val target = sampleTarget(category = 14, trackDeg = null)

        val feature = AdsbGeoJsonMapper.toFeature(target)

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_drone",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertFalse(feature.hasProperty(AdsbGeoJsonMapper.PROP_TRACK_DEG))
    }

    @Test
    fun toFeature_usesEmergencyIconWhenCollisionRiskIsTrue() {
        val target = sampleTarget(category = 9, trackDeg = 180.0, isEmergencyCollisionRisk = true)

        val feature = AdsbGeoJsonMapper.toFeature(target)

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            "adsb_icon_glider_emergency",
            feature.getStringProperty(AdsbGeoJsonMapper.PROP_ICON_ID)
        )
        assertEquals(
            1.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_IS_EMERGENCY).toDouble(),
            1e-6
        )
    }

    @Test
    fun toFeature_marksOwnshipReferenceAvailabilityFlag() {
        val target = sampleTarget(category = 2, trackDeg = 95.0, usesOwnshipReference = false)

        val feature = AdsbGeoJsonMapper.toFeature(target)

        assertNotNull(feature)
        feature ?: return
        assertEquals(
            0.0,
            feature.getNumberProperty(AdsbGeoJsonMapper.PROP_HAS_OWNSHIP_REF).toDouble(),
            1e-6
        )
    }

    private fun sampleTarget(
        category: Int?,
        trackDeg: Double?,
        isEmergencyCollisionRisk: Boolean = false,
        usesOwnshipReference: Boolean = true
    ): AdsbTrafficUiModel {
        val id = Icao24.from("abc123") ?: error("invalid test id")
        return AdsbTrafficUiModel(
            id = id,
            callsign = "TEST01",
            lat = -35.0,
            lon = 149.0,
            altitudeM = 1000.0,
            speedMps = 70.0,
            trackDeg = trackDeg,
            climbMps = 0.5,
            ageSec = 3,
            isStale = false,
            distanceMeters = 1500.0,
            bearingDegFromUser = 220.0,
            usesOwnshipReference = usesOwnshipReference,
            positionSource = 0,
            category = category,
            lastContactEpochSec = null,
            isEmergencyCollisionRisk = isEmergencyCollisionRisk
        )
    }
}
