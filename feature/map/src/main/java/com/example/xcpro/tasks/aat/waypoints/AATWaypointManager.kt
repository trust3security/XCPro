package com.example.xcpro.tasks.aat.waypoints

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import java.time.Duration

/**
 * AAT waypoint operation facade.
 */
class AATWaypointManager {

    fun initializeTask(waypoints: List<SearchWaypoint>): SimpleAATTask {
        return AATWaypointInitializationSupport.initializeTask(waypoints)
    }

    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.core.TaskWaypoint>): SimpleAATTask {
        return AATWaypointInitializationSupport.initializeFromGenericWaypoints(genericWaypoints)
    }

    fun addWaypoint(currentTask: SimpleAATTask, searchWaypoint: SearchWaypoint): SimpleAATTask {
        return AATWaypointMutationSupport.addWaypoint(currentTask, searchWaypoint)
    }

    fun removeWaypoint(currentTask: SimpleAATTask, currentLeg: Int, index: Int): Pair<SimpleAATTask, Int> {
        return AATWaypointMutationSupport.removeWaypoint(currentTask, currentLeg, index)
    }

    fun updateArea(currentTask: SimpleAATTask, index: Int, newArea: AATAssignedArea): SimpleAATTask {
        return AATWaypointMutationSupport.updateArea(currentTask, index, newArea)
    }

    fun updateTimes(currentTask: SimpleAATTask, minTime: Duration, maxTime: Duration?): SimpleAATTask {
        return AATWaypointMutationSupport.updateTimes(currentTask, minTime, maxTime)
    }

    fun reorderWaypoints(currentTask: SimpleAATTask, fromIndex: Int, toIndex: Int): SimpleAATTask {
        return AATWaypointMutationSupport.reorderWaypoints(currentTask, fromIndex, toIndex)
    }

    fun replaceWaypoint(currentTask: SimpleAATTask, index: Int, newWaypoint: SearchWaypoint): SimpleAATTask {
        return AATWaypointMutationSupport.replaceWaypoint(currentTask, index, newWaypoint)
    }
}
