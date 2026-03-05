package com.example.xcpro.map

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
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
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
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
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
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
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 303))
        coordinator.onTaskMutation()

        assertEquals(2, syncCount)
    }

    @Test
    fun onTaskStateChanged_distinctSignatures_syncEachChange() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 1, taskType = TaskType.RACING, activeLeg = 0))
        coordinator.onTaskStateChanged(signature(taskHash = 2, taskType = TaskType.RACING, activeLeg = 0))
        coordinator.onTaskStateChanged(signature(taskHash = 2, taskType = TaskType.AAT, activeLeg = 1))

        assertEquals(3, syncCount)
    }

    @Test
    fun onMapStyleChanged_withoutMap_flushesWhenMapReady() {
        var map: MapLibreMap? = null
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
        )

        coordinator.onMapStyleChanged(null)
        assertEquals(0, syncCount)

        map = mock()
        coordinator.onMapReady(map)

        assertEquals(1, syncCount)
    }

    @Test
    fun onMapStyleChanged_withReadyMap_forcesResync() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 505))
        coordinator.onMapStyleChanged(map)

        assertEquals(2, syncCount)
    }

    @Test
    fun clearTaskVisuals_withoutMap_clearsBeforePendingSyncWhenMapBecomesReady() {
        var map: MapLibreMap? = null
        val events = mutableListOf<String>()
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> events += "sync" },
            renderClear = { _ -> events += "clear" }
        )

        coordinator.onTaskStateChanged(signature(taskHash = 404))
        coordinator.clearTaskVisuals()

        assertEquals(emptyList<String>(), events)

        map = mock()
        coordinator.onMapReady(map)

        assertEquals(listOf("clear", "sync"), events)
    }

    @Test
    fun clearThenMutation_withoutMap_keepsClearBeforeSyncWhenMapReady() {
        var map: MapLibreMap? = null
        val events = mutableListOf<String>()
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> events += "sync" },
            renderClear = { _ -> events += "clear" }
        )

        coordinator.clearTaskVisuals()
        coordinator.onTaskMutation()

        assertEquals(emptyList<String>(), events)

        map = mock()
        coordinator.onMapReady(map)

        assertEquals(listOf("clear", "sync"), events)
    }

    @Test
    fun previewAatTargetPoint_withReadyMap_usesPreviewRendererOnly() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        var previewCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> },
            renderAatPreview = { _, _, _, _, _ -> previewCount += 1 }
        )

        coordinator.previewAatTargetPoint(
            waypointIndex = 2,
            latitude = -34.5,
            longitude = 138.6
        )

        assertEquals(0, syncCount)
        assertEquals(1, previewCount)
    }

    @Test
    fun previewAatTargetPoint_withoutMap_skipsPreviewRenderer() {
        var previewCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { null },
            renderSync = { _, _ -> },
            renderClear = { _ -> },
            renderAatPreview = { _, _, _, _, _ -> previewCount += 1 }
        )

        coordinator.previewAatTargetPoint(
            waypointIndex = 0,
            latitude = 1.0,
            longitude = 2.0
        )

        assertEquals(0, previewCount)
    }

    @Test
    fun previewAatTargetPoint_repeatedMoves_neverTriggersFullSync() {
        val map: MapLibreMap = mock()
        var syncCount = 0
        var previewCount = 0
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> syncCount += 1 },
            renderClear = { _ -> },
            renderAatPreview = { _, _, _, _, _ -> previewCount += 1 }
        )

        repeat(5) { step ->
            coordinator.previewAatTargetPoint(
                waypointIndex = 1,
                latitude = -34.0 + step * 0.0001,
                longitude = 138.0 + step * 0.0001
            )
        }

        val counters = coordinator.runtimeCounters()
        assertEquals(0, syncCount)
        assertEquals(5, previewCount)
        assertEquals(5L, counters.previewInvocations)
        assertEquals(0L, counters.fullSyncInvocations)
    }

    @Test
    fun previewThenMutation_tracksSingleDragCommitSync() {
        val map: MapLibreMap = mock()
        val coordinator = TaskRenderSyncCoordinator(
            snapshotProvider = ::sampleSnapshot,
            mapProvider = { map },
            renderSync = { _, _ -> },
            renderClear = { _ -> },
            renderAatPreview = { _, _, _, _, _ -> }
        )

        coordinator.previewAatTargetPoint(waypointIndex = 1, latitude = -34.0, longitude = 138.0)
        coordinator.previewAatTargetPoint(waypointIndex = 1, latitude = -34.1, longitude = 138.1)
        coordinator.onTaskMutation()

        val counters = coordinator.runtimeCounters()
        assertEquals(2L, counters.previewInvocations)
        assertEquals(1L, counters.dragCommitSyncInvocations)
        assertEquals(1L, counters.fullSyncInvocations)
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

    private fun sampleSnapshot(): TaskRenderSnapshot {
        return TaskRenderSnapshot(
            task = Task(id = "test", waypoints = emptyList()),
            taskType = TaskType.RACING,
            aatEditWaypointIndex = null
        )
    }
}
