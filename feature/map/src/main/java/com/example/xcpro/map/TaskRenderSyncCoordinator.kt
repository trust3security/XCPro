package com.example.xcpro.map

import com.example.xcpro.tasks.TaskMapRenderRouter
import com.example.xcpro.tasks.core.TaskType
import org.maplibre.android.maps.MapLibreMap

/**
 * Single runtime owner for task overlay render synchronization.
 * All map/task trigger paths should call this coordinator instead of the router directly.
 */
class TaskRenderSyncCoordinator(
    private val snapshotProvider: () -> TaskRenderSnapshot,
    private val mapProvider: () -> MapLibreMap?,
    private val renderSync: (TaskRenderSnapshot, MapLibreMap?) -> Unit = TaskMapRenderRouter::syncTaskVisuals,
    private val renderClear: (MapLibreMap?) -> Unit = TaskMapRenderRouter::clearAllTaskVisuals,
    private val renderAatPreview: (TaskRenderSnapshot, Int, Double, Double, MapLibreMap?) -> Unit =
        TaskMapRenderRouter::previewAatTargetPoint
) {

    data class TaskStateSignature(
        val taskId: String,
        val taskHash: Int,
        val taskType: TaskType,
        val activeLeg: Int
    )

    data class RuntimeCounters(
        val fullSyncInvocations: Long,
        val previewInvocations: Long,
        val previewSkippedNoMap: Long,
        val dragCommitSyncInvocations: Long
    )

    private var lastTaskStateSignature: TaskStateSignature? = null
    private var pendingSync = false
    private var pendingClear = false
    private var fullSyncInvocations = 0L
    private var previewInvocations = 0L
    private var previewSkippedNoMap = 0L
    private var dragCommitSyncInvocations = 0L
    private var previewActive = false

    fun onMapReady(map: MapLibreMap?) {
        syncNow(map)
    }

    fun onMapStyleChanged(map: MapLibreMap?) {
        syncNow(map)
    }

    fun onOverlayRefresh(map: MapLibreMap?) {
        syncNow(map)
    }

    fun onTaskMutation() {
        if (previewActive) {
            dragCommitSyncInvocations += 1
            previewActive = false
        }
        syncNow(mapProvider())
    }

    fun onTaskStateChanged(signature: TaskStateSignature) {
        if (!pendingSync && !pendingClear && signature == lastTaskStateSignature) {
            return
        }
        lastTaskStateSignature = signature
        syncNow(mapProvider())
    }

    fun previewAatTargetPoint(
        waypointIndex: Int,
        latitude: Double,
        longitude: Double
    ) {
        val currentMap = mapProvider()
        if (currentMap == null) {
            previewSkippedNoMap += 1
            return
        }
        previewInvocations += 1
        previewActive = true
        renderAatPreview(
            snapshotProvider(),
            waypointIndex,
            latitude,
            longitude,
            currentMap
        )
    }

    fun clearTaskVisuals(map: MapLibreMap? = null) {
        val currentMap = map ?: mapProvider()
        if (currentMap == null) {
            pendingClear = true
            pendingSync = false
            return
        }
        pendingClear = false
        pendingSync = false
        previewActive = false
        renderClear(currentMap)
    }

    fun runtimeCounters(): RuntimeCounters = RuntimeCounters(
        fullSyncInvocations = fullSyncInvocations,
        previewInvocations = previewInvocations,
        previewSkippedNoMap = previewSkippedNoMap,
        dragCommitSyncInvocations = dragCommitSyncInvocations
    )

    private fun syncNow(map: MapLibreMap?) {
        val currentMap = map ?: mapProvider()
        if (currentMap == null) {
            if (!pendingClear) {
                pendingSync = true
            }
            return
        }
        if (pendingClear) {
            renderClear(currentMap)
            pendingClear = false
        }
        pendingSync = false
        fullSyncInvocations += 1
        renderSync(snapshotProvider(), currentMap)
    }
}
