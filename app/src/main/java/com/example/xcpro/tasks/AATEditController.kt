package com.example.xcpro.tasks

import org.maplibre.android.maps.MapLibreMap

internal interface AATEditOperations {
    fun updateTargetPoint(index: Int, lat: Double, lon: Double)
    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>?
    fun setEditMode(waypointIndex: Int, enabled: Boolean)
    fun plotAATEditOverlay(map: MapLibreMap, waypointIndex: Int)
    fun plotAATOnMap(map: MapLibreMap)
    fun clearAATEditOverlay(map: MapLibreMap)
    fun isInEditMode(): Boolean
    fun getEditWaypointIndex(): Int?
    fun checkTargetPointHit(map: MapLibreMap, screenX: Float, screenY: Float): Int?
}

internal class AATEditController(
    private val operations: AATEditOperations,
    private val mapProvider: () -> MapLibreMap?,
    private val log: (String) -> Unit
) {

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) {
        log("Updating AAT target point index=$index lat=$lat lon=$lon")
        operations.updateTargetPoint(index, lat, lon)
        mapProvider()?.let { map ->
            log("Re-plotting AAT task after target point update")
            operations.plotAATOnMap(map)
        } ?: log("Cannot re-plot AAT task - map instance is null")
    }

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
        operations.checkAreaTap(lat, lon)

    fun enterEditMode(waypointIndex: Int) {
        log("Entering AAT edit mode for waypoint $waypointIndex")
        operations.setEditMode(waypointIndex, true)
        mapProvider()?.let { map ->
            operations.plotAATEditOverlay(map, waypointIndex)
            operations.plotAATOnMap(map)
        } ?: log("Cannot plot AAT edit overlay - map instance is null")
    }

    fun exitEditMode() {
        log("Exiting AAT edit mode")
        operations.setEditMode(-1, false)
        mapProvider()?.let { map ->
            operations.clearAATEditOverlay(map)
            operations.plotAATOnMap(map)
        } ?: log("Cannot clear AAT edit overlay - map instance is null")
    }

    fun isInEditMode(): Boolean = operations.isInEditMode()

    fun editWaypointIndex(): Int? = operations.getEditWaypointIndex()

    fun checkTargetPointHit(screenX: Float, screenY: Float): Int? {
        val map = mapProvider()
        if (map == null) {
            log("Cannot check target point hit - map instance is null")
            return null
        }
        return operations.checkTargetPointHit(map, screenX, screenY)
    }
}
