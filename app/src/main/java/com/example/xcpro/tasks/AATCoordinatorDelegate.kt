package com.example.xcpro.tasks

import com.example.xcpro.tasks.aat.AATTaskManager
import com.example.xcpro.tasks.core.TaskWaypoint
import java.time.Duration
import org.maplibre.android.maps.MapLibreMap

/**
 * Encapsulates all AAT-specific coordination logic so the [TaskManagerCoordinator]
 * can remain a simple router between task types.
 */
internal class AATCoordinatorDelegate(
    private val taskManager: AATTaskManager,
    private val mapProvider: () -> MapLibreMap?,
    private val log: (String) -> Unit
) : TaskTypeCoordinatorDelegate {

    private val editOperations = object : AATEditOperations {
        override fun updateTargetPoint(index: Int, lat: Double, lon: Double) =
            taskManager.updateTargetPoint(index, lat, lon)

        override fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
            taskManager.checkAreaTap(lat, lon)

        override fun setEditMode(waypointIndex: Int, enabled: Boolean) =
            taskManager.setEditMode(waypointIndex, enabled)

        override fun plotAATEditOverlay(map: MapLibreMap, waypointIndex: Int) =
            taskManager.plotAATEditOverlay(map, waypointIndex)

        override fun plotAATOnMap(map: MapLibreMap) =
            taskManager.plotAATOnMap(map)

        override fun clearAATEditOverlay(map: MapLibreMap) =
            taskManager.clearAATEditOverlay(map)

        override fun isInEditMode(): Boolean = taskManager.isInEditMode()

        override fun getEditWaypointIndex(): Int? = taskManager.getEditWaypointIndex()

        override fun checkTargetPointHit(
            map: MapLibreMap,
            screenX: Float,
            screenY: Float
        ): Int? = taskManager.checkTargetPointHit(map, screenX, screenY)
    }

    private val editController = AATEditController(editOperations, mapProvider, log)

    fun updateWaypointPointType(
        index: Int,
        startType: Any?,
        finishType: Any?,
        turnType: Any?,
        gateWidth: Double?,
        keyholeInnerRadius: Double?,
        keyholeAngle: Double?,
        sectorOuterRadius: Double?
    ) {
        log("AAT waypoint point type update - Index: $index")
        listOf(
            "start" to startType,
            "finish" to finishType,
            "turn" to turnType,
            "gateWidthKm" to gateWidth,
            "keyholeInnerRadiusKm" to keyholeInnerRadius,
            "keyholeAngle" to keyholeAngle,
            "sectorOuterRadiusKm" to sectorOuterRadius
        ).forEach { (label, value) ->
            if (value != null) {
                log("AAT point type attr: $label=$value")
            }
        }

        taskManager.updateWaypointPointTypeBridge(
            index = index,
            startType = startType,
            finishType = finishType,
            turnType = turnType,
            gateWidth = gateWidth,
            keyholeInnerRadius = keyholeInnerRadius,
            keyholeAngle = keyholeAngle,
            sectorOuterRadius = sectorOuterRadius
        )

        val map = mapProvider()
        if (map != null) {
            log("Re-plotting AAT task after point type update")
            taskManager.plotAATOnMap(map)
        } else {
            log("Cannot re-plot AAT task - map instance is null")
        }
    }

    fun updateTargetPoint(index: Int, lat: Double, lon: Double) =
        editController.updateTargetPoint(index, lat, lon)

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

    fun checkAreaTap(lat: Double, lon: Double): Pair<Int, Any>? =
        editController.checkAreaTap(lat, lon)

    fun enterEditMode(waypointIndex: Int) {
        editController.enterEditMode(waypointIndex)
    }

    fun exitEditMode() {
        editController.exitEditMode()
    }

    fun isInEditMode(): Boolean = editController.isInEditMode()

    fun editWaypointIndex(): Int? = editController.editWaypointIndex()

    fun checkTargetPointHit(screenX: Float, screenY: Float): Int? =
        editController.checkTargetPointHit(screenX, screenY)

    override fun plotOnMap(map: MapLibreMap?) {
        val resolvedMap = map ?: mapProvider()
        if (resolvedMap == null) {
            log("Cannot plot AAT task - map instance is null")
            return
        }
        taskManager.plotAATOnMap(resolvedMap)
        log("Plotted AAT task on map")
    }

    override fun clearFromMap(map: MapLibreMap?) {
        val resolvedMap = map ?: mapProvider()
        if (resolvedMap == null) {
            log("Cannot clear AAT visuals - map instance is null")
            return
        }
        taskManager.clearAATFromMap(resolvedMap)
        log("Cleared AAT visuals from map")
    }

    override fun clearTask() {
        taskManager.clearAATTask()
        log("Cleared AAT task state")
    }

    override fun calculateDistance(): Double = taskManager.calculateAATTaskDistance()

    override fun calculateSegmentDistance(from: TaskWaypoint, to: TaskWaypoint): Double =
        taskManager.calculateSegmentDistance(from.lat, from.lon, to.lat, to.lon)
}
