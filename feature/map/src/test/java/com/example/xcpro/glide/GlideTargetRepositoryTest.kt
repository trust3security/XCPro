package com.example.xcpro.glide

import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.example.xcpro.tasks.navigation.NavigationRouteKind
import com.example.xcpro.tasks.navigation.NavigationRoutePoint
import com.example.xcpro.tasks.navigation.NavigationRouteSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTargetRepositoryTest {

    @Test
    fun finishTarget_uses_canonical_route_seam_points() = runTest {
        val repository = GlideTargetRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Boundary Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.08, label = "Boundary Finish")
                    ),
                    valid = true
                )
            ),
            glideTargetProjector = GlideTargetProjector()
        )

        val snapshot = repository.finishTarget.first()

        assertTrue(snapshot.valid)
        assertEquals("Boundary Finish", snapshot.label)
        assertEquals(0.08, snapshot.remainingWaypoints.single().lon, 0.0)
        assertEquals(900.0, snapshot.finishConstraint?.requiredAltitudeMeters ?: Double.NaN, 0.0)
    }

    @Test
    fun finishTarget_emits_prestart_from_canonical_route_status() = runTest {
        val repository = GlideTargetRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                    ),
                    valid = false,
                    invalidReason = NavigationRouteInvalidReason.PRESTART
                )
            ),
            glideTargetProjector = GlideTargetProjector()
        )

        val snapshot = repository.finishTarget.first()

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.PRESTART, snapshot.invalidReason)
        assertEquals(1, snapshot.remainingWaypoints.size)
    }

    @Test
    fun finishTarget_requires_finish_altitude_rule_from_task_runtime() = runTest {
        val repository = GlideTargetRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(finishMinAltitudeMeters = null)),
            routeFlow = MutableStateFlow(
                NavigationRouteSnapshot(
                    kind = NavigationRouteKind.TASK_FINISH,
                    label = "Finish",
                    remainingWaypoints = listOf(
                        NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                    ),
                    valid = true
                )
            ),
            glideTargetProjector = GlideTargetProjector()
        )

        val snapshot = repository.finishTarget.first()

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
