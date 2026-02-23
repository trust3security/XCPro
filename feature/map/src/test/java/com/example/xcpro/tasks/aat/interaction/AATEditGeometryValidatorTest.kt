package com.example.xcpro.tasks.aat.interaction

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.map.AATMovablePointManager
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AATEditGeometryValidatorTest {

    private val validator = AATEditGeometryValidator()
    private val movablePointManager = AATMovablePointManager()

    @Test
    fun `clamp keeps cylinder inside radius`() {
        val waypoint = waypoint(
            shape = AATAreaShape.CIRCLE,
            radiusMeters = 10_000.0
        )
        val outside = AATMathUtils.calculatePointAtBearing(AATLatLng(0.0, 0.0), 0.0, 20.0)

        val result = validator.clampTarget(waypoint, outside)

        val distMeters = AATMathUtils.calculateDistanceMeters(
            waypoint.lat, waypoint.lon,
            result.targetPoint.latitude, result.targetPoint.longitude
        )
        assertTrue("Distance should be clamped to <= 10km", distMeters <= 10_001.0)
    }

    @Test
    fun `clamp keeps sector angle within wedge`() {
        val waypoint = waypoint(
            shape = AATAreaShape.SECTOR,
            innerRadiusMeters = 0.0,
            outerRadiusMeters = 10_000.0,
            startAngleDegrees = 0.0,
            endAngleDegrees = 60.0
        )
        val outsideAngle = AATMathUtils.calculatePointAtBearing(AATLatLng(0.0, 0.0), 120.0, 5.0)

        val result = validator.clampTarget(waypoint, outsideAngle)
        val bearing = AATMathUtils.calculateBearing(AATLatLng(waypoint.lat, waypoint.lon), result.targetPoint)

        assertTrue(
            "Bearing should be clamped inside sector",
            bearing in -1.0..61.0 // allow tiny numeric drift
        )
    }

    @Test
    fun `clamp accepts keyhole inner cylinder even outside sector`() {
        val waypoint = waypoint(
            shape = AATAreaShape.SECTOR,
            innerRadiusMeters = 1_000.0,
            outerRadiusMeters = 10_000.0,
            startAngleDegrees = 0.0,
            endAngleDegrees = 90.0,
            turnPointType = AATTurnPointType.AAT_KEYHOLE
        )
        val pointOutsideAngle = AATMathUtils.calculatePointAtBearing(AATLatLng(0.0, 0.0), 180.0, 0.5)

        val result = validator.clampTarget(waypoint, pointOutsideAngle)

        assertTrue(
            "Result should be inside area (inner cylinder or sector)",
            movablePointManager.isPointInsideArea(waypoint, result.targetPoint)
        )
    }

    private fun waypoint(
        shape: AATAreaShape,
        radiusMeters: Double = 0.0,
        innerRadiusMeters: Double = 0.0,
        outerRadiusMeters: Double = 0.0,
        startAngleDegrees: Double = 0.0,
        endAngleDegrees: Double = 0.0,
        turnPointType: AATTurnPointType = AATTurnPointType.AAT_SECTOR
    ) = AATWaypoint(
        id = "wp",
        title = "wp",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = AATWaypointRole.TURNPOINT,
        assignedArea = AATAssignedArea(
            shape = shape,
            radiusMeters = radiusMeters,
            innerRadiusMeters = innerRadiusMeters,
            outerRadiusMeters = outerRadiusMeters,
            startAngleDegrees = startAngleDegrees,
            endAngleDegrees = endAngleDegrees
        ),
        turnPointType = turnPointType
    )
}
