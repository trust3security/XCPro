package com.example.xcpro.livefollow.data.task

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TaskCoordinatorLiveFollowTaskSnapshotSourceTest {

    @Test
    fun taskExport_ignoresActiveLegOnlyChanges() = runTest {
        val snapshotFlow = MutableStateFlow(
            TaskRuntimeSnapshot(
                taskType = TaskType.RACING,
                task = sampleTask(),
                activeLeg = 0
            )
        )
        val coordinator: TaskManagerCoordinator = mock()
        whenever(coordinator.taskSnapshotFlow).thenReturn(snapshotFlow)
        val source = TaskCoordinatorLiveFollowTaskSnapshotSource(coordinator)
        val collected = mutableListOf<com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot?>()

        val job = launch {
            source.taskSnapshot.collect { collected += it }
        }
        advanceUntilIdle()

        snapshotFlow.value = snapshotFlow.value.copy(activeLeg = 1)
        advanceUntilIdle()

        assertEquals(1, collected.size)
        assertNotNull(collected.single())
        job.cancel()
    }

    private fun sampleTask(): Task {
        return Task(
            id = "task-alpha",
            waypoints = listOf(
                TaskWaypoint(
                    id = "start-1",
                    title = "Start",
                    subtitle = "",
                    lat = -33.9,
                    lon = 151.2,
                    role = WaypointRole.START,
                    customRadiusMeters = 10_000.0,
                    customPointType = "START_LINE"
                ),
                TaskWaypoint(
                    id = "tp-1",
                    title = "TP1",
                    subtitle = "",
                    lat = -33.8,
                    lon = 151.3,
                    role = WaypointRole.TURNPOINT,
                    customRadiusMeters = 500.0,
                    customPointType = "TURN_POINT_CYLINDER"
                )
            )
        )
    }
}
