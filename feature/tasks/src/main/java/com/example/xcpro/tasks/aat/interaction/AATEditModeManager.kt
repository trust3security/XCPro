package com.example.xcpro.tasks.aat.interaction

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.map.AATAreaTapDetector
import com.example.xcpro.tasks.aat.map.AATMovablePointManager

class AATEditModeManager {
    private val movablePointManager = AATMovablePointManager()
    private var activeWaypointIndex: Int? = null

    val isInEditMode: Boolean get() = activeWaypointIndex != null
    val editWaypointIndex: Int? get() = activeWaypointIndex

    fun checkAreaTap(task: SimpleAATTask, lat: Double, lon: Double): Pair<Int, AATWaypoint>? {
        if (task.waypoints.isEmpty()) {
            return null
        }
        return AATAreaTapDetector.findTappedArea(task.waypoints, lat, lon)
    }

    fun setEditMode(waypointIndex: Int, enabled: Boolean) {
        activeWaypointIndex = waypointIndex.takeIf { enabled }
    }

    fun exitEditMode() {
        activeWaypointIndex = null
    }

    fun updateTargetPoint(task: SimpleAATTask, index: Int, lat: Double, lon: Double): AATWaypoint? {
        val waypoint = task.waypoints.getOrNull(index) ?: return null
        return movablePointManager.moveTargetPoint(waypoint, lat, lon)
    }
}
