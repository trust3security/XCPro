package com.trust3.xcpro.tasks.domain

import com.trust3.xcpro.tasks.TaskRepository
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.TaskWaypointParamKeys
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.domain.logic.TaskValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TaskRepositoryTargetStateTest {
    private val repository = TaskRepository(
        validator = TaskValidator()
    )

    @Test
    fun `projection preserves active leg from canonical snapshot input`() {
        repository.updateFrom(taskWithLockedTarget(), TaskType.AAT, activeIndex = 2)

        assertEquals(2, repository.state.value.stats.activeIndex)
    }

    @Test
    fun `locked target projection preserves canonical target coordinates`() {
        repository.updateFrom(taskWithLockedTarget(), TaskType.AAT, activeIndex = 1)

        val target = repository.state.value.targets[1].target!!
        assertEquals(45.11, target.lat, 1e-9)
        assertEquals(7.11, target.lon, 1e-9)
        assertEquals(true, repository.state.value.targets[1].isLocked)
        assertEquals(0.67, repository.state.value.targets[1].targetParam, 1e-9)
    }

    @Test
    fun `unlocked target projection derives target from canonical param`() {
        repository.updateFrom(taskWithUnlockedParam(), TaskType.AAT, activeIndex = 1)

        val target = repository.state.value.targets[1].target!!
        assertEquals(false, repository.state.value.targets[1].isLocked)
        assertNotEquals(45.0, target.lat, 1e-9)
        assertNotEquals(7.05, target.lon, 1e-9)
        assertEquals(0.25, repository.state.value.targets[1].targetParam, 1e-9)
    }

    private fun taskWithLockedTarget(): Task = Task(
        id = "aat-locked",
        waypoints = listOf(
            waypoint("start", 45.0, 7.0, WaypointRole.START),
            waypoint(
                "tp-1",
                45.05,
                7.05,
                WaypointRole.TURNPOINT,
                customParameters = mapOf(
                    TaskWaypointParamKeys.TARGET_PARAM to 0.67,
                    TaskWaypointParamKeys.TARGET_LOCKED to true,
                    TaskWaypointParamKeys.TARGET_LAT to 45.11,
                    TaskWaypointParamKeys.TARGET_LON to 7.11
                )
            ),
            waypoint("finish", 45.1, 7.1, WaypointRole.FINISH)
        )
    )

    private fun taskWithUnlockedParam(): Task = Task(
        id = "aat-unlocked",
        waypoints = listOf(
            waypoint("start", 45.0, 7.0, WaypointRole.START),
            waypoint(
                "tp-1",
                45.05,
                7.05,
                WaypointRole.TURNPOINT,
                customParameters = mapOf(
                    TaskWaypointParamKeys.TARGET_PARAM to 0.25,
                    TaskWaypointParamKeys.TARGET_LOCKED to false
                )
            ),
            waypoint("finish", 45.1, 7.1, WaypointRole.FINISH)
        )
    )

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id,
        subtitle = id,
        lat = lat,
        lon = lon,
        role = role,
        customParameters = customParameters
    )
}
