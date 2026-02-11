package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.toSimpleAATTask
import com.example.xcpro.tasks.aat.rendering.AATTaskRenderer
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingTaskCalculator
import com.example.xcpro.tasks.racing.RacingTaskCalculatorInterface
import com.example.xcpro.tasks.racing.RacingTaskDisplay
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.toSimpleRacingTask
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

    fun plotCurrentTask(taskManager: TaskManagerCoordinator, map: MapLibreMap?) {
        val currentMap = map ?: return
        val coreTask = taskManager.currentTask
        when (taskManager.taskType) {
            TaskType.RACING -> {
                val racingTask = coreTask.toSimpleRacingTask()
                racingTaskDisplay.plotRacingOnMap(
                    map = currentMap,
                    waypoints = racingTask.waypoints,
                    racingTaskCalculator = racingCalculatorDelegate
                )
            }
            TaskType.AAT -> {
                val aatTask = coreTask.toSimpleAATTask()
                aatTaskRenderer.plotTaskOnMap(
                    map = currentMap,
                    task = aatTask,
                    editModeWaypointIndex = taskManager.getAATEditWaypointIndex()
                )
            }
        }
    }

    fun clearAllTaskVisuals(@Suppress("UNUSED_PARAMETER") taskManager: TaskManagerCoordinator, map: MapLibreMap?) {
        val currentMap = map ?: return
        racingTaskDisplay.clearRacingFromMap(currentMap)
        aatTaskRenderer.clearTaskFromMap(currentMap)
    }
}
