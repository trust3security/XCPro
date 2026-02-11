package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskWaypoint

/**
 * Common contract that lets the coordinator dispatch task-type specific behaviour
 * without duplicating logic or leaking implementation details between Racing and AAT.
 */
internal interface TaskTypeCoordinatorDelegate {
    fun clearTask()
    fun calculateDistance(): Double
    fun calculateSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double
}
