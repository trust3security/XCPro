package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import javax.inject.Inject

class TaskSheetCoordinatorUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
) {
    val currentTask: Task
        get() = taskManager.currentTask

    val taskType: TaskType
        get() = taskManager.taskType

    val currentLeg: Int
        get() = taskManager.currentLeg

    fun setProximityHandler(handler: (Boolean, Boolean) -> Unit) {
        taskManager.setProximityHandler(handler)
    }

    fun addLegChangeListener(handler: (Int) -> Unit) {
        taskManager.addLegChangeListener(handler)
    }

    fun removeLegChangeListener(handler: (Int) -> Unit) {
        taskManager.removeLegChangeListener(handler)
    }

    fun addWaypoint(waypoint: SearchWaypoint) {
        taskManager.addWaypoint(waypoint)
    }

    fun removeWaypoint(index: Int) {
        taskManager.removeWaypoint(index)
    }

    fun reorderWaypoints(from: Int, to: Int) {
        taskManager.reorderWaypoints(from, to)
    }

    fun replaceWaypoint(index: Int, waypoint: SearchWaypoint) {
        taskManager.replaceWaypoint(index, waypoint)
    }

    fun setTaskType(taskType: TaskType) {
        taskManager.setTaskType(taskType)
    }

    fun clearTask() {
        taskManager.clearTask()
    }

    fun advanceToNextLeg() {
        taskManager.advanceToNextLeg()
    }

    fun setActiveLeg(index: Int) {
        taskManager.setActiveLeg(index)
    }

    fun updateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadius: Double?
    ) {
        taskManager.updateWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadius = faiQuadrantOuterRadius
        )
    }

    fun updateAATWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        sectorOuterRadius: Double?
    ) {
        taskManager.updateAATWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            sectorOuterRadius = sectorOuterRadius
        )
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        taskManager.updateAATTargetPoint(index, lat, lon)
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        taskManager.updateAATArea(index, radiusMeters)
    }
}
