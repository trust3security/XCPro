package com.example.xcpro.map

import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskMapRenderRouter
import com.example.xcpro.tasks.core.TaskType
import org.maplibre.android.maps.MapLibreMap

/**
 * Single runtime owner for task overlay render synchronization.
 * All map/task trigger paths should call this coordinator instead of the router directly.
 */
class TaskRenderSyncCoordinator(
    private val taskManager: TaskManagerCoordinator,
    private val mapProvider: () -> MapLibreMap?,
    private val renderSync: (TaskManagerCoordinator, MapLibreMap?) -> Unit = TaskMapRenderRouter::syncTaskVisuals,
    private val renderClear: (TaskManagerCoordinator, MapLibreMap?) -> Unit = TaskMapRenderRouter::clearAllTaskVisuals
) {

    data class TaskStateSignature(
        val taskId: String,
        val taskHash: Int,
        val taskType: TaskType,
        val activeLeg: Int
    )

    private var lastTaskStateSignature: TaskStateSignature? = null
    private var pendingSync = false

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
        if (!pendingSync && signature == lastTaskStateSignature) {
            return
        }
        lastTaskStateSignature = signature
        syncNow(mapProvider())
    }

    fun clearTaskVisuals(map: MapLibreMap? = null) {
        val currentMap = map ?: mapProvider()
        if (currentMap == null) {
            pendingSync = true
            return
        }
        pendingSync = false
        renderClear(taskManager, currentMap)
    }

    private fun syncNow(map: MapLibreMap?) {
        val currentMap = map ?: mapProvider()
        if (currentMap == null) {
            pendingSync = true
            return
        }
        pendingSync = false
        renderSync(taskManager, currentMap)
    }
}
