package com.example.xcpro.tasks.aat.map

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.xcpro.core.time.Clock
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * AAT Map Interaction Handler - Phase 1 Integration
 *
 * Main component that integrates single-tap detection, coordinate conversion,
 * and edit mode state management for AAT interactive turnpoints.
 *
 * This is the primary interface for AAT map interactions in the MapScreen.
 *
 * Features:
 * - Single-tap area detection with accurate coordinates and automatic zoom
 * - Automatic zoom and edit mode transitions
 * - State management integration
 * - Callback system for UI updates
 *
 * Usage in MapScreen:
 * ```kotlin
 * val aatHandler = rememberAATMapInteractionHandler(...)
 *
 * AndroidView(...) { mapView ->
 *     aatHandler.attachToMap(mapView.mapLibreMap)
 * }
 * .modifier(aatHandler.pointerInputModifier)
 * ```
 */

data class AATInteractionCallbacks(
    val onAreaTapped: (Int, AATWaypoint) -> Unit = { _, _ -> },
    val onEditModeEntered: (Int, AATWaypoint) -> Unit = { _, _ -> },
    val onEditModeExited: () -> Unit = {},
    val onTargetPointMoved: (Int, AATLatLng) -> Unit = { _, _ -> },
    val onZoomToArea: (AATWaypoint, Float) -> Unit = { _, _ -> },
    val onZoomToOverview: (Float) -> Unit = {},
    val onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null } // Returns waypoint index if hit
)

@Composable
fun rememberAATMapInteractionHandler(
    aatWaypoints: List<AATWaypoint>,
    callbacks: AATInteractionCallbacks = AATInteractionCallbacks(),
    clock: Clock
): AATMapInteractionHandler {
    return remember(aatWaypoints, clock) {
        AATMapInteractionHandler(
            aatWaypoints = aatWaypoints,
            callbacks = callbacks,
            clock = clock
        )
    }
}

class AATMapInteractionHandler(
    private var aatWaypoints: List<AATWaypoint>,
    private val callbacks: AATInteractionCallbacks,
    private val clock: Clock
) {
    private var mapLibreMap: MapLibreMap? = null
    private var coordinateConverter: AATMapCoordinateConverter? = null

    // Edit mode state
    private val editModeManager = AATEditModeStateManager(clock)

    // Drag state tracking
    private var isDragging = false
    private var draggedPointIndex: Int? = null
    private val movablePointManager = AATMovablePointManager()

    /**
     * Attach to MapLibre map instance
     */
    fun attachToMap(map: MapLibreMap?) {
        mapLibreMap = map
        coordinateConverter = map?.let { AATMapCoordinateConverterFactory.create(it) }
    }

    /**
     * Update AAT waypoints list
     */
    fun updateWaypoints(newWaypoints: List<AATWaypoint>) {
        aatWaypoints = newWaypoints
    }

    /**
     * Pointer input modifier for map gestures
     * Handles both tap gestures (for area interaction) and drag gestures (for pin movement)
     */
    val pointerInputModifier = Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { offset ->
                    handleTapGesture(offset.x, offset.y)
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    handleDragStart(offset.x, offset.y)
                },
                onDrag = { change, _ ->
                    handleDragMove(change.position.x, change.position.y)
                },
                onDragEnd = {
                    handleDragEnd()
                }
            )
        }

    /**
     * Handle tap gesture - single tap now triggers zoom and edit mode
     */
    private fun handleTapGesture(screenX: Float, screenY: Float) {
        val converter = coordinateConverter ?: return

        // Create tap details
        val tapDetails = AATMapCoordinateConverterFactory.createTapDetails(
            screenX = screenX,
            screenY = screenY,
            converter = converter,
            timestampMs = clock.nowMonoMs()
        ) ?: return


        // Find which AAT area was tapped
        val tappedArea = AATAreaTapDetector.findTappedArea(
            waypoints = aatWaypoints,
            lat = tapDetails.mapCoordinates.latitude,
            lon = tapDetails.mapCoordinates.longitude
        )

        if (tappedArea != null) {
            handleAreaTap(tappedArea.first, tappedArea.second)
        } else {
            handleOutsideTap()
        }
    }

    /**
     * Handle single tap on AAT area - zoom in and enter edit mode
     */
    private fun handleAreaTap(areaIndex: Int, waypoint: AATWaypoint) {

        // Notify callbacks
        callbacks.onAreaTapped(areaIndex, waypoint)

        // Disable map gestures to prevent conflicts with pin dragging
        disableMapGestures()

        // Enter edit mode
        val success = editModeManager.enterAreaEdit(
            areaIndex = areaIndex,
            waypoint = waypoint,
            targetZoom = 3.0f
        )

        if (success) {
            callbacks.onEditModeEntered(areaIndex, waypoint)
            callbacks.onZoomToArea(waypoint, 3.0f)
        }
    }

    /**
     * Handle tap outside AAT areas - exit edit mode
     */
    private fun handleOutsideTap() {

        if (editModeManager.currentSession.isEditingArea) {
            // Re-enable map gestures before exiting edit mode
            enableMapGestures()

            // Exit edit mode
            val previousSession = editModeManager.exitEditMode(overviewZoom = 1.0f)

            callbacks.onEditModeExited()
            callbacks.onZoomToOverview(1.0f)

        }
    }

    /**
     * Handle drag start - check if we're starting to drag a pin
     */
    private fun handleDragStart(screenX: Float, screenY: Float) {
        // Only allow dragging in edit mode
        if (!editModeManager.currentSession.isEditingArea) {
            return
        }

        // Check if we're starting drag on a target point
        val hitPointIndex = callbacks.onCheckTargetPointHit(screenX, screenY)
        if (hitPointIndex != null) {
            isDragging = true
            draggedPointIndex = hitPointIndex
        }
    }

    /**
     * Handle drag movement - update pin position during drag
     *  FIX: Use AATMovablePointManager for proper boundary validation (no artificial constraints)
     */
    private fun handleDragMove(screenX: Float, screenY: Float) {
        if (!isDragging || draggedPointIndex == null) {
            return
        }

        val converter = coordinateConverter ?: return
        val currentLatLng = converter.screenToMap(screenX, screenY) ?: return

        val newPosition = AATLatLng(currentLatLng.latitude, currentLatLng.longitude)

        //  FIX: Let AATMovablePointManager handle validation properly
        // It knows about all geometry types (cylinder, sector, keyhole)
        val waypoint = aatWaypoints.getOrNull(draggedPointIndex!!) ?: return
        val validatedWaypoint = movablePointManager.moveTargetPoint(
            waypoint,
            newPosition.latitude,
            newPosition.longitude
        )

        // Update pin position with validated result
        callbacks.onTargetPointMoved(draggedPointIndex!!, validatedWaypoint.targetPoint)

    }

    /**
     * Handle drag end - clean up drag state
     */
    private fun handleDragEnd() {
        if (isDragging && draggedPointIndex != null) {

            // Final position is already set by handleDragMove
            // Just clean up state
            isDragging = false
            draggedPointIndex = null
        }
    }

    //  REMOVED: applyAreaBoundaryConstraints() - Was causing the restriction bug!
    // This function snapped pins to center when outside bounds, creating artificial restrictions.
    // Now using AATMovablePointManager.moveTargetPoint() which handles all geometry types correctly.

    /**
     * Get current edit mode state
     */
    fun getCurrentEditState(): AATEditSession = editModeManager.currentSession

    /**
     * Check if currently in edit mode
     */
    fun isInEditMode(): Boolean = editModeManager.currentSession.isEditingArea

    /**
     * Get focused area index (-1 if none)
     */
    fun getFocusedAreaIndex(): Int = editModeManager.currentSession.focusedAreaIndex

    /**
     * Manually exit edit mode (for external triggers)
     */
    fun exitEditMode() {
        if (editModeManager.currentSession.isEditingArea) {
            enableMapGestures() // Ensure gestures are re-enabled
            handleOutsideTap()
        }
    }

    /**
     * Update target point position (for drag operations)
     */
    fun updateTargetPoint(newPosition: AATLatLng): Boolean {
        val success = editModeManager.updateTargetPoint(newPosition)

        if (success) {
            val focusedIndex = editModeManager.currentSession.focusedAreaIndex
            callbacks.onTargetPointMoved(focusedIndex, newPosition)
        }

        return success
    }

    /**
     * Save current edit session changes
     */
    fun saveEditChanges(): AATWaypoint? {
        return editModeManager.saveChanges()
    }

    /**
     * Discard current edit session changes
     */
    fun discardEditChanges() {
        editModeManager.discardChanges()
    }

    /**
     * Disable MapLibre gestures to prevent conflicts during pin dragging
     */
    private fun disableMapGestures() {
        mapLibreMap?.uiSettings?.let { uiSettings ->
            uiSettings.setAllGesturesEnabled(false)
        }
    }

    /**
     * Re-enable MapLibre gestures after exiting edit mode
     */
    private fun enableMapGestures() {
        mapLibreMap?.uiSettings?.let { uiSettings ->
            uiSettings.setAllGesturesEnabled(true)
        }
    }
}

