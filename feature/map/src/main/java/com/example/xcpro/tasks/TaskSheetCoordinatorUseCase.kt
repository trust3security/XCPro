package com.example.xcpro.tasks

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import java.time.Duration
import javax.inject.Inject

data class TaskCoordinatorSnapshot(
    val task: Task,
    val taskType: TaskType,
    val activeLeg: Int
)

class TaskSheetCoordinatorUseCase @Inject constructor(
    private val taskManager: TaskManagerCoordinator
) {
    fun snapshot(): TaskCoordinatorSnapshot = TaskCoordinatorSnapshot(
        task = taskManager.currentTask,
        taskType = taskManager.taskType,
        activeLeg = taskManager.currentLeg
    )

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

    fun calculateSimpleSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSimpleSegmentDistanceMeters(from, to)

    fun calculateOptimalStartLineDistanceMeters(startWaypoint: TaskWaypoint, nextWaypoint: TaskWaypoint): Double {
        val optimal = taskManager.calculateOptimalStartLineCrossingPoint(startWaypoint, nextWaypoint)
        val projectedStart = TaskWaypoint(
            id = "optimal-start",
            title = "Optimal Start Crossing",
            subtitle = "",
            lat = optimal.first,
            lon = optimal.second,
            role = WaypointRole.START
        )
        return taskManager.calculateSimpleSegmentDistanceMeters(projectedStart, nextWaypoint)
    }

    fun calculateDistanceToNextWaypointMeters(
        fromWaypoint: TaskWaypoint,
        nextWaypoint: TaskWaypoint,
        useOptimalStartLine: Boolean
    ): Double {
        return if (useOptimalStartLine) {
            calculateOptimalStartLineDistanceMeters(
                startWaypoint = fromWaypoint,
                nextWaypoint = nextWaypoint
            )
        } else {
            calculateSimpleSegmentDistanceMeters(
                from = fromWaypoint,
                to = nextWaypoint
            )
        }
    }

    fun updateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        faiQuadrantOuterRadiusMeters: Double?
    ) {
        taskManager.updateWaypointPointType(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            faiQuadrantOuterRadiusMeters = faiQuadrantOuterRadiusMeters
        )
    }

    fun updateAATWaypointPointTypeMeters(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidthMeters: Double?,
        keyholeInnerRadiusMeters: Double?,
        keyholeAngle: Double?,
        sectorOuterRadiusMeters: Double?
    ) {
        taskManager.updateAATWaypointPointTypeMeters(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidthMeters = gateWidthMeters,
            keyholeInnerRadiusMeters = keyholeInnerRadiusMeters,
            keyholeAngle = keyholeAngle,
            sectorOuterRadiusMeters = sectorOuterRadiusMeters
        )
    }

    fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
        taskManager.updateAATTargetPoint(index, lat, lon)
    }

    fun updateAATArea(index: Int, radiusMeters: Double) {
        taskManager.updateAATArea(index, radiusMeters)
    }

    fun updateAATParameters(minimumTime: Duration, maximumTime: Duration) {
        taskManager.updateAATParameters(minimumTime, maximumTime)
    }
}
