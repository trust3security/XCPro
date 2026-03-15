package com.example.xcpro.glide

import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTargetRepositoryTest {

    @Test
    fun resolveFinishTarget_returns_prestart_until_racing_task_has_started() {
        val snapshot = resolveFinishTarget(
            taskSnapshot = racingTaskSnapshot(),
            navigationState = RacingNavigationState(status = RacingNavigationStatus.PENDING_START)
        )

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.PRESTART, snapshot.invalidReason)
        assertEquals(2, snapshot.remainingWaypoints.size)
    }

    @Test
    fun resolveFinishTarget_returns_remaining_route_for_started_racing_task() {
        val snapshot = resolveFinishTarget(
            taskSnapshot = racingTaskSnapshot(),
            navigationState = RacingNavigationState(
                status = RacingNavigationStatus.IN_PROGRESS,
                currentLegIndex = 1
            )
        )

        assertTrue(snapshot.valid)
        assertEquals(GlideTargetKind.TASK_FINISH, snapshot.kind)
        assertEquals("Finish", snapshot.label)
        assertEquals(2, snapshot.remainingWaypoints.size)
        assertEquals(900.0, snapshot.finishConstraint?.requiredAltitudeMeters ?: Double.NaN, 0.0)
    }

    @Test
    fun resolveFinishTarget_requires_finish_altitude_rule_in_mvp() {
        val snapshot = resolveFinishTarget(
            taskSnapshot = racingTaskSnapshot(finishMinAltitudeMeters = null),
            navigationState = RacingNavigationState(
                status = RacingNavigationStatus.STARTED,
                currentLegIndex = 1
            )
        )

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.NO_FINISH_ALTITUDE, snapshot.invalidReason)
    }

    private fun racingTaskSnapshot(finishMinAltitudeMeters: Double? = 900.0): TaskRuntimeSnapshot {
        val finishParams = mutableMapOf<String, Any>()
        RacingFinishCustomParams(minAltitudeMeters = finishMinAltitudeMeters).applyTo(finishParams)
        return TaskRuntimeSnapshot(
            task = Task(
                id = "task-1",
                waypoints = listOf(
                    waypoint("start", 0.0, 0.0, WaypointRole.START),
                    waypoint("tp1", 0.0, 0.05, WaypointRole.TURNPOINT),
                    waypoint("finish", 0.0, 0.1, WaypointRole.FINISH, finishParams)
                )
            ),
            taskType = TaskType.RACING,
            activeLeg = 0
        )
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole,
        customParameters: Map<String, Any> = emptyMap()
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id.replaceFirstChar(Char::titlecase),
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role,
        customParameters = customParameters
    )
}
