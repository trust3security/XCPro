package com.example.xcpro.tasks.aat.interaction

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.map.AATAreaTapDetector
import com.example.xcpro.tasks.aat.map.AATMovablePointManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.maps.MapLibreMap

/**
 * AAT Edit Mode Manager
 *
 * Manages interactive editing features for AAT tasks:
 * - Area tap detection
 * - Edit mode state
 * - Edit overlay rendering (highlighted areas)
 * - Target point dragging and updates
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 4 - Edit Mode Extraction)
 * DEPENDENCIES: SimpleAATTask, AATWaypoint, MapLibre
 */
class AATEditModeManager {

    private val gestureController = AATEditGestureController()
    private val geometryValidator = AATEditGeometryValidator(AATMovablePointManager())
    private val overlayRenderer = AATEditOverlayRenderer()
    private val editStateFlow = MutableStateFlow(AATEditState())
    val state: StateFlow<AATEditState> = editStateFlow.asStateFlow()
    private var editState: AATEditState
        get() = editStateFlow.value
        set(value) {
            editStateFlow.value = value
        }

    val isInEditMode: Boolean get() = editState.activeWaypointIndex != null
    val editWaypointIndex: Int? get() = editState.activeWaypointIndex

    /**
     * Check if a map tap hit an AAT area
     *
     * Iterates through all task waypoints and checks if the tap location
     * falls within any assigned area radius. For keyhole areas, checks both
     * the inner cylinder and outer sector extension.
     *
     * @param task The current task
     * @param lat Tap latitude
     * @param lon Tap longitude
     * @return Pair of (waypointIndex, waypoint) if hit, null otherwise
     */
    fun checkAreaTap(task: SimpleAATTask, lat: Double, lon: Double): Pair<Int, AATWaypoint>? {
        if (task.waypoints.isEmpty()) {
            return null
        }
        return AATAreaTapDetector.findTappedArea(task.waypoints, lat, lon)
    }

    /**
     * Set edit mode for a specific waypoint
     *
     * Enables or disables edit mode for a waypoint, allowing the user
     * to interactively adjust the target point within the assigned area.
     *
     * @param waypointIndex The waypoint to edit (or -1 to disable)
     * @param enabled true to enable edit mode, false to disable
     */
    fun setEditMode(waypointIndex: Int, enabled: Boolean) {
        val index = if (enabled) waypointIndex else -1
        editState = editState.copy(
            activeWaypointIndex = if (enabled) index else null,
            isDragging = false
        )

    }

    /**
     * Exit edit mode
     *
     * Convenience method to disable edit mode.
     */
    fun exitEditMode() {
        setEditMode(-1, false)
    }

    /**
     * Update target point position for interactive editing
     *
     * Moves a waypoint's target point to a new location, with automatic
     * boundary validation to keep it within the assigned area.
     *
     * @param task The current task
     * @param index The waypoint index to update
     * @param lat New target latitude
     * @param lon New target longitude
     * @return Updated waypoint with new target point
     */
    fun updateTargetPoint(task: SimpleAATTask, index: Int, lat: Double, lon: Double): AATWaypoint? {
        if (index >= task.waypoints.size) {
            return null
        }

        val waypoint = task.waypoints[index]
        val movablePointManager = AATMovablePointManager()

        // Update target point with boundary validation
        val updatedWaypoint = movablePointManager.moveTargetPoint(waypoint, lat, lon)

        return updatedWaypoint
    }

    /**
     * Check if a map click hit a target point pin for drag handling
     *
     * Queries the map for features at the click location to determine
     * if a draggable target point pin was clicked.
     *
     * @param mapLibreMap The map instance
     * @param screenX Screen X coordinate of click
     * @param screenY Screen Y coordinate of click
     * @return Waypoint index if target point hit, null otherwise
     */
    fun checkTargetPointHit(mapLibreMap: MapLibreMap, screenX: Float, screenY: Float): Int? {
        try {
            val features = mapLibreMap.queryRenderedFeatures(
                android.graphics.PointF(screenX, screenY),
                "aat-target-points-layer"
            )

            if (features.isNotEmpty()) {
                val feature = features[0]
                val index = feature.getNumberProperty("index")?.toInt()
                return index
            }
        } catch (e: Exception) {
        }
        return null
    }

    /**
     * Plot AAT edit overlay on map.
     */
    fun plotEditOverlay(mapLibreMap: MapLibreMap, task: SimpleAATTask, waypointIndex: Int) {
        overlayRenderer.plotEditOverlay(mapLibreMap, task, waypointIndex)
    }

    /**
     * Clear AAT edit overlay from map.
     */
    fun clearEditOverlay(mapLibreMap: MapLibreMap) {
        overlayRenderer.clearEditOverlay(mapLibreMap)
    }
}
