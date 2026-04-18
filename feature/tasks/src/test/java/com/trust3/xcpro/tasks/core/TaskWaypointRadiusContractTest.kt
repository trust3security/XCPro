package com.trust3.xcpro.tasks.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskWaypointRadiusContractTest {

    @Test
    fun `meter radius is canonical and takes precedence over legacy km field`() {
        val waypoint = waypoint(customRadius = 3.0, customRadiusMeters = 4200.0)

        assertEquals(4200.0, waypoint.resolvedCustomRadiusMeters() ?: Double.NaN, 1e-9)
    }

    @Test
    fun `legacy km radius is accepted only as compatibility fallback`() {
        val waypoint = waypoint(customRadius = 2.5, customRadiusMeters = null)

        assertEquals(2500.0, waypoint.resolvedCustomRadiusMeters() ?: Double.NaN, 1e-9)
    }

    @Test
    fun `with custom radius meters keeps meter field canonical and clears legacy km field`() {
        val updated = waypoint().withCustomRadiusMeters(1500.0)

        assertEquals(1500.0, updated.customRadiusMeters ?: Double.NaN, 1e-9)
        assertNull(updated.customRadius)
    }

    @Test
    fun `null radius remains unset`() {
        val waypoint = waypoint(customRadius = null, customRadiusMeters = null)

        assertNull(waypoint.resolvedCustomRadiusMeters())
    }

    private fun waypoint(
        customRadius: Double? = null,
        customRadiusMeters: Double? = null
    ): TaskWaypoint = TaskWaypoint(
        id = "wp",
        title = "WP",
        subtitle = "",
        lat = 0.0,
        lon = 0.0,
        role = WaypointRole.TURNPOINT,
        customRadius = customRadius,
        customRadiusMeters = customRadiusMeters
    )
}
