package com.trust3.xcpro.tasks.navigation

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import com.trust3.xcpro.tasks.racing.SimpleRacingTask
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.toRacingWaypoints
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryCrossingMath
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryGeometry
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryPoint
import com.trust3.xcpro.tasks.racing.boundary.RacingBoundaryTransition
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStatus
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.trust3.xcpro.tasks.racing.navigation.buildFinishLineTask
import com.trust3.xcpro.tasks.racing.navigation.buildLineStartTask
import com.trust3.xcpro.tasks.racing.toCoreTask
import com.trust3.xcpro.tasks.racing.turnpoints.KeyholeGeometry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationRouteRepositoryTest {

    @Test
    fun route_flow_derives_remaining_route_for_started_racing_task() = runTest {
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot()),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 1
                )
            )
        )

        val snapshot = repository.route.first()

        assertTrue(snapshot.valid)
        assertEquals(NavigationRouteKind.TASK_FINISH, snapshot.kind)
        assertEquals("Finish", snapshot.label)
        assertEquals(2, snapshot.remainingWaypoints.size)
        assertEquals("Tp1", snapshot.remainingWaypoints.first().label)
        assertEquals("Finish", snapshot.remainingWaypoints.last().label)
    }

    @Test
    fun route_flow_returns_no_task_for_non_racing_runtime() = runTest {
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(
                TaskRuntimeSnapshot(
                    taskType = TaskType.AAT,
                    task = racingTaskSnapshot().task,
                    activeLeg = 0
                )
            ),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(status = RacingNavigationStatus.IN_PROGRESS)
            )
        )

        val snapshot = repository.route.first()

        assertFalse(snapshot.valid)
        assertEquals(NavigationRouteInvalidReason.NO_TASK, snapshot.invalidReason)
        assertTrue(snapshot.remainingWaypoints.isEmpty())
    }

    @Test
    fun project_navigation_route_is_deterministic_for_same_inputs() {
        val taskSnapshot = racingTaskSnapshot()
        val navigationState = RacingNavigationState(
            status = RacingNavigationStatus.PENDING_START,
            currentLegIndex = 0
        )

        val first = projectNavigationRoute(taskSnapshot, navigationState)
        val second = projectNavigationRoute(taskSnapshot, navigationState)

        assertEquals(first, second)
        assertFalse(first.valid)
        assertEquals(NavigationRouteInvalidReason.PRESTART, first.invalidReason)
    }

    @Test
    fun route_flow_projects_finish_cylinder_boundary_entry_for_active_finish_leg() = runTest {
        val task = buildLineStartTask().toCoreTask()
        val finish = task.waypoints.last()
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(task = task, activeLeg = 2)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 2,
                    lastFix = RacingNavigationFix(
                        lat = finish.lat,
                        lon = finish.lon + 0.05,
                        timestampMillis = 1_000L
                    )
                )
            )
        )

        val snapshot = repository.route.first()
        val point = snapshot.remainingWaypoints.single()
        val radiusMeters = RacingGeometryUtils.haversineDistanceMeters(
            finish.lat,
            finish.lon,
            point.lat,
            point.lon
        )

        assertTrue(snapshot.valid)
        assertEquals(1, snapshot.remainingWaypoints.size)
        assertEquals(3_000.0, radiusMeters, 20.0)
        assertTrue(point.lon > finish.lon)
    }

    @Test
    fun route_flow_projects_finish_line_touchpoint_on_boundary() = runTest {
        val task = buildFinishLineTask().toCoreTask()
        val finish = task.waypoints.last()
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(task = task, activeLeg = 2)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 2,
                    lastFix = RacingNavigationFix(
                        lat = 0.01,
                        lon = finish.lon + 0.05,
                        timestampMillis = 1_000L
                    )
                )
            )
        )

        val snapshot = repository.route.first()
        val point = snapshot.remainingWaypoints.single()

        assertTrue(snapshot.valid)
        assertEquals(finish.lon, point.lon, 0.0001)
        assertEquals(0.01, point.lat, 0.0002)
        assertTrue(abs(point.lat - finish.lat) > 0.001)
    }

    @Test
    fun route_flow_projects_fai_quadrant_boundary_touchpoint_for_active_turnpoint() = runTest {
        val task = buildQuadrantTask().toCoreTask()
        val racingTask = task.toRacingWaypoints()
        val turnpoint = racingTask[1]
        val finish = racingTask.last()
        val lastFixPoint = RacingBoundaryGeometry.pointOnBearing(
            RacingBoundaryPoint(turnpoint.lat, turnpoint.lon),
            90.0,
            11_000.0
        )
        val lastFix = RacingNavigationFix(
            lat = lastFixPoint.lat,
            lon = lastFixPoint.lon,
            timestampMillis = 1_000L
        )
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(task = task, activeLeg = 1)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.IN_PROGRESS,
                    currentLegIndex = 1,
                    lastFix = lastFix
                )
            )
        )

        val expected = expectedQuadrantIntersection(
            turnpoint = turnpoint,
            previousWaypoint = racingTask.first(),
            nextWaypoint = finish,
            lastFix = lastFix
        )
        assertNotNull(expected)

        val snapshot = repository.route.first()
        val point = snapshot.remainingWaypoints.first()

        assertTrue(snapshot.valid)
        assertEquals(expected!!.lat, point.lat, 0.00001)
        assertEquals(expected.lon, point.lon, 0.00001)
        assertTrue(abs(point.lon - turnpoint.lon) > 0.001)
    }

    @Test
    fun route_flow_returns_finished_state_with_empty_route() = runTest {
        val task = buildLineStartTask().toCoreTask()
        val repository = NavigationRouteRepository(
            taskSnapshotFlow = MutableStateFlow(racingTaskSnapshot(task = task, activeLeg = 2)),
            racingStateFlow = MutableStateFlow(
                RacingNavigationState(
                    status = RacingNavigationStatus.FINISHED,
                    currentLegIndex = 2
                )
            )
        )

        val snapshot = repository.route.first()

        assertFalse(snapshot.valid)
        assertEquals(NavigationRouteInvalidReason.FINISHED, snapshot.invalidReason)
        assertTrue(snapshot.remainingWaypoints.isEmpty())
    }

    private fun racingTaskSnapshot(
        task: Task = defaultRacingTask(),
        activeLeg: Int = 0
    ): TaskRuntimeSnapshot =
        TaskRuntimeSnapshot(
            task = task,
            taskType = TaskType.RACING,
            activeLeg = activeLeg
        )

    private fun defaultRacingTask(): Task = Task(
        id = "task-1",
        waypoints = listOf(
            waypoint("start", 0.0, 0.0, WaypointRole.START),
            waypoint("tp1", 0.0, 0.05, WaypointRole.TURNPOINT),
            waypoint("finish", 0.0, 0.1, WaypointRole.FINISH)
        )
    )

    private fun buildQuadrantTask(): SimpleRacingTask {
        val center = RacingBoundaryPoint(0.0, 0.0)
        val startPoint = RacingBoundaryGeometry.pointOnBearing(center, 180.0, 20_000.0)
        val finishPoint = RacingBoundaryGeometry.pointOnBearing(center, 0.0, 20_000.0)
        val start = RacingWaypoint.createWithStandardizedDefaults(
            id = "start-quadrant",
            title = "Start",
            subtitle = "",
            lat = startPoint.lat,
            lon = startPoint.lon,
            role = RacingWaypointRole.START,
            startPointType = RacingStartPointType.START_CYLINDER
        )
        val turnpoint = RacingWaypoint.createWithStandardizedDefaults(
            id = "tp-quadrant",
            title = "Quadrant",
            subtitle = "",
            lat = center.lat,
            lon = center.lon,
            role = RacingWaypointRole.TURNPOINT,
            turnPointType = RacingTurnPointType.FAI_QUADRANT,
            customGateWidthMeters = 10_000.0,
            faiQuadrantOuterRadiusMeters = 10_000.0
        )
        val finish = RacingWaypoint.createWithStandardizedDefaults(
            id = "finish-quadrant",
            title = "Finish",
            subtitle = "",
            lat = finishPoint.lat,
            lon = finishPoint.lon,
            role = RacingWaypointRole.FINISH,
            finishPointType = RacingFinishPointType.FINISH_CYLINDER
        )
        return SimpleRacingTask(
            id = "task-quadrant",
            waypoints = listOf(start, turnpoint, finish)
        )
    }

    private fun expectedQuadrantIntersection(
        turnpoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint,
        lastFix: RacingNavigationFix
    ): RacingBoundaryPoint? {
        val center = RacingBoundaryPoint(turnpoint.lat, turnpoint.lon)
        val sectorBearing = KeyholeGeometry.calculateFAISectorBisector(
            waypoint = turnpoint,
            previousWaypoint = previousWaypoint,
            nextWaypoint = nextWaypoint
        )
        val p0 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(lastFix.lat, lastFix.lon))
        val p1 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(nextWaypoint.lat, nextWaypoint.lon))
        val intersection = RacingBoundaryCrossingMath.sectorIntersectionParameter(
            center = center,
            radiusMeters = turnpoint.faiQuadrantOuterRadiusMeters,
            sectorBearingDegrees = sectorBearing,
            halfAngleDegrees = 45.0,
            p0 = p0,
            p1 = p1,
            transition = RacingBoundaryTransition.ENTER
        ) ?: return null
        return RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
    }

    private fun waypoint(
        id: String,
        lat: Double,
        lon: Double,
        role: WaypointRole
    ): TaskWaypoint = TaskWaypoint(
        id = id,
        title = id.replaceFirstChar(Char::titlecase),
        subtitle = "",
        lat = lat,
        lon = lon,
        role = role
    )
}
