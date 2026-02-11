package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.racing.RacingTaskManager

/**
 * Racing counterpart to the AAT delegate so the coordinator can dispatch without
 * branching on task type for common operations.
 */
internal class RacingCoordinatorDelegate(
    private val taskManager: RacingTaskManager,
    private val log: (String) -> Unit
) : TaskTypeCoordinatorDelegate {

    override fun clearTask() {
        taskManager.clearRacingTask()
        log("Cleared Racing task state")
    }

    override fun calculateDistance(): Double = taskManager.calculateRacingTaskDistance()

    override fun calculateSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSegmentDistance(from.lat, from.lon, to.lat, to.lon)
}
