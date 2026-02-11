package com.example.xcpro.tasks.domain.engine

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRacingTaskEngineTest {

    @Test
    fun `add remove reorder keeps racing role invariants`() {
        val engine = DefaultRacingTaskEngine()

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

        engine.removeWaypoint(1)
        val afterRemove = engine.state.value.base.task.waypoints
        assertEquals(listOf("a", "b"), afterRemove.map { it.id })
        assertEquals(listOf(WaypointRole.START, WaypointRole.FINISH), afterRemove.map { it.role })
    }

    @Test
    fun `distance and segment calculations are positive`() {
        val engine = DefaultRacingTaskEngine()
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("tp", 0.0, 0.5))
        engine.addWaypoint(waypoint("finish", 0.0, 1.0))

        val taskDistance = engine.state.value.taskDistanceMeters
        assertTrue(taskDistance > 1000.0)

        val from = waypoint("x", 0.0, 0.0, role = WaypointRole.START)
        val to = waypoint("y", 0.0, 1.0, role = WaypointRole.TURNPOINT)
        val forward = engine.calculateSegmentDistanceMeters(from, to)
        val reverse = engine.calculateSegmentDistanceMeters(to, from)
        assertTrue(forward > 100000.0)
        assertEquals(forward, reverse, 1e-6)
    }

    @Test
    fun `setActiveLeg clamps to valid range`() {
        val engine = DefaultRacingTaskEngine()
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("finish", 0.1, 0.1))

        engine.setActiveLeg(99)

        assertEquals(TaskType.RACING, engine.state.value.base.taskType)
        assertEquals(1, engine.state.value.base.activeLegIndex)
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
