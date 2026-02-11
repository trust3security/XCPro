package com.example.xcpro.tasks

internal interface AATEditOperations {
    fun updateTargetPoint(index: Int, lat: Double, lon: Double)
    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>?
    fun setEditMode(waypointIndex: Int, enabled: Boolean)
    fun isInEditMode(): Boolean
    fun getEditWaypointIndex(): Int?
}

internal class AATEditController(
    private val operations: AATEditOperations,
    private val log: (String) -> Unit
) {

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        log("Updating AAT target point index=$index lat=$lat lon=$lon")
        operations.updateTargetPoint(index, lat, lon)
    }

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
        operations.checkAreaTap(lat, lon)

    fun enterEditMode(waypointIndex: Int) {
        log("Entering AAT edit mode for waypoint $waypointIndex")
        operations.setEditMode(waypointIndex, true)
    }

    fun exitEditMode() {
        log("Exiting AAT edit mode")
        operations.setEditMode(-1, false)
    }

    fun isInEditMode(): Boolean = operations.isInEditMode()

    fun editWaypointIndex(): Int? = operations.getEditWaypointIndex()
}
