package com.example.xcpro.map

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.gestures.TaskGestureCallbacks
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenTaskShellCoordinatorTest {

    @Test
    fun `isAATEditMode follows task-owned edit-mode flow`() = runTest {
        val taskManager = TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager(),
            coordinatorScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        )
        val mapTasksUseCase = MapTasksUseCase(taskManager)
        val shellScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shellCoordinator = MapScreenTaskShellCoordinator(
            scope = shellScope,
            mapTasksUseCase = mapTasksUseCase
        )
        try {
            taskManager.setTaskTypeForTesting(TaskType.AAT)
            taskManager.addWaypoint(searchWaypoint("start", 45.0, 7.0))
            taskManager.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
            taskManager.addWaypoint(searchWaypoint("finish", 45.1, 7.1))
            advanceUntilIdle()

            assertFalse(shellCoordinator.isAATEditMode.value)

            shellCoordinator.enterAATEditMode(1)
            advanceUntilIdle()
            assertTrue(shellCoordinator.isAATEditMode.value)
            assertTrue(mapTasksUseCase.aatEditWaypointIndexFlow.value == 1)

            shellCoordinator.exitAATEditMode()
            advanceUntilIdle()
            assertFalse(shellCoordinator.isAATEditMode.value)
            assertTrue(mapTasksUseCase.aatEditWaypointIndexFlow.value == null)
        } finally {
            shellScope.cancel()
        }
    }

    @Test
    fun `createTaskGestureHandler follows current task type`() = runTest {
        val taskManager = TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(),
            aatTaskManager = AATTaskManager(),
            coordinatorScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        )
        val mapTasksUseCase = MapTasksUseCase(taskManager)
        val shellScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val shellCoordinator = MapScreenTaskShellCoordinator(
            scope = shellScope,
            mapTasksUseCase = mapTasksUseCase
        )
        val callbacks = TaskGestureCallbacks(
            onEnterEditMode = { _, _, _, _ -> },
            onExitEditMode = {},
            onDragTargetPreview = { _, _, _ -> },
            onDragTargetCommit = { _, _, _ -> }
        )
        try {
            taskManager.setTaskTypeForTesting(TaskType.RACING)
            assertEquals(
                "NoOpTaskGestureHandler",
                shellCoordinator.createTaskGestureHandler(callbacks).javaClass.simpleName
            )

            taskManager.setTaskTypeForTesting(TaskType.AAT)
            taskManager.addWaypoint(searchWaypoint("start", 45.0, 7.0))
            taskManager.addWaypoint(searchWaypoint("tp1", 45.05, 7.05))
            taskManager.addWaypoint(searchWaypoint("finish", 45.1, 7.1))
            advanceUntilIdle()

            assertNotEquals(
                "NoOpTaskGestureHandler",
                shellCoordinator.createTaskGestureHandler(callbacks).javaClass.simpleName
            )
        } finally {
            shellScope.cancel()
        }
    }

    private fun searchWaypoint(id: String, lat: Double, lon: Double): SearchWaypoint =
        SearchWaypoint(
            id = id,
            title = id,
            subtitle = "",
            lat = lat,
            lon = lon
        )
}
