package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.AATInteractiveTurnpointManager
import com.example.xcpro.tasks.aat.AATManagerCallbacks
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATTurnPointType
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.core.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class AATInteractiveTurnpointManagerValidationTest {

    private lateinit var manager: AATInteractiveTurnpointManager
    private lateinit var keyholeWaypoint: AATWaypoint

    @Before
    fun setup() {
        manager = AATInteractiveTurnpointManager(AATManagerCallbacks(), FakeClock())

        keyholeWaypoint = AATWaypoint(
            id = "wp1",
            title = "Keyhole TP",
            subtitle = "",
            lat = 0.0,
            lon = 0.0,
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

        manager.updateWaypoints(listOf(keyholeWaypoint))
    }

    @Test
    fun `clamps target update outside keyhole sector to nearest valid point`() {
        val outside = AATMathUtils.calculatePointAtBearing(
            AATLatLng(0.0, 0.0),
            180.0, // outside the 0-90 sector
            10.0
        )

        manager.updateWaypointTargetPoint(0, outside)

        val stored = getCurrentWaypoint()
        val tp = stored.targetPoint

        // Should not equal the original invalid outside point
        assertFalse(outside.latitude == tp.latitude && outside.longitude == tp.longitude)

        // Geometry-based assert: within outer radius and (inside angle or inside inner cylinder)
        val distance = AATMathUtils.calculateDistanceKm(0.0, 0.0, tp.latitude, tp.longitude)
        assertTrue(distance <= stored.assignedArea.outerRadiusMeters / 1000.0 + 1e-6)
        val bearing = AATMathUtils.calculateBearing(AATLatLng(0.0, 0.0), tp)
        val innerRadiusKm = stored.assignedArea.innerRadiusMeters / 1000.0
        val inInner = distance <= innerRadiusKm
        val inAngle = com.example.xcpro.tasks.aat.map.AATMovablePointManager()
            .let { mgr ->
                val method = mgr.javaClass.getDeclaredMethod(
                    "isAngleInSector",
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType,
                    Double::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(mgr, bearing, stored.assignedArea.startAngleDegrees, stored.assignedArea.endAngleDegrees) as Boolean
            }
        assertTrue("Resulting point should be inside keyhole sector or inner cylinder", inInner || inAngle)
    }

    @Test
    fun `accepts target update inside keyhole`() {
        val inside = AATMathUtils.calculatePointAtBearing(
            AATLatLng(0.0, 0.0),
            45.0,
            10.0
        )

        manager.updateWaypointTargetPoint(0, inside)

        val stored = getCurrentWaypoint()
        assertEquals(inside.latitude, stored.targetPoint.latitude, 1e-9)
        assertEquals(inside.longitude, stored.targetPoint.longitude, 1e-9)
        assertTrue(stored.isTargetPointCustomized)
    }

    private fun getCurrentWaypoint(): AATWaypoint =
        manager.getCurrentWaypoints()[0]
}
