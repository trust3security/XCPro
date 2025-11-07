package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.racing.RacingTaskManager
import org.maplibre.android.maps.MapLibreMap

/**
 * Racing counterpart to the AAT delegate so the coordinator can dispatch without
 * branching on task type for common operations.
 */
internal class RacingCoordinatorDelegate(
    private val taskManager: RacingTaskManager,
    private val mapProvider: () -> MapLibreMap?,
    private val log: (String) -> Unit
) : TaskTypeCoordinatorDelegate {

    override fun plotOnMap(map: MapLibreMap?) {
        val resolvedMap = map ?: mapProvider()
        if (resolvedMap == null) {
            log("Cannot plot Racing task - map instance is null")
            return
        }
        taskManager.plotRacingOnMap(resolvedMap)
        log("Plotted Racing task on map")
    }

    override fun clearFromMap(map: MapLibreMap?) {
        val resolvedMap = map ?: mapProvider()
        if (resolvedMap == null) {
            log("Cannot clear Racing visuals - map instance is null")
            return
        }
        taskManager.clearRacingFromMap(resolvedMap)
        log("Cleared Racing visuals from map")
    }

    override fun clearTask() {
        taskManager.clearRacingTask()
        log("Cleared Racing task state")
    }

    override fun calculateDistance(): Double = taskManager.calculateRacingTaskDistance()

    override fun calculateSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSegmentDistance(from.lat, from.lon, to.lat, to.lon)
}
