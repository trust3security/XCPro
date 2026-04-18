package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.core.TaskWaypoint

/**
 * Common contract that lets the coordinator dispatch task-type specific behaviour
 * without duplicating logic or leaking implementation details between Racing and AAT.
 */
internal interface TaskTypeCoordinatorDelegate {
    fun clearTask()
    fun calculateDistanceMeters(): Double
    fun calculateSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double
}
