package com.example.xcpro.map

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.mockito.kotlin.mock

class TaskRenderSyncCoordinatorTest {

    @Test
    fun onTaskStateChanged_sameSignature_syncsOnce() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            taskManager = createTaskManager(),
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _, _ -> }
        )
        val signature = signature(taskHash = 101)

        coordinator.onTaskStateChanged(signature)
        coordinator.onTaskStateChanged(signature)

        assertEquals(1, syncCount)
    }

    @Test
    fun onTaskStateChanged_withoutMap_flushesWhenMapReady() {
        var map: MapLibreMap? = null
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            taskManager = createTaskManager(),
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _, _ -> }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 202))
        assertEquals(0, syncCount)

        map = mock()
        coordinator.onMapReady(map)

        assertEquals(1, syncCount)
    }

    @Test
    fun onTaskMutation_forcesSyncAfterTaskStateSync() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            taskManager = createTaskManager(),
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _, _ -> }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 303))
        coordinator.onTaskMutation()

        assertEquals(2, syncCount)
    }

    private fun signature(
        taskId: String = "task-1",
        taskHash: Int = 1,
        taskType: TaskType = TaskType.RACING,
        activeLeg: Int = 0
    ): TaskRenderSyncCoordinator.TaskStateSignature {
        return TaskRenderSyncCoordinator.TaskStateSignature(
            taskId = taskId,
            taskHash = taskHash,
            taskType = taskType,
            activeLeg = activeLeg
        )
    }

    private fun createTaskManager(): TaskManagerCoordinator {
        return TaskManagerCoordinator(
            taskEnginePersistenceService = null,
            racingTaskEngine = null,
            aatTaskEngine = null,
            racingTaskManager = RacingTaskManager(null),
            aatTaskManager = AATTaskManager(null)
        )
    }
}
