package com.example.xcpro.tasks.domain

import com.example.xcpro.tasks.TaskRepository
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryEnvelopeTest {

    private val repo = TaskRepository(
        validator = TaskValidator()
    )

    private fun wp(id: String, lat: Double, lon: Double) = TaskWaypoint(
        id = id,
        title = id,
        subtitle = "",
        lat = lat,
        lon = lon,
        role = WaypointRole.TURNPOINT
    )

    @Test
    fun `racing nominal equals min equals max`() {
        val task = Task(
            id = "r1",
            waypoints = listOf(
                wp("s", 0.0, 0.0).copy(role = WaypointRole.START),
                wp("t", 0.0, 0.05),
                wp("f", 0.0, 0.1).copy(role = WaypointRole.FINISH)
            )
        )

        repo.updateFrom(task, TaskType.RACING)
        val stats = repo.state.value.stats

        // 0.05 deg lon at equator ~= 5565 m
        assertEquals(stats.distanceNominal, stats.distanceMin, 1e-3)
        assertEquals(stats.distanceNominal, stats.distanceMax, 1e-3)
    }

    @Test
    fun `aat min is reduced by OZ radii`() {
        val task = Task(
            id = "aat1",
            waypoints = listOf(
                wp("s", 0.0, 0.0).copy(role = WaypointRole.START),
                wp("t", 0.0, 0.05),
                wp("f", 0.0, 0.1).copy(role = WaypointRole.FINISH)
            )
        )

        repo.updateFrom(task, TaskType.AAT)
        val stats = repo.state.value.stats

        // Nominal ~11.1 km, min should be smaller due to 5km+5km envelope
        assert(stats.distanceMin < stats.distanceNominal)
        assert(stats.distanceMax > stats.distanceNominal)
    }
}
