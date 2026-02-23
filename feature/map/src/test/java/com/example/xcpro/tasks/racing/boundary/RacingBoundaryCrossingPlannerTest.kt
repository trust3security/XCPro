package com.example.xcpro.tasks.racing.boundary

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RacingBoundaryCrossingPlannerTest {

    private val epsilonPolicy = RacingBoundaryEpsilonPolicy(baseMeters = 30.0)
    private val planner = RacingBoundaryCrossingPlanner(epsilonPolicy)

    @Test
    fun cylinderEnterDetectsCrossingWithBoundaryAnchors() {
        val center = RacingBoundaryPoint(0.0, 0.0)
        val radiusMeters = 1_000.0
        val outside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, radiusMeters + 500.0)
        val inside = RacingBoundaryGeometry.pointOnBearing(center, 90.0, radiusMeters - 500.0)

        val previousFix = RacingNavigationFix(
            lat = outside.lat,
            lon = outside.lon,
            timestampMillis = 1_000L,
            accuracyMeters = 5.0
        )
        val currentFix = RacingNavigationFix(
            lat = inside.lat,
            lon = inside.lon,
            timestampMillis = 2_000L,
            accuracyMeters = 5.0
        )

        val crossing = planner.detectCylinderCrossing(
            center = center,
            radiusMeters = radiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.ENTER
        )

        assertNotNull(crossing)
        val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
            center.lat,
            center.lon,
            crossing!!.crossingPoint.lat,
            crossing.crossingPoint.lon
        )
        assertTrue(abs(distanceMeters - radiusMeters) < 5.0)
        assertTrue(crossing.crossingTimeMillis in previousFix.timestampMillis..currentFix.timestampMillis)

        val insideDistance = RacingGeometryUtils.haversineDistanceMeters(
            center.lat,
            center.lon,
            crossing.insideAnchor.lat,
            crossing.insideAnchor.lon
        )
        val outsideDistance = RacingGeometryUtils.haversineDistanceMeters(
            center.lat,
            center.lon,
            crossing.outsideAnchor.lat,
            crossing.outsideAnchor.lon
        )
        assertTrue(insideDistance < radiusMeters)
        assertTrue(outsideDistance > radiusMeters)
    }

    @Test
    fun cylinderExitDetectsCrossingWithinWindow() {
        val center = RacingBoundaryPoint(0.0, 0.0)
        val radiusMeters = 1_000.0
        val inside = RacingBoundaryGeometry.pointOnBearing(center, 270.0, radiusMeters - 500.0)
        val outside = RacingBoundaryGeometry.pointOnBearing(center, 270.0, radiusMeters + 500.0)

        val previousFix = RacingNavigationFix(
            lat = inside.lat,
            lon = inside.lon,
            timestampMillis = 5_000L,
            accuracyMeters = 5.0
        )
        val currentFix = RacingNavigationFix(
            lat = outside.lat,
            lon = outside.lon,
            timestampMillis = 6_000L,
            accuracyMeters = 5.0
        )

        val crossing = planner.detectCylinderCrossing(
            center = center,
            radiusMeters = radiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.EXIT
        )

        assertNotNull(crossing)
        assertTrue(crossing!!.crossingTimeMillis in previousFix.timestampMillis..currentFix.timestampMillis)
    }

    @Test
    fun lineExitDetectsCrossingWithinSemicircle() {
        val center = RacingBoundaryPoint(0.0, 0.0)
        val lineLengthMeters = 1_000.0
        val lineBearing = 90.0
        val sectorBearing = 0.0
        val insidePoint = RacingBoundaryGeometry.pointOnBearing(center, sectorBearing, 200.0)
        val outsidePoint = RacingBoundaryGeometry.pointOnBearing(center, sectorBearing + 180.0, 200.0)

        val previousFix = RacingNavigationFix(
            lat = insidePoint.lat,
            lon = insidePoint.lon,
            timestampMillis = 10_000L,
            accuracyMeters = 5.0
        )
        val currentFix = RacingNavigationFix(
            lat = outsidePoint.lat,
            lon = outsidePoint.lon,
            timestampMillis = 11_000L,
            accuracyMeters = 5.0
        )

        val crossing = planner.detectLineCrossing(
            center = center,
            lineLengthMeters = lineLengthMeters,
            lineBearingDegrees = lineBearing,
            sectorBearingDegrees = sectorBearing,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = RacingBoundaryTransition.EXIT
        )

        assertNotNull(crossing)
        assertTrue(crossing!!.crossingTimeMillis in previousFix.timestampMillis..currentFix.timestampMillis)
    }
}
