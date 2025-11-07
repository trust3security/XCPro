package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskWaypoint
import org.maplibre.android.maps.MapLibreMap

/**
 * Common contract that lets the coordinator dispatch task-type specific behaviour
 * without duplicating logic or leaking implementation details between Racing and AAT.
 */
internal interface TaskTypeCoordinatorDelegate {
    fun plotOnMap(map: MapLibreMap?)
    fun clearFromMap(map: MapLibreMap?)
    fun clearTask()
    fun calculateDistance(): Double
    fun calculateSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double
}
