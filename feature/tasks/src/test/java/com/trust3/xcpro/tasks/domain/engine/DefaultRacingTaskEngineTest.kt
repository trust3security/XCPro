package com.trust3.xcpro.tasks.domain.engine

import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.RacingTaskStructureRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRacingTaskEngineTest {

    @Test
    fun `add remove reorder keeps racing role invariants`() {
        val engine = DefaultRacingTaskEngine()

        engine.addWaypoint(waypoint("a", 0.0, 0.0))
        engine.addWaypoint(waypoint("b", 0.0, 0.5))
        engine.addWaypoint(waypoint("c", 0.0, 0.8))
        engine.addWaypoint(waypoint("d", 0.0, 1.0))

        val initial = engine.state.value.base.task.waypoints
        assertEquals(
            listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.TURNPOINT, WaypointRole.FINISH),
            initial.map { it.role }
        )
        assertTrue(engine.state.value.base.isTaskValid)

        engine.reorderWaypoints(3, 1)
        val reordered = engine.state.value.base.task.waypoints
        assertEquals(listOf("a", "d", "b", "c"), reordered.map { it.id })
        assertEquals(
            listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.TURNPOINT, WaypointRole.FINISH),
            reordered.map { it.role }
        )

        engine.removeWaypoint(1)
        val afterRemove = engine.state.value.base.task.waypoints
        assertEquals(listOf("a", "b", "c"), afterRemove.map { it.id })
        assertEquals(listOf(WaypointRole.START, WaypointRole.TURNPOINT, WaypointRole.FINISH), afterRemove.map { it.role })
        assertEquals(false, engine.state.value.base.isTaskValid)
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
        engine.addWaypoint(waypoint("tp1", 0.0, 0.03))
        engine.addWaypoint(waypoint("tp2", 0.0, 0.06))
        engine.addWaypoint(waypoint("finish", 0.1, 0.1))

        engine.setActiveLeg(99)

        assertEquals(TaskType.RACING, engine.state.value.base.taskType)
        assertEquals(3, engine.state.value.base.activeLegIndex)
    }

    @Test
    fun `extended profile can opt in to short racing task validity`() {
        val engine = DefaultRacingTaskEngine(
            validationProfile = RacingTaskStructureRules.Profile.XC_PRO_EXTENDED
        )
        engine.addWaypoint(waypoint("start", 0.0, 0.0))
        engine.addWaypoint(waypoint("finish", 0.1, 0.1))

        assertTrue(engine.state.value.base.isTaskValid)
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
