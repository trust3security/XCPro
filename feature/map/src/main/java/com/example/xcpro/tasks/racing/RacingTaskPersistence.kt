package com.example.xcpro.tasks.racing

import android.content.Context

/**
 * Racing Task Persistence Manager - Handles all storage operations
 *
 * ZERO DEPENDENCIES on AAT or other task modules - maintains complete separation
 * Extracted from RacingTaskManager.kt to reduce file size and improve modularity
 */
class RacingTaskPersistence(private val context: Context) {

    // Racing task storage - handles all persistence operations
    private val racingTaskStorage = RacingTaskStorage(context)

    /**
     * Save Racing task to preferences
     */
    fun saveRacingTask(currentTask: SimpleRacingTask) {
        racingTaskStorage.saveRacingTask(currentTask) ?: run {
        }
    }

    /**
     * Load Racing task from preferences
     */
    fun loadRacingTask(): SimpleRacingTask? {
        return racingTaskStorage.loadRacingTask()?.also { task ->
        } ?: run {
            null
        }
    }

    /**
     * Get Racing task summary
     */
    fun getRacingTaskSummary(currentTask: SimpleRacingTask, distanceCalculator: () -> Double): String {
        val waypoints = currentTask.waypoints.size
        val distance = if (waypoints >= 2) {
            String.format("%.1f km", distanceCalculator())
        } else {
            "No distance"
        }

        return "Racing Task: $waypoints waypoints, $distance"
    }

    /**
     * Calculate racing task distance
     */
    fun calculateRacingTaskDistance(currentTask: SimpleRacingTask, distanceCalculator: () -> Double): Double {
        return if (currentTask.waypoints.size >= 2) {
            distanceCalculator()
        } else {
            0.0
        }
    }

    /**
     * Get list of saved Racing tasks
     */
    fun getSavedRacingTasks(): List<String> {
        return racingTaskStorage.getSavedRacingTasks()
    }

    /**
     * Save Racing task to file
     */
    fun saveRacingTask(currentTask: SimpleRacingTask, taskName: String): Boolean {
        return racingTaskStorage.saveRacingTask(currentTask, taskName)
    }

    /**
     * Load Racing task from file
     */
    fun loadRacingTaskFromFile(taskName: String): SimpleRacingTask? {
        return racingTaskStorage.loadRacingTaskFromFile(taskName)
    }

    /**
     * Delete Racing task file
     */
    fun deleteRacingTask(taskName: String): Boolean {
        return racingTaskStorage.deleteRacingTask(taskName)
    }

    /**
     * Get task parameters string for Racing task
     */
    fun getRacingTaskParameters(currentTask: SimpleRacingTask, distanceCalculator: () -> Double): String {
        return buildString {
            append("Racing Task Parameters:\\n")
            currentTask.waypoints.forEachIndexed { index, waypoint ->
                append("${index + 1}. ${waypoint.title} (${waypoint.currentPointType})\\n")
            }
            if (currentTask.waypoints.size >= 2) {
                append("Total Distance: ${String.format("%.2f", distanceCalculator())} km")
            }
        }
    }
}
