package com.example.xcpro.tasks.domain

import com.example.xcpro.tasks.TaskRepository
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskWaypointParamKeys
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRepositoryProjectionComplianceTest {
    private val repository = TaskRepository(
        validator = TaskValidator()
    )

    @Test
    fun `aat projection preserves explicit roles for target-capable validation`() {
        val task = Task(
            id = "aat-role-contract",
            waypoints = listOf(
                waypoint("wp0", 0.0, 0.0, WaypointRole.START),
                waypoint("wp1", 0.0, 0.05, WaypointRole.START),
                waypoint("wp2", 0.0, 0.10, WaypointRole.FINISH)
            )
        )

        repository.updateFrom(task, TaskType.AAT)

        assertTrue(
            repository.state.value.validationErrors.contains(
                TaskValidator.ValidationError.AATRequiresAdjustablePoint
            )
        )
    }

    @Test
    fun `racing projection honors persisted observation-zone override`() {
        val annularOZ = mapOf<String, Any>(
            TaskWaypointParamKeys.OZ_TYPE to "ANNULAR_SECTOR",
            TaskWaypointParamKeys.OZ_PARAMS to mapOf(
                TaskWaypointParamKeys.INNER_RADIUS_METERS to 300.0,
                TaskWaypointParamKeys.OUTER_RADIUS_METERS to 1200.0,
                TaskWaypointParamKeys.OZ_ANGLE_DEG to 90.0
            )
        )
        val task = Task(
            id = "racing-oz-contract",
            waypoints = listOf(
                waypoint("start", 0.0, 0.0, WaypointRole.START),
                waypoint("tp", 0.0, 0.05, WaypointRole.TURNPOINT, annularOZ),
                waypoint("finish", 0.0, 0.10, WaypointRole.FINISH)
            )
        )

        repository.updateFrom(task, TaskType.RACING)

        val invalidOZ = repository.state.value.validationErrors
            .filterIsInstance<TaskValidator.ValidationError.InvalidObservationZone>()
        assertTrue(
            invalidOZ.any { error ->
                error.role == WaypointRole.TURNPOINT && error.zone == "ANNULAR_SECTOR"
            }
        )
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint {
        return TaskWaypoint(
            id = id,
            title = id,
            subtitle = id,
            lat = lat,
            lon = lon,
            role = role,
            customParameters = customParameters
        )
    }
}
