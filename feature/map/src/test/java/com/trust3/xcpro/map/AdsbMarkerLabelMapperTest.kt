package com.trust3.xcpro.map

import com.trust3.xcpro.common.units.UnitsPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AdsbMarkerLabelMapperTest {

    @Test
    fun map_placesHeightTopWhenTargetAbove() {
        val mapping = AdsbMarkerLabelMapper.map(
            targetAltitudeMeters = 1100.0,
            ownshipAltitudeMeters = 1000.0,
            distanceMeters = 1400.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals(AdsbRelativeAltitudeBand.ABOVE, mapping.relativeBand)
        assertEquals("+100 m", mapping.topLabel)
        assertEquals("1.4 km", mapping.bottomLabel)
    }

    @Test
    fun map_placesHeightBottomWhenTargetBelow() {
        val mapping = AdsbMarkerLabelMapper.map(
            targetAltitudeMeters = 900.0,
            ownshipAltitudeMeters = 1000.0,
            distanceMeters = 1400.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals(AdsbRelativeAltitudeBand.BELOW, mapping.relativeBand)
        assertEquals("1.4 km", mapping.topLabel)
        assertEquals("-100 m", mapping.bottomLabel)
    }

    @Test
    fun map_defaultsToHeightTopWhenDeltaUnknown() {
        val mapping = AdsbMarkerLabelMapper.map(
            targetAltitudeMeters = null,
            ownshipAltitudeMeters = 1000.0,
            distanceMeters = 1400.0,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals(AdsbRelativeAltitudeBand.UNKNOWN, mapping.relativeBand)
        assertEquals(AdsbMarkerLabelMapper.UNKNOWN_TEXT, mapping.topLabel)
        assertEquals("1.4 km", mapping.bottomLabel)
    }

    @Test
    fun map_usesUnknownDistanceWhenDistanceInvalid() {
        val mapping = AdsbMarkerLabelMapper.map(
            targetAltitudeMeters = 1100.0,
            ownshipAltitudeMeters = 1000.0,
            distanceMeters = Double.NaN,
            unitsPreferences = UnitsPreferences()
        )

        assertEquals("+100 m", mapping.topLabel)
        assertEquals(AdsbMarkerLabelMapper.UNKNOWN_TEXT, mapping.bottomLabel)
    }
}
