package com.example.xcpro.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class AdsbTrafficOverlaySupportTest {

    @Test
    fun viewportRange_returnsFarthestEdgeMidpointDistanceFromCenter() {
        val rangeMeters = resolveAdsbViewportRangeMeters(
            center = LatLng(0.0, 0.0),
            topLeft = LatLng(0.30, -0.20),
            topRight = LatLng(0.30, 0.20),
            bottomLeft = LatLng(-0.30, -0.20),
            bottomRight = LatLng(-0.30, 0.20)
        )

        val expectedMeters = haversineMeters(
            lat1 = 0.0,
            lon1 = 0.0,
            lat2 = 0.30,
            lon2 = 0.0
        )
        requireNotNull(rangeMeters)
        assertEquals(expectedMeters, rangeMeters, 250.0)
    }
}
