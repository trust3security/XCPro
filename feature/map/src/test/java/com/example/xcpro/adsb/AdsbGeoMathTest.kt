package com.example.xcpro.adsb

import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbGeoMathTest {

    @Test
    fun computeBbox_expandsLongitudeSpanAtHigherLatitude() {
        val radiusKm = 20.0
        val atEquator = AdsbGeoMath.computeBbox(centerLat = 0.0, centerLon = 0.0, radiusKm = radiusKm)
        val at45 = AdsbGeoMath.computeBbox(centerLat = 45.0, centerLon = 0.0, radiusKm = radiusKm)
        val at80 = AdsbGeoMath.computeBbox(centerLat = 80.0, centerLon = 0.0, radiusKm = radiusKm)

        val lonSpanEquator = atEquator.lomax - atEquator.lomin
        val lonSpan45 = at45.lomax - at45.lomin
        val lonSpan80 = at80.lomax - at80.lomin

        assertTrue(lonSpan45 > lonSpanEquator)
        assertTrue(lonSpan80 > lonSpan45)
    }

    @Test
    fun haversine_thresholdAroundTwentyKilometers_matchesFilterContract() {
        val originLat = 0.0
        val originLon = 0.0
        val metersPerLatDegree = AdsbGeoMath.haversineMeters(originLat, originLon, 1.0, originLon)
        val lat19_9 = 19_900.0 / metersPerLatDegree
        val lat20_0 = 20_000.0 / metersPerLatDegree
        val lat20_1 = 20_100.0 / metersPerLatDegree

        val d19_9 = AdsbGeoMath.haversineMeters(originLat, originLon, lat19_9, originLon)
        val d20_0 = AdsbGeoMath.haversineMeters(originLat, originLon, lat20_0, originLon)
        val d20_1 = AdsbGeoMath.haversineMeters(originLat, originLon, lat20_1, originLon)

        assertTrue(d19_9 <= 20_000.0)
        assertTrue(kotlin.math.abs(d20_0 - 20_000.0) < 1.0)
        assertTrue(d20_1 > 20_000.0)
    }
}
