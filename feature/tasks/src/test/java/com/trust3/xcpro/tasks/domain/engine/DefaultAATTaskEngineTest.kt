package com.trust3.xcpro.tasks.domain.engine

import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.TaskWaypointParamKeys
import com.trust3.xcpro.tasks.core.WaypointRole
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAATTaskEngineTest {

    @Test
    fun `add and reorder keep aat role invariants`() {
        val engine = DefaultAATTaskEngine()

        engine.addWaypoint(waypoint("a", 0.0, 0.0))
        engine.addWaypoint(waypoint("b", 0.0, 0.5))
        engine.addWaypoint(waypoint("c", 0.0, 1.0))

        val initial = engine.state.value.base.task.waypoints
        assertEquals(listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.FINISH), initial.map { it.role })
        assertTrue(engine.state.value.base.isTaskValid)

        engine.reorderWaypoints(2, 1)
        val reordered = engine.state.value.base.task.waypoints
        assertEquals(listOf("a", "c", "b"), reordered.map { it.id })
        assertEquals(listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.FINISH), reordered.map { it.role })
    }

    @Test
    fun `update target point and area radius writes into task state`() {
        val engine = DefaultAATTaskEngine()
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("tp", 0.0, 0.5))
        engine.addWaypoint(waypoint("finish", 0.0, 1.0))

        engine.updateTargetPoint(index = 1, lat = 45.1, lon = 7.2)
        engine.updateAreaRadiusMeters(index = 1, radiusMeters = 15000.0)

        val waypoint = engine.state.value.base.task.waypoints[1]
        assertEquals(45.1, (waypoint.customParameters[TaskWaypointParamKeys.TARGET_LAT] as Double), 0.0)
        assertEquals(7.2, (waypoint.customParameters[TaskWaypointParamKeys.TARGET_LON] as Double), 0.0)
        assertEquals(15_000.0, waypoint.customRadiusMeters ?: 0.0, 1e-9)
        assertNull(waypoint.customRadius)
    }

    @Test
    fun `minimum time zero invalidates task`() {
        val engine = DefaultAATTaskEngine()
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("finish", 0.1, 0.1))

        engine.updateParameters(Duration.ZERO, Duration.ofHours(1))

        assertEquals(TaskType.AAT, engine.state.value.base.taskType)
        assertFalse(engine.state.value.base.isTaskValid)
    }

    @Test
    fun `geometry-based task distance is positive`() {
        val engine = DefaultAATTaskEngine()
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("tp", 0.0, 0.5))
        engine.addWaypoint(waypoint("finish", 0.0, 1.0))

        val distanceMeters = engine.calculateTaskDistanceMeters()

        assertTrue(distanceMeters > 1000.0)
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole = WaypointRole.TURNPOINT
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role
    )
}
