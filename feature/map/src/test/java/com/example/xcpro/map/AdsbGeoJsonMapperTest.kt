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

    private fun sampleTarget(category: Int?, trackDeg: Double?): AdsbTrafficUiModel {
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
            positionSource = 0,
            category = category,
            lastContactEpochSec = null
        )
    }
}
