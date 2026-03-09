package com.example.xcpro.tasks

import com.example.xcpro.map.TaskRenderSnapshot
import com.example.xcpro.tasks.aat.toSimpleAATTask
import com.example.xcpro.tasks.aat.rendering.AATTaskRenderer
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskCalculator
import com.example.xcpro.tasks.racing.RacingTaskCalculatorInterface
import com.example.xcpro.tasks.racing.RacingTaskDisplay
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.toRacingWaypoints
import org.maplibre.android.maps.MapLibreMap

/**
 * UI/runtime-only routing for task map rendering.
 * Keeps MapLibre-specific calls out of TaskManagerCoordinator routing logic.
 */
object TaskMapRenderRouter {

    private val racingTaskDisplay = RacingTaskDisplay()
    private val racingTaskCalculator = RacingTaskCalculator()
    private val racingCalculatorDelegate = object : RacingTaskCalculatorInterface {
        override fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>> {
            return racingTaskCalculator.findOptimalFAIPath(waypoints)
        }
    }
    private val aatTaskRenderer = AATTaskRenderer()

    private val allTaskLayers = listOf(
        // Racing layers.
        "racing-waypoints",
        "racing-turnpoint-areas-fill",
        "racing-turnpoint-areas-border",
        "racing-course-line",
        // AAT layers.
        "aat-waypoints",
        "aat-areas-layer",
        "aat-borders-layer",
        "aat-lines-layer",
        "aat-task-line",
        "aat-target-points-layer",
        // Generic legacy layers.
        "task-waypoints",
        "task-areas",
        "task-line"
    )

    private val allTaskSources = listOf(
        // Racing sources.
        "racing-waypoints",
        "racing-turnpoint-areas",
        "racing-course-line",
        // AAT sources.
        "aat-waypoints",
        "aat-areas",
        "aat-lines",
        "aat-task-line",
        "aat-target-points",
        // Generic legacy sources.
        "task-waypoints",
        "task-areas",
        "task-line"
    )

    fun plotCurrentTask(snapshot: TaskRenderSnapshot, map: MapLibreMap?) {
        val currentMap = map ?: return
        val coreTask = snapshot.task
        when (snapshot.taskType) {
            TaskType.RACING -> {
                racingTaskDisplay.plotRacingOnMap(
                    map = currentMap,
                    waypoints = coreTask.toRacingWaypoints(),
                    racingTaskCalculator = racingCalculatorDelegate
                )
            }
            TaskType.AAT -> {
                val aatTask = coreTask.toSimpleAATTask()
                aatTaskRenderer.plotTaskOnMap(
                    map = currentMap,
                    task = aatTask,
                    editModeWaypointIndex = snapshot.aatEditWaypointIndex
                )
            }
        }
    }

    fun clearAllTaskVisuals(map: MapLibreMap?) {
        val currentMap = map ?: return
        racingTaskDisplay.clearRacingFromMap(currentMap)
        aatTaskRenderer.clearTaskFromMap(currentMap)
    }

    fun syncTaskVisuals(snapshot: TaskRenderSnapshot, map: MapLibreMap?) {
        val currentMap = map ?: return
        clearAllTaskVisuals(currentMap)
        removeOrphanedTaskStyleArtifacts(currentMap)
        if (snapshot.task.waypoints.isNotEmpty()) {
            plotCurrentTask(snapshot, currentMap)
        }
    }

    fun previewAatTargetPoint(
        snapshot: TaskRenderSnapshot,
        waypointIndex: Int,
        latitude: Double,
        longitude: Double,
        map: MapLibreMap?
    ) {
        val currentMap = map ?: return
        if (snapshot.taskType != TaskType.AAT) return
        val currentTask = snapshot.task.toSimpleAATTask()
        aatTaskRenderer.previewTargetPointAndTaskLine(
            map = currentMap,
            task = currentTask,
            waypointIndex = waypointIndex,
            latitude = latitude,
            longitude = longitude,
            editModeWaypointIndex = snapshot.aatEditWaypointIndex
        )
    }

    private fun removeOrphanedTaskStyleArtifacts(map: MapLibreMap) {
        map.getStyle { style ->
            allTaskLayers.forEach { layerId ->
                try {
                    if (style.getLayer(layerId) != null) {
                        style.removeLayer(layerId)
                    }
                } catch (_: Exception) {
                    // Best-effort cleanup across style/version differences.
                }
            }
            allTaskSources.forEach { sourceId ->
                try {
                    if (style.getSource(sourceId) != null) {
                        style.removeSource(sourceId)
                    }
                } catch (_: Exception) {
                    // Best-effort cleanup across style/version differences.
                }
            }
        }
    }
}
