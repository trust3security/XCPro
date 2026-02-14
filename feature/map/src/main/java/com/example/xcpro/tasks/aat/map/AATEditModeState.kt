package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.core.time.Clock
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * AAT Edit Mode State Management - Phase 1 Foundation
 *
 * Centralized state management for AAT interactive turnpoint editing.
 * Handles transitions between view mode and edit mode for individual AAT areas.
 *
 * States:
 * - VIEW_MODE: Normal task overview, all areas visible
 * - AREA_EDIT: Focused on single AAT area, turnpoint movable within bounds
 * - TRANSITIONING: Animation between states
 *
 * Features:
 * - State validation and transitions
 * - Zoom level management
 * - Focus area tracking
 * - Edit session timing
 */

enum class AATEditState {
    VIEW_MODE,      // Normal overview of entire task
    AREA_EDIT,      // Editing specific AAT area
    TRANSITIONING   // Animation between states
}

data class AATEditSession(
    val state: AATEditState,
    val focusedAreaIndex: Int = -1,
    val focusedWaypoint: AATWaypoint? = null,
    val originalTargetPoint: AATLatLng? = null,
    val currentTargetPoint: AATLatLng? = null,
    val zoomLevel: Float = 1.0f,
    val sessionStartTime: Long = 0L,
    val hasUnsavedChanges: Boolean = false
) {
    val isEditingArea: Boolean get() = state == AATEditState.AREA_EDIT
    val isInViewMode: Boolean get() = state == AATEditState.VIEW_MODE
    val isTransitioning: Boolean get() = state == AATEditState.TRANSITIONING

    fun sessionDurationMs(nowMs: Long): Long = (nowMs - sessionStartTime).coerceAtLeast(0L)

    /**
     * Check if target point has been moved from original position
     */
    val hasMovedTargetPoint: Boolean get() {
        if (originalTargetPoint == null || currentTargetPoint == null) return false
        val distance = AATMathUtils.calculateDistanceKm(
            originalTargetPoint.latitude, originalTargetPoint.longitude,
            currentTargetPoint.latitude, currentTargetPoint.longitude
        )
        return distance > 0.00001 // ~1 meter tolerance (km)
    }
}

/**
 * AAT Edit Mode State Manager
 * Manages state transitions and validation for AAT area editing
 */
@Composable
fun rememberAATEditModeState(
    clock: Clock,
    initialState: AATEditState = AATEditState.VIEW_MODE
): AATEditModeStateManager {
    return remember(clock, initialState) { AATEditModeStateManager(clock, initialState) }
}

class AATEditModeStateManager(
    private val clock: Clock,
    initialState: AATEditState = AATEditState.VIEW_MODE
) {
    private val nowMs: () -> Long = clock::nowMonoMs
    private val currentSessionState = MutableStateFlow(
        AATEditSession(state = initialState, sessionStartTime = nowMs())
    )

    val currentSession: AATEditSession get() = currentSessionState.value

    /**
     * Enter edit mode for specific AAT area
     */
    fun enterAreaEdit(
        areaIndex: Int,
        waypoint: AATWaypoint,
        targetZoom: Float = 3.0f
    ): Boolean {
        if (currentSessionState.value.state == AATEditState.AREA_EDIT) {
            return false
        }


        currentSessionState.value = AATEditSession(
            state = AATEditState.AREA_EDIT,
            focusedAreaIndex = areaIndex,
            focusedWaypoint = waypoint,
            originalTargetPoint = waypoint.targetPoint,
            currentTargetPoint = waypoint.targetPoint,
            zoomLevel = targetZoom,
            sessionStartTime = nowMs()
        )

        return true
    }

    /**
     * Exit edit mode and return to overview
     */
    fun exitEditMode(overviewZoom: Float = 1.0f): AATEditSession {
        val previousSession = currentSessionState.value


        currentSessionState.value = AATEditSession(
            state = AATEditState.VIEW_MODE,
            zoomLevel = overviewZoom,
            sessionStartTime = nowMs()
        )

        return previousSession
    }

    /**
     * Update target point position during editing
     */
    fun updateTargetPoint(newPosition: AATLatLng): Boolean {
        if (currentSessionState.value.state != AATEditState.AREA_EDIT) {
            return false
        }

        val waypoint = currentSessionState.value.focusedWaypoint
        if (waypoint == null) {
            return false
        }

        // Validate new position is within area bounds
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            newPosition.latitude, newPosition.longitude
        )

        val maxDistance = waypoint.assignedArea.radiusMeters / 1000.0

        if (distance > maxDistance) {
            return false
        }

        currentSessionState.value = currentSessionState.value.copy(
            currentTargetPoint = newPosition,
            hasUnsavedChanges = true
        )

        return true
    }

    /**
     * Save changes and apply to waypoint
     */
    fun saveChanges(): AATWaypoint? {
        val session = currentSessionState.value
        if (session.state != AATEditState.AREA_EDIT || !session.hasUnsavedChanges) {
            return null
        }

        val waypoint = session.focusedWaypoint
        val newTargetPoint = session.currentTargetPoint

        if (waypoint == null || newTargetPoint == null) {
            return null
        }

        val updatedWaypoint = waypoint.copy(
            targetPoint = newTargetPoint,
            isTargetPointCustomized = session.hasMovedTargetPoint
        )

        currentSessionState.value = session.copy(hasUnsavedChanges = false)

        return updatedWaypoint
    }

    /**
     * Discard changes and revert to original position
     */
    fun discardChanges() {
        val session = currentSessionState.value
        if (session.originalTargetPoint != null) {
            currentSessionState.value = session.copy(
                currentTargetPoint = session.originalTargetPoint,
                hasUnsavedChanges = false
            )
        }
    }

    /**
     * Check if specific area is currently focused
     */
    fun isAreaFocused(areaIndex: Int): Boolean {
        val session = currentSessionState.value
        return session.isEditingArea && session.focusedAreaIndex == areaIndex
    }

    /**
     * Get current zoom level for map display
     */
    fun getCurrentZoomLevel(): Float = currentSessionState.value.zoomLevel

    /**
     * Update zoom level
     */
    fun updateZoomLevel(newZoom: Float) {
        currentSessionState.value = currentSessionState.value.copy(zoomLevel = newZoom)
    }
}
