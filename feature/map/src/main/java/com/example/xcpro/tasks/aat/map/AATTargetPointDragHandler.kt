package com.example.xcpro.tasks.aat.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _dragState = MutableStateFlow(DragState())
    val dragState: StateFlow<DragState> = _dragState.asStateFlow()
    private var lastValidPosition: AATLatLng = waypoint.targetPoint
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

        _dragState.value = DragState(
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
        val state = _dragState.value
        if (!state.isDragging) return

        val newPosition = state.currentPosition + dragAmount
        val newDragOffset = state.dragOffset + dragAmount

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
            _dragState.value = state.copy(
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
        if (!_dragState.value.isDragging) return


        //  FIX: Always use last valid position - no clamping
        // If user dragged outside bounds, pin stays at last valid inside position
        val finalPosition = lastValidPosition

        _dragState.value = DragState() // Reset to default state
        onDragEnd(finalPosition)
    }

    /**
     * Clamp position to area boundary
     */
    private fun clampToBoundary(position: AATLatLng): AATLatLng {
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            waypoint.lat, waypoint.lon,
            position.latitude, position.longitude
        )

        val maxDistanceMeters = waypoint.assignedArea.radiusMeters

        if (distanceMeters <= maxDistanceMeters) {
            return position // Already within bounds
        }

        // Calculate bearing from center to position
        val bearing = AATMathUtils.calculateBearing(
            AATLatLng(waypoint.lat, waypoint.lon),
            position
        )

        // Calculate new position at max distance
        return AATMathUtils.calculatePointAtBearingMeters(
            from = AATLatLng(waypoint.lat, waypoint.lon),
            bearing = bearing,
            distanceMeters = maxDistanceMeters
        )
    }

    /**
     * Get current drag state
     */
    fun getCurrentDragState(): DragState = _dragState.value

    /**
     * Check if currently dragging
     */
    fun isDragging(): Boolean = _dragState.value.isDragging

    /**
     * Get drag offset for visual feedback
     */
    fun getDragOffset(): IntOffset = IntOffset(
        _dragState.value.dragOffset.x.roundToInt(),
        _dragState.value.dragOffset.y.roundToInt()
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
    val dragState by dragHandler.dragState.collectAsStateWithLifecycle()

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
