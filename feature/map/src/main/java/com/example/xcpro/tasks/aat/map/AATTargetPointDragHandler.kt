package com.example.xcpro.tasks.aat.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.IntOffset
import org.maplibre.android.geometry.LatLng
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.map.AATMovablePointManager
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import kotlin.math.*

/**
 * AAT Target Point Drag Handler - Phase 2 Interactive Features
 *
 * Handles drag and drop operations for moving AAT target points within area boundaries.
 * Provides real-time feedback and boundary validation during dragging.
 *
 * Features:
 * - Real-time drag with boundary validation
 * - Visual feedback during drag operations
 * - Smooth animation and snapping
 * - Coordinate clamping to area bounds
 * - Live distance calculations
 *
 * Usage:
 * - Attach to target point UI elements
 * - Validates moves within AAT area bounds
 * - Updates target position in real-time
 * - Provides haptic feedback for boundaries
 */

data class DragState(
    val isDragging: Boolean = false,
    val startPosition: Offset = Offset.Zero,
    val currentPosition: Offset = Offset.Zero,
    val dragOffset: Offset = Offset.Zero,
    val isValidPosition: Boolean = true
) {
    val totalDragDistance: Float
        get() = sqrt(
            (currentPosition.x - startPosition.x) * (currentPosition.x - startPosition.x) +
            (currentPosition.y - startPosition.y) * (currentPosition.y - startPosition.y)
        )
}

@Composable
fun rememberAATTargetPointDragHandler(
    waypoint: AATWaypoint,
    currentTargetPoint: AATLatLng,
    coordinateConverter: AATMapCoordinateConverter?,
    isEditMode: Boolean,
    onDragStart: () -> Unit = {},
    onDragUpdate: (AATLatLng) -> Unit = {},
    onDragEnd: (AATLatLng) -> Unit = {},
    onInvalidPosition: () -> Unit = {},
    dragThreshold: Float = 10f
): AATTargetPointDragHandler {
    return remember(waypoint, isEditMode) {
        AATTargetPointDragHandler(
            waypoint = waypoint,
            coordinateConverter = coordinateConverter,
            isEditMode = isEditMode,
            onDragStart = onDragStart,
            onDragUpdate = onDragUpdate,
            onDragEnd = onDragEnd,
            onInvalidPosition = onInvalidPosition,
            dragThreshold = dragThreshold
        )
    }
}

class AATTargetPointDragHandler(
    private val waypoint: AATWaypoint,
    private val coordinateConverter: AATMapCoordinateConverter?,
    private val isEditMode: Boolean,
    private val onDragStart: () -> Unit,
    private val onDragUpdate: (AATLatLng) -> Unit,
    private val onDragEnd: (AATLatLng) -> Unit,
    private val onInvalidPosition: () -> Unit,
    private val dragThreshold: Float = 10f
) {
    private var dragState by mutableStateOf(DragState())
    private var lastValidPosition by mutableStateOf(waypoint.targetPoint)
    private val movablePointManager = AATMovablePointManager()

    /**
     * Pointer input modifier for drag gestures
     */
    val dragModifier = Modifier.pointerInput(isEditMode, waypoint.id) {
        if (!isEditMode) return@pointerInput

        detectDragGestures(
            onDragStart = { startOffset ->
                handleDragStart(startOffset)
            },
            onDragEnd = {
                handleDragEnd()
            },
            onDrag = { _, dragAmount ->
                handleDrag(dragAmount)
            }
        )
    }

    /**
     * Handle drag start
     */
    private fun handleDragStart(startOffset: Offset) {
        println("🎯 AAT: Starting target point drag at $startOffset")

        dragState = DragState(
            isDragging = true,
            startPosition = startOffset,
            currentPosition = startOffset
        )

        onDragStart()
    }

    /**
     * Handle drag movement
     * Uses movablePointManager to clamp to the allowed geometry.
     */
    private fun handleDrag(dragAmount: Offset) {
        if (!dragState.isDragging) return

        val newPosition = dragState.currentPosition + dragAmount
        val newDragOffset = dragState.dragOffset + dragAmount

        // Convert screen position to map coordinates
        val mapCoords = coordinateConverter?.screenToMap(newPosition.x, newPosition.y)

        if (mapCoords != null) {
            val newTargetPoint = AATLatLng(mapCoords.latitude, mapCoords.longitude)

            // Clamp/validate within area bounds using shared geometry-aware validator
            val validatedWaypoint = movablePointManager.moveTargetPoint(
                waypoint,
                newTargetPoint.latitude,
                newTargetPoint.longitude
            )
            val validatedPoint = validatedWaypoint.targetPoint

            lastValidPosition = validatedPoint
            dragState = dragState.copy(
                currentPosition = newPosition,
                dragOffset = newDragOffset,
                isValidPosition = true
            )

            onDragUpdate(validatedPoint)
        }
    }

    /**
     * Handle drag end using the last valid in-bounds position.
     */
    private fun handleDragEnd() {
        if (!dragState.isDragging) return

        println("🎯 AAT: Ending target point drag (distance: ${dragState.totalDragDistance}px)")

        // ✅ FIX: Always use last valid position - no clamping
        // If user dragged outside bounds, pin stays at last valid inside position
        val finalPosition = lastValidPosition

        dragState = DragState() // Reset to default state
        onDragEnd(finalPosition)
    }

    /**
     * Clamp position to area boundary
     */
    private fun clampToBoundary(position: AATLatLng): AATLatLng {
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            position.latitude, position.longitude
        )

        val maxDistanceKm = waypoint.assignedArea.radiusMeters / 1000.0

        if (distance <= maxDistanceKm) {
            return position // Already within bounds
        }

        // Calculate bearing from center to position
        val bearing = calculateBearing(waypoint.lat, waypoint.lon, position.latitude, position.longitude)

        // Calculate new position at max distance
        return calculateDestination(waypoint.lat, waypoint.lon, bearing, maxDistanceKm)
    }

    /**
     * Get current drag state
     */
    fun getCurrentDragState(): DragState = dragState

    /**
     * Check if currently dragging
     */
    fun isDragging(): Boolean = dragState.isDragging

    /**
     * Get drag offset for visual feedback
     */
    fun getDragOffset(): IntOffset = IntOffset(
        dragState.dragOffset.x.roundToInt(),
        dragState.dragOffset.y.roundToInt()
    )
}

/**
 * Calculate bearing between two points
 */
private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)

    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) -
            sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

    val bearing = atan2(y, x)
    return (Math.toDegrees(bearing) + 360) % 360
}

/**
 * Calculate destination point given start point, bearing, and distance
 */
private fun calculateDestination(lat: Double, lon: Double, bearing: Double, distanceKm: Double): AATLatLng {
    val earthRadiusKm = 6371.0
    val bearingRad = Math.toRadians(bearing)
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val angularDistance = distanceKm / earthRadiusKm

    val destLatRad = asin(
        sin(latRad) * cos(angularDistance) +
        cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )

    val destLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(destLatRad)
    )

    return AATLatLng(
        latitude = Math.toDegrees(destLatRad),
        longitude = Math.toDegrees(destLonRad)
    )
}

/**
 * Composable for draggable target point with visual feedback
 */
@Composable
fun DraggableTargetPoint(
    dragHandler: AATTargetPointDragHandler,
    content: @Composable () -> Unit
) {
    val dragState = dragHandler.getCurrentDragState()

    Box(
        modifier = Modifier
            .offset { dragHandler.getDragOffset() }
            .then(dragHandler.dragModifier)
    ) {
        content()

        // Visual feedback during drag
        if (dragState.isDragging) {
            // Add visual feedback overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = if (dragState.isValidPosition) {
                            Color(0xFF388E3C).copy(alpha = 0.3f)
                        } else {
                            Color.Red.copy(alpha = 0.3f)
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}
