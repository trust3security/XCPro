package com.example.xcpro.tasks.aat.map

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import android.graphics.PointF
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import kotlinx.coroutines.delay
import kotlin.math.*

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
    callbacks: AATInteractionCallbacks = AATInteractionCallbacks()
): AATMapInteractionHandler {
    return remember(aatWaypoints) {
        AATMapInteractionHandler(
            aatWaypoints = aatWaypoints,
            callbacks = callbacks
        )
    }
}

class AATMapInteractionHandler(
    private var aatWaypoints: List<AATWaypoint>,
    private val callbacks: AATInteractionCallbacks
) {
    private var mapLibreMap: MapLibreMap? = null
    private var coordinateConverter: AATMapCoordinateConverter? = null

    // Edit mode state
    private val editModeManager = AATEditModeStateManager()

    // Drag state tracking
    private var isDragging = false
    private var draggedPointIndex: Int? = null
    private var dragStartPoint: AATLatLng? = null

    /**
     * Attach to MapLibre map instance
     */
    fun attachToMap(map: MapLibreMap?) {
        mapLibreMap = map
        coordinateConverter = map?.let { AATMapCoordinateConverterFactory.create(it) }
        println("🎯 AAT: Interaction handler attached to map")
    }

    /**
     * Update AAT waypoints list
     */
    fun updateWaypoints(newWaypoints: List<AATWaypoint>) {
        aatWaypoints = newWaypoints
        println("🎯 AAT: Updated waypoints list (${newWaypoints.size} waypoints)")
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
            screenX, screenY, converter
        ) ?: return

        println("🎯 AAT: Tap detected at ${tapDetails.mapCoordinates.latitude}, ${tapDetails.mapCoordinates.longitude}")

        // Find which AAT area was tapped
        val tappedArea = findTappedAATArea(
            tapDetails.mapCoordinates.latitude,
            tapDetails.mapCoordinates.longitude
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
        println("🎯 AAT: Tap on area $areaIndex (${waypoint.title})")

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
            println("🎯 AAT: Edit mode entered and zoomed in - map gestures disabled for pin dragging")
        }
    }

    /**
     * Handle tap outside AAT areas - exit edit mode
     */
    private fun handleOutsideTap() {
        println("🎯 AAT: Tap outside AAT areas")

        if (editModeManager.currentSession.isEditingArea) {
            // Re-enable map gestures before exiting edit mode
            enableMapGestures()

            // Exit edit mode
            val previousSession = editModeManager.exitEditMode(overviewZoom = 1.0f)

            callbacks.onEditModeExited()
            callbacks.onZoomToOverview(1.0f)

            println("🎯 AAT: Exited edit mode after ${previousSession.sessionDurationMs}ms - map gestures restored")
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

            val converter = coordinateConverter
            if (converter != null) {
                val startLatLng = converter.screenToMap(screenX, screenY)
                if (startLatLng != null) {
                    dragStartPoint = AATLatLng(startLatLng.latitude, startLatLng.longitude)
                    println("🎯 AAT: Started dragging pin $hitPointIndex from ${startLatLng.latitude}, ${startLatLng.longitude}")
                }
            }
        }
    }

    /**
     * Handle drag movement - update pin position during drag
     * ✅ FIX: Use AATMovablePointManager for proper boundary validation (no artificial constraints)
     */
    private fun handleDragMove(screenX: Float, screenY: Float) {
        if (!isDragging || draggedPointIndex == null) {
            return
        }

        val converter = coordinateConverter ?: return
        val currentLatLng = converter.screenToMap(screenX, screenY) ?: return

        val newPosition = AATLatLng(currentLatLng.latitude, currentLatLng.longitude)

        // ✅ FIX: Let AATMovablePointManager handle validation properly
        // It knows about all geometry types (cylinder, sector, keyhole)
        val waypoint = aatWaypoints.getOrNull(draggedPointIndex!!) ?: return
        val movablePointManager = AATMovablePointManager()
        val validatedWaypoint = movablePointManager.moveTargetPoint(
            waypoint,
            newPosition.latitude,
            newPosition.longitude
        )

        // Update pin position with validated result
        callbacks.onTargetPointMoved(draggedPointIndex!!, validatedWaypoint.targetPoint)

        println("🎯 AAT: Dragging pin ${draggedPointIndex} to ${validatedWaypoint.targetPoint.latitude}, ${validatedWaypoint.targetPoint.longitude}")
    }

    /**
     * Handle drag end - clean up drag state
     */
    private fun handleDragEnd() {
        if (isDragging && draggedPointIndex != null) {
            println("🎯 AAT: Finished dragging pin ${draggedPointIndex}")

            // Final position is already set by handleDragMove
            // Just clean up state
            isDragging = false
            draggedPointIndex = null
            dragStartPoint = null
        }
    }

    // ❌ REMOVED: applyAreaBoundaryConstraints() - Was causing the restriction bug!
    // This function snapped pins to center when outside bounds, creating artificial restrictions.
    // Now using AATMovablePointManager.moveTargetPoint() which handles all geometry types correctly.

    /**
     * Calculate bearing from point1 to point2 in degrees (0-360)
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Check if an angle falls within a sector defined by start and end angles
     */
    private fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = (angle + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        return if (normalizedEnd >= normalizedStart) {
            // Normal case: sector doesn't cross 0°
            normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd
        } else {
            // Sector crosses 0° (e.g., 350° to 10°)
            normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
        }
    }

    /**
     * Find which AAT area contains the given coordinates
     * ✅ FIX: Proper geometry-aware checking for all turnpoint types (Circle, Sector, Keyhole)
     * @return Pair of (areaIndex, waypoint) or null if not found
     */
    private fun findTappedAATArea(lat: Double, lon: Double): Pair<Int, AATWaypoint>? {
        aatWaypoints.forEachIndexed { index, waypoint ->
            val distance = AATMathUtils.calculateDistanceKm(lat, lon, waypoint.lat, waypoint.lon)

            // ✅ FIX: Check area based on actual geometry shape
            val isInArea = when (waypoint.assignedArea.shape) {
                com.example.xcpro.tasks.aat.models.AATAreaShape.CIRCLE -> {
                    // Simple circle check
                    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                    distance <= radiusKm
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.SECTOR -> {
                    // Sector or Keyhole: Check if in inner cylinder OR outer sector
                    val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
                    val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

                    if (innerRadiusKm > 0.0) {
                        // KEYHOLE: Check inner cylinder OR sector
                        if (distance <= innerRadiusKm) {
                            true // Inside inner cylinder (always valid)
                        } else if (distance <= outerRadiusKm) {
                            // Check if within sector angles
                            val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                            isAngleInSector(bearing, waypoint.assignedArea.startAngleDegrees, waypoint.assignedArea.endAngleDegrees)
                        } else {
                            false
                        }
                    } else {
                        // SECTOR: Check if within outer radius AND sector angles
                        if (distance <= outerRadiusKm) {
                            val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                            isAngleInSector(bearing, waypoint.assignedArea.startAngleDegrees, waypoint.assignedArea.endAngleDegrees)
                        } else {
                            false
                        }
                    }
                }
                com.example.xcpro.tasks.aat.models.AATAreaShape.LINE -> {
                    // Line check (for start/finish)
                    val halfWidth = (waypoint.assignedArea.lineWidthMeters / 1000.0) / 2.0
                    distance <= halfWidth
                }
            }

            if (isInArea) {
                println("🎯 AAT: Double-click detected in ${waypoint.title} area (${String.format("%.2f", distance)}km from center, shape: ${waypoint.assignedArea.shape})")
                return Pair(index, waypoint)
            }
        }
        return null
    }

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
            println("🎯 AAT: Map gestures disabled for edit mode")
        }
    }

    /**
     * Re-enable MapLibre gestures after exiting edit mode
     */
    private fun enableMapGestures() {
        mapLibreMap?.uiSettings?.let { uiSettings ->
            uiSettings.setAllGesturesEnabled(true)
            println("🎯 AAT: Map gestures re-enabled after edit mode")
        }
    }
}

