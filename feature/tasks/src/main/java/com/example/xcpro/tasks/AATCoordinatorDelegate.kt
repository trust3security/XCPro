package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.core.TaskWaypoint
import java.time.Duration

/**
 * Encapsulates all AAT-specific coordination logic so the [TaskManagerCoordinator]
 * can remain a simple router between task types.
 */
internal class AATCoordinatorDelegate(
    private val taskManager: AATTaskManager,
    private val log: (String) -> Unit
) : TaskTypeCoordinatorDelegate {

    private val editOperations = object : AATEditOperations {
        override fun updateTargetPoint(index: Int, lat: Double, lon: Double) =
            taskManager.updateTargetPoint(index, lat, lon)

        override fun checkAreaTap(lat: Double, lon: Double): Pair<Int, AATWaypoint>? =
            taskManager.checkAreaTap(lat, lon)

        override fun setEditMode(waypointIndex: Int, enabled: Boolean) =
            taskManager.setEditMode(waypointIndex, enabled)

        override fun isInEditMode(): Boolean = taskManager.isInEditMode()

        override fun getEditWaypointIndex(): Int? = taskManager.getEditWaypointIndex()
    }

    private val editController = AATEditController(editOperations, log)

    fun updateWaypointPointTypeMeters(update: AATWaypointTypeUpdate) {
        log("AAT waypoint point type update - Index: ${update.index}")
        listOf(
            "start" to update.startType,
            "finish" to update.finishType,
            "turn" to update.turnType,
            "gateWidthMeters" to update.gateWidthMeters,
            "keyholeInnerRadiusMeters" to update.keyholeInnerRadiusMeters,
            "keyholeAngle" to update.keyholeAngle,
            "sectorOuterRadiusMeters" to update.sectorOuterRadiusMeters
        ).forEach { (label, value) ->
            if (value != null) {
                log("AAT point type attr: $label=$value")
            }
        }

        taskManager.updateAATWaypointPointTypeMeters(update)
    }

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) =
        editController.updateTargetPoint(index, lat, lon)

    fun updateTargetParam(index: Int, targetParam: Double) {
        taskManager.updateTargetParam(index, targetParam)
        log("Updated AAT target param at index $index to $targetParam")
    }

    fun toggleTargetLock(index: Int) {
        taskManager.toggleTargetLock(index)
        log("Toggled AAT target lock at index $index")
    }

    fun setTargetLock(index: Int, locked: Boolean) {
        taskManager.setTargetLock(index, locked)
        log("Set AAT target lock at index $index to $locked")
    }

    fun applyTargetState(
        index: Int,
        targetParam: Double,
        targetLocked: Boolean,
        targetLat: Double?,
        targetLon: Double?
    ) {
        taskManager.applyTargetState(
            index = index,
            targetParam = targetParam,
            targetLocked = targetLocked,
            targetLat = targetLat,
            targetLon = targetLon
        )
        log("Applied AAT target state at index $index")
    }

    fun updateParameters(minimumTime: Duration, maximumTime: Duration) {
        taskManager.updateAATTimes(minimumTime, maximumTime)
        log("Updated AAT parameters - min: ${minimumTime.toHours()}h, max: ${maximumTime.toHours()}h")
    }

    fun updateArea(index: Int, radiusMeters: Double) {
        val waypoint = taskManager.currentAATTask.waypoints.getOrNull(index) ?: return
        val newArea = waypoint.assignedArea.copy(radiusMeters = radiusMeters)
        taskManager.updateAATArea(index, newArea)
        log("Updated AAT area radius at index $index to ${radiusMeters / 1000.0}km")
        // Map re-plotting intentionally left to caller to avoid duplicate renders.
    }

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, AATWaypoint>? =
        editController.checkAreaTap(lat, lon)

    fun enterEditMode(waypointIndex: Int) {
        editController.enterEditMode(waypointIndex)
    }

    fun exitEditMode() {
        editController.exitEditMode()
    }

    fun isInEditMode(): Boolean = editController.isInEditMode()

    fun editWaypointIndex(): Int? = editController.editWaypointIndex()

    override fun clearTask() {
        taskManager.clearAATTask()
        log("Cleared AAT task state")
    }

    override fun calculateDistanceMeters(): Double = taskManager.calculateAATTaskDistanceMeters()

    override fun calculateSegmentDistanceMeters(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSegmentDistanceMeters(from.lat, from.lon, to.lat, to.lon)
}
