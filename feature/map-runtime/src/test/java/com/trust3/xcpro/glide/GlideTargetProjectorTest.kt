package com.trust3.xcpro.glide

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.RacingFinishCustomParams
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.trust3.xcpro.tasks.navigation.NavigationRouteKind
import com.trust3.xcpro.tasks.navigation.NavigationRoutePoint
import com.trust3.xcpro.tasks.navigation.NavigationRouteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideTargetProjectorTest {

    private val projector = GlideTargetProjector()

    @Test
    fun project_preserves_canonical_route_points_for_valid_finish_target() {
        val snapshot = projector.project(
            taskSnapshot = racingTaskSnapshot(),
            route = NavigationRouteSnapshot(
                kind = NavigationRouteKind.TASK_FINISH,
                label = "Boundary Finish",
                remainingWaypoints = listOf(
                    NavigationRoutePoint(lat = 0.0, lon = 0.08, label = "Boundary Finish")
                ),
                valid = true
            )
        )

        assertTrue(snapshot.valid)
        assertEquals(0.08, snapshot.remainingWaypoints.single().lon, 0.0)
        assertEquals(900.0, snapshot.finishConstraint?.requiredAltitudeMeters ?: Double.NaN, 0.0)
    }

    @Test
    fun project_returns_prestart_from_route_status() {
        val snapshot = projector.project(
            taskSnapshot = racingTaskSnapshot(),
            route = NavigationRouteSnapshot(
                kind = NavigationRouteKind.TASK_FINISH,
                label = "Finish",
                remainingWaypoints = listOf(
                    NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                ),
                valid = false,
                invalidReason = NavigationRouteInvalidReason.PRESTART
            )
        )

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.PRESTART, snapshot.invalidReason)
    }

    @Test
    fun project_requires_finish_altitude_rule_for_valid_route() {
        val snapshot = projector.project(
            taskSnapshot = racingTaskSnapshot(finishMinAltitudeMeters = null),
            route = NavigationRouteSnapshot(
                kind = NavigationRouteKind.TASK_FINISH,
                label = "Finish",
                remainingWaypoints = listOf(
                    NavigationRoutePoint(lat = 0.0, lon = 0.1, label = "Finish")
                ),
                valid = true
            )
        )

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.NO_FINISH_ALTITUDE, snapshot.invalidReason)
    }

    @Test
    fun project_returns_invalid_route_when_constraint_exists_but_route_is_empty() {
        val snapshot = projector.project(
            taskSnapshot = racingTaskSnapshot(),
            route = NavigationRouteSnapshot(
                kind = NavigationRouteKind.TASK_FINISH,
                label = "Finish",
                remainingWaypoints = emptyList(),
                valid = false,
                invalidReason = NavigationRouteInvalidReason.INVALID_ROUTE
            )
        )

        assertFalse(snapshot.valid)
        assertEquals(GlideInvalidReason.INVALID_ROUTE, snapshot.invalidReason)
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
