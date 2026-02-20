package com.example.xcpro.tasks.domain

import com.example.xcpro.tasks.TaskRepository
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.domain.logic.TaskValidator
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRepositoryTargetStateTest {
    private val repository = TaskRepository(
        validator = TaskValidator(),
        proximityEvaluator = TaskProximityEvaluator()
    )

    @Test
    fun `target mutations preserve active leg`() {
        repository.updateFrom(taskWithUniqueIds(), TaskType.AAT, activeIndex = 2)

        repository.setTargetParam(index = 1, param = 0.20)
        assertEquals(2, repository.state.value.stats.activeIndex)

        repository.toggleTargetLock(index = 1)
        assertEquals(2, repository.state.value.stats.activeIndex)

        repository.setTargetLock(index = 1, locked = false)
        assertEquals(2, repository.state.value.stats.activeIndex)
    }

    @Test
    fun `locked target does not move when param changes`() {
        repository.updateFrom(taskWithUniqueIds(), TaskType.AAT, activeIndex = 1)
        repository.setTargetParam(index = 1, param = 0.15)
        repository.setTargetLock(index = 1, locked = true)
        val before = repository.state.value.targets[1].target!!

        repository.setTargetParam(index = 1, param = 0.85)
        val after = repository.state.value.targets[1].target!!

        assertEquals(before.lat, after.lat, 1e-9)
        assertEquals(before.lon, after.lon, 1e-9)
    }

    @Test
    fun `duplicate waypoint ids do not alias target memory across indices`() {
        repository.updateFrom(taskWithDuplicateTurnpointIds(), TaskType.AAT, activeIndex = 1)

        repository.setTargetParam(index = 1, param = 0.10)
        val indexOneBefore = repository.state.value.targets[1].target!!

        repository.setTargetParam(index = 2, param = 0.90)
        val indexOneAfter = repository.state.value.targets[1].target!!

        assertEquals(indexOneBefore.lat, indexOneAfter.lat, 1e-9)
        assertEquals(indexOneBefore.lon, indexOneAfter.lon, 1e-9)
    }

    private fun taskWithUniqueIds(): Task = Task(
        id = "aat-unique",
        waypoints = listOf(
            waypoint("start", 0.0, 0.0, WaypointRole.START),
            waypoint("tp-1", 0.0, 0.05, WaypointRole.TURNPOINT),
            waypoint("tp-2", 0.0, 0.10, WaypointRole.TURNPOINT),
            waypoint("finish", 0.0, 0.15, WaypointRole.FINISH)
        )
    )

    private fun taskWithDuplicateTurnpointIds(): Task = Task(
        id = "aat-duplicate",
        waypoints = listOf(
            waypoint("start", 0.0, 0.0, WaypointRole.START),
            waypoint("dup", 0.0, 0.05, WaypointRole.TURNPOINT),
            waypoint("dup", 0.0, 0.10, WaypointRole.TURNPOINT),
            waypoint("tp-3", 0.0, 0.15, WaypointRole.TURNPOINT),
            waypoint("finish", 0.0, 0.20, WaypointRole.FINISH)
        )
    )

    private fun waypoint(id: String, lat: Double, lon: Double, role: WaypointRole): TaskWaypoint {
        return TaskWaypoint(
            id = id,
            title = id,
            subtitle = id,
            lat = lat,
            lon = lon,
            role = role
        )
    }
}
