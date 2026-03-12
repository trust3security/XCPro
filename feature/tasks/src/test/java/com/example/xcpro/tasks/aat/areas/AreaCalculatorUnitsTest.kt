package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AreaCalculatorUnitsTest {

    private val circleCalculator = CircleAreaCalculator()
    private val sectorCalculator = SectorAreaCalculator()

    @Test
    fun circleIsInsideArea_respectsMeterRadius() {
        val center = AATLatLng(0.0, 0.0)
        val insidePoint = AATMathUtils.calculatePointAtBearing(center, 45.0, 0.5)
        val outsidePoint = AATMathUtils.calculatePointAtBearing(center, 45.0, 1.5)

        assertTrue(circleCalculator.isInsideArea(insidePoint, center, 1000.0))
        assertFalse(circleCalculator.isInsideArea(outsidePoint, center, 1000.0))
    }

    @Test
    fun circleNearestPointOnBoundary_usesMeterRadius() {
        val center = AATLatLng(0.0, 0.0)
        val from = AATMathUtils.calculatePointAtBearing(center, 90.0, 3.0)

        val boundaryPoint = circleCalculator.nearestPointOnBoundary(from, center, 1000.0)
        val boundaryDistanceMeters = AATMathUtils.calculateDistanceMeters(center, boundaryPoint)

        assertTrue(boundaryDistanceMeters in 990.0..1010.0)
    }

    @Test
    fun sectorIsInsideArea_respectsMeterRadius() {
        val center = AATLatLng(0.0, 0.0)
        val insidePoint = AATMathUtils.calculatePointAtBearing(center, 90.0, 0.5)
        val outsidePoint = AATMathUtils.calculatePointAtBearing(center, 90.0, 1.5)

        assertTrue(
            sectorCalculator.isInsideArea(
                point = insidePoint,
                center = center,
                innerRadius = null,
                outerRadius = 1000.0,
                startBearing = 0.0,
                endBearing = 180.0
            )
        )
        assertFalse(
            sectorCalculator.isInsideArea(
                point = outsidePoint,
                center = center,
                innerRadius = null,
                outerRadius = 1000.0,
                startBearing = 0.0,
                endBearing = 180.0
            )
        )
    }

    @Test
    fun sectorOptimalTouchPoint_usesMeterRadius() {
        val center = AATLatLng(0.0, 0.0)
        val approachFrom = AATMathUtils.calculatePointAtBearing(center, 270.0, 5.0)
        val exitTo = AATMathUtils.calculatePointAtBearing(center, 90.0, 5.0)

        val touchPoint = sectorCalculator.calculateOptimalTouchPoint(
            center = center,
            innerRadius = null,
            outerRadius = 1000.0,
            startBearing = 0.0,
            endBearing = 180.0,
            approachFrom = approachFrom,
            exitTo = exitTo
        )
        val touchDistanceMeters = AATMathUtils.calculateDistanceMeters(center, touchPoint)

        assertTrue(touchDistanceMeters in 990.0..1010.0)
    }

    @Test
    fun sectorNearestPointOnBoundary_usesMeterRadius() {
        val center = AATLatLng(0.0, 0.0)
        val from = AATMathUtils.calculatePointAtBearing(center, 90.0, 2.0)

        val nearest = sectorCalculator.nearestPointOnBoundary(
            from = from,
            center = center,
            innerRadius = null,
            outerRadius = 1000.0,
            startBearing = 0.0,
            endBearing = 180.0
        )
        val nearestDistanceMeters = AATMathUtils.calculateDistanceMeters(center, nearest)

        assertTrue(nearestDistanceMeters in 990.0..1010.0)
    }

    @Test
    fun sectorGenerateBoundaryPoints_usesMeterRadius() {
        val center = AATLatLng(0.0, 0.0)

        val points = sectorCalculator.generateBoundaryPoints(
            center = center,
            innerRadius = null,
            outerRadius = 1000.0,
            startBearing = 0.0,
            endBearing = 90.0,
            numArcPoints = 12
        )

        val firstBoundaryDistanceMeters =
            AATMathUtils.calculateDistanceMeters(center, points.first())

        assertTrue(firstBoundaryDistanceMeters in 990.0..1010.0)
    }
}
