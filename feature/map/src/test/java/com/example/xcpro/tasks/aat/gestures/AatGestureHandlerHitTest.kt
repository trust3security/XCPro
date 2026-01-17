package com.example.xcpro.tasks.aat.gestures

import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AatGestureHandlerHitTest {

    @Test
    fun `returns null when no turnpoint within radius`() {
        val waypoints = listOf(
            waypoint(id = "tp-1", lat = 10.0, lon = 10.0, role = WaypointRole.TURNPOINT)
        )

        val hit = findAatWaypointHitForMapPoint(mapLat = 0.0, mapLon = 0.0, waypoints = waypoints)

        assertNull(hit)
    }

    @Test
    fun `ignores non turnpoints even if within radius`() {
        val waypoints = listOf(
            waypoint(id = "start", lat = 0.0, lon = 0.0, role = WaypointRole.START),
            waypoint(id = "finish", lat = 0.0, lon = 0.0, role = WaypointRole.FINISH)
        )

        val hit = findAatWaypointHitForMapPoint(mapLat = 0.0, mapLon = 0.0, waypoints = waypoints)

        assertNull(hit)
    }

    @Test
    fun `picks closest turnpoint when overlapping`() {
        val waypoints = listOf(
            waypoint(id = "tp-1", lat = 0.02, lon = 0.0, role = WaypointRole.TURNPOINT),
            waypoint(id = "tp-2", lat = 0.05, lon = 0.0, role = WaypointRole.TURNPOINT)
        )

        val hit = findAatWaypointHitForMapPoint(mapLat = 0.0, mapLon = 0.0, waypoints = waypoints)

        assertEquals(0, hit)
    }

    @Test
    fun `uses custom radius for hit detection`() {
        val waypoints = listOf(
            waypoint(id = "tp-1", lat = 0.03, lon = 0.0, role = WaypointRole.TURNPOINT, radiusKm = 1.0)
        )

        val hit = findAatWaypointHitForMapPoint(mapLat = 0.0, mapLon = 0.0, waypoints = waypoints)

        assertNull(hit)
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        radiusKm: Double? = null
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role,
        customRadius = radiusKm
    )
}
