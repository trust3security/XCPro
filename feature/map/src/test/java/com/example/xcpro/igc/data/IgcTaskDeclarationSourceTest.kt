package com.example.xcpro.igc.data

import com.example.xcpro.igc.domain.IgcTaskDeclarationStartSnapshot
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.flow.MutableStateFlow

class IgcTaskDeclarationSourceTest {

    @Test
    fun snapshotForStart_returnsDeclarationForRacingTaskFixture() {
        val source = TaskRepositoryIgcTaskDeclarationSource(
            taskCoordinator = coordinatorWith(task = racingTaskFixture())
        )

        val snapshot = source.snapshotForStart(
            sessionId = 1L,
            capturedAtUtcMs = 1_741_483_200_000L
        )

        assertTrue(snapshot is IgcTaskDeclarationStartSnapshot.Available)
        val available = snapshot as IgcTaskDeclarationStartSnapshot.Available
        assertNotNull(available)
        assertEquals("task-racing-001", available.snapshot.taskId)
        assertEquals(3, available.snapshot.waypoints.size)
        assertEquals("START", available.snapshot.waypoints.first().name)
    }

    @Test
    fun snapshotForStart_returnsInvalidWhenTaskHasTooFewWaypoints() {
        val source = TaskRepositoryIgcTaskDeclarationSource(
            taskCoordinator = coordinatorWith(
                task = Task(
                    id = "task-invalid",
                    waypoints = listOf(
                        TaskWaypoint(
                            id = "wp-start",
                            title = "START",
                            subtitle = "",
                            lat = -33.865,
                            lon = 151.209,
                            role = WaypointRole.START
                        )
                    )
                )
            )
        )

        val snapshot = source.snapshotForStart(
            sessionId = 2L,
            capturedAtUtcMs = 1_741_483_200_000L
        )

        assertTrue(snapshot is IgcTaskDeclarationStartSnapshot.Invalid)
        val invalid = snapshot as IgcTaskDeclarationStartSnapshot.Invalid
        assertEquals("WAYPOINT_COUNT_LT_2", invalid.reason)
    }

    @Test
    fun snapshotForStart_returnsInvalidWhenWaypointCoordinatesAreOutOfRange() {
        val source = TaskRepositoryIgcTaskDeclarationSource(
            taskCoordinator = coordinatorWith(
                task = Task(
                    id = "task-invalid-coords",
                    waypoints = listOf(
                        TaskWaypoint(
                            id = "wp-start",
                            title = "START",
                            subtitle = "",
                            lat = -33.865,
                            lon = 151.209,
                            role = WaypointRole.START
                        ),
                        TaskWaypoint(
                            id = "wp-bad",
                            title = "BAD",
                            subtitle = "",
                            lat = 96.0,
                            lon = 151.250,
                            role = WaypointRole.TURNPOINT
                        )
                    )
                )
            )
        )

        val snapshot = source.snapshotForStart(
            sessionId = 3L,
            capturedAtUtcMs = 1_741_483_200_000L
        )

        assertTrue(snapshot is IgcTaskDeclarationStartSnapshot.Invalid)
        val invalid = snapshot as IgcTaskDeclarationStartSnapshot.Invalid
        assertEquals("WAYPOINT_COORDINATE_INVALID", invalid.reason)
    }

    private fun racingTaskFixture(): Task {
        return Task(
            id = "task-racing-001",
            waypoints = listOf(
                TaskWaypoint(
                    id = "wp-start",
                    title = "START",
                    subtitle = "",
                    lat = -33.865,
                    lon = 151.209,
                    role = WaypointRole.START
                ),
                TaskWaypoint(
                    id = "wp-tp1",
                    title = "TP1",
                    subtitle = "",
                    lat = -33.900,
                    lon = 151.250,
                    role = WaypointRole.TURNPOINT
                ),
                TaskWaypoint(
                    id = "wp-finish",
                    title = "FINISH",
                    subtitle = "",
                    lat = -33.920,
                    lon = 151.280,
                    role = WaypointRole.FINISH
                )
            )
        )
    }

    private fun coordinatorWith(task: Task): TaskManagerCoordinator {
        val taskCoordinator: TaskManagerCoordinator = mock()
        whenever(taskCoordinator.taskSnapshotFlow).thenReturn(
            MutableStateFlow(
                TaskRuntimeSnapshot(
                    taskType = TaskType.RACING,
                    task = task,
                    activeLeg = 0
                )
            )
        )
        return taskCoordinator
    }
}
