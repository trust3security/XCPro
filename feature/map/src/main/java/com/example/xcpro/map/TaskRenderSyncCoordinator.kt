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
    private val renderClear: (MapLibreMap?) -> Unit = TaskMapRenderRouter::clearAllTaskVisuals
) {

    data class TaskStateSignature(
        val taskId: String,
        val taskHash: Int,
        val taskType: TaskType,
        val activeLeg: Int
    )

    private var lastTaskStateSignature: TaskStateSignature? = null
    private var pendingSync = false
    private var pendingClear = false

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
        syncNow(mapProvider())
    }

    fun onTaskStateChanged(signature: TaskStateSignature) {
        if (!pendingSync && !pendingClear && signature == lastTaskStateSignature) {
            return
        }
        lastTaskStateSignature = signature
        syncNow(mapProvider())
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
        renderClear(currentMap)
    }

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
        renderSync(snapshotProvider(), currentMap)
    }
}
