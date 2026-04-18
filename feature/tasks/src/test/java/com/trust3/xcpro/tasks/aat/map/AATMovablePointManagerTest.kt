package com.trust3.xcpro.tasks.aat.map

import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATAreaShape
import com.trust3.xcpro.tasks.aat.models.AATAssignedArea
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATTurnPointType
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AATMovablePointManagerTest {

    private val manager = AATMovablePointManager()
    private val origin = AATLatLng(0.0, 0.0)

    @Test
    fun `cylinder accepts inside point and rejects outside`() {
        val waypoint = AATWaypoint(
            id = "cyl",
            title = "Cylinder",
            subtitle = "",
            lat = origin.latitude,
            lon = origin.longitude,
            role = AATWaypointRole.TURNPOINT,
            assignedArea = AATAssignedArea(
                shape = AATAreaShape.CIRCLE,
                radiusMeters = 10_000.0
            ),
            turnPointType = AATTurnPointType.AAT_CYLINDER
        )

        val inside = AATMathUtils.calculatePointAtBearing(origin, 45.0, 5.0)
        val outside = AATMathUtils.calculatePointAtBearing(origin, 45.0, 15.0)

        assertTrue(manager.isPointInsideArea(waypoint, inside))
        assertFalse(manager.isPointInsideArea(waypoint, outside))
    }

    @Test
    fun `sector enforces angular limits`() {
        val waypoint = AATWaypoint(
            id = "sector",
            title = "Sector",
            subtitle = "",
            lat = origin.latitude,
            lon = origin.longitude,
            role = AATWaypointRole.TURNPOINT,
            assignedArea = AATAssignedArea(
                shape = AATAreaShape.SECTOR,
                radiusMeters = 20_000.0,
                innerRadiusMeters = 0.0,
                outerRadiusMeters = 20_000.0,
                startAngleDegrees = 0.0,
                endAngleDegrees = 90.0
            ),
            turnPointType = AATTurnPointType.AAT_SECTOR
        )

        val inside = AATMathUtils.calculatePointAtBearing(origin, 45.0, 10.0)
        val outsideAngle = AATMathUtils.calculatePointAtBearing(origin, 180.0, 10.0)

        assertTrue(manager.isPointInsideArea(waypoint, inside))
        assertFalse(manager.isPointInsideArea(waypoint, outsideAngle))
    }

    @Test
    fun `keyhole allows inner cylinder even when outside sector`() {
        val waypoint = AATWaypoint(
            id = "keyhole",
            title = "Keyhole",
            subtitle = "",
            lat = origin.latitude,
            lon = origin.longitude,
            role = AATWaypointRole.TURNPOINT,
            assignedArea = AATAssignedArea(
                shape = AATAreaShape.SECTOR,
                radiusMeters = 20_000.0,
                innerRadiusMeters = 500.0,
                outerRadiusMeters = 20_000.0,
                startAngleDegrees = 0.0,
                endAngleDegrees = 90.0
            ),
            turnPointType = AATTurnPointType.AAT_KEYHOLE
        )

        val insideInner = AATMathUtils.calculatePointAtBearing(origin, 180.0, 0.3)
        val outsideSector = AATMathUtils.calculatePointAtBearing(origin, 180.0, 10.0)

        assertTrue(manager.isPointInsideArea(waypoint, insideInner))
        assertFalse(manager.isPointInsideArea(waypoint, outsideSector))
    }
}
