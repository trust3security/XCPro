package com.example.xcpro.tasks.aat.map

import androidx.compose.runtime.*
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.calculations.AATMathUtils

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
    val sessionStartTime: Long = System.currentTimeMillis(),
    val hasUnsavedChanges: Boolean = false
) {
    val isEditingArea: Boolean get() = state == AATEditState.AREA_EDIT
    val isInViewMode: Boolean get() = state == AATEditState.VIEW_MODE
    val isTransitioning: Boolean get() = state == AATEditState.TRANSITIONING

    val sessionDurationMs: Long get() = System.currentTimeMillis() - sessionStartTime

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
    initialState: AATEditState = AATEditState.VIEW_MODE
): AATEditModeStateManager {
    return remember { AATEditModeStateManager(initialState) }
}

class AATEditModeStateManager(
    initialState: AATEditState = AATEditState.VIEW_MODE
) {
    private var _currentSession by mutableStateOf(
        AATEditSession(state = initialState)
    )

    val currentSession: AATEditSession get() = _currentSession

    /**
     * Enter edit mode for specific AAT area
     */
    fun enterAreaEdit(
        areaIndex: Int,
        waypoint: AATWaypoint,
        targetZoom: Float = 3.0f
    ): Boolean {
        if (_currentSession.state == AATEditState.AREA_EDIT) {
            println("⚠️ AAT: Already in edit mode for area ${_currentSession.focusedAreaIndex}")
            return false
        }

        println("🎯 AAT: Entering edit mode for area $areaIndex (${waypoint.title})")

        _currentSession = AATEditSession(
            state = AATEditState.AREA_EDIT,
            focusedAreaIndex = areaIndex,
            focusedWaypoint = waypoint,
            originalTargetPoint = waypoint.targetPoint,
            currentTargetPoint = waypoint.targetPoint,
            zoomLevel = targetZoom,
            sessionStartTime = System.currentTimeMillis()
        )

        return true
    }

    /**
     * Exit edit mode and return to overview
     */
    fun exitEditMode(overviewZoom: Float = 1.0f): AATEditSession {
        val previousSession = _currentSession

        println("🎯 AAT: Exiting edit mode (session duration: ${previousSession.sessionDurationMs}ms)")

        _currentSession = AATEditSession(
            state = AATEditState.VIEW_MODE,
            zoomLevel = overviewZoom
        )

        return previousSession
    }

    /**
     * Update target point position during editing
     */
    fun updateTargetPoint(newPosition: AATLatLng): Boolean {
        if (_currentSession.state != AATEditState.AREA_EDIT) {
            println("❌ AAT: Cannot update target point - not in edit mode")
            return false
        }

        val waypoint = _currentSession.focusedWaypoint
        if (waypoint == null) {
            println("❌ AAT: Cannot update target point - no focused waypoint")
            return false
        }

        // Validate new position is within area bounds
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            newPosition.latitude, newPosition.longitude
        )

        val maxDistance = waypoint.assignedArea.radiusMeters / 1000.0

        if (distance > maxDistance) {
            println("❌ AAT: Target point outside area bounds (${String.format("%.2f", distance)}km > ${String.format("%.2f", maxDistance)}km)")
            return false
        }

        _currentSession = _currentSession.copy(
            currentTargetPoint = newPosition,
            hasUnsavedChanges = true
        )

        println("✅ AAT: Updated target point to ${String.format("%.6f", newPosition.latitude)}, ${String.format("%.6f", newPosition.longitude)}")
        return true
    }

    /**
     * Save changes and apply to waypoint
     */
    fun saveChanges(): AATWaypoint? {
        if (_currentSession.state != AATEditState.AREA_EDIT || !_currentSession.hasUnsavedChanges) {
            return null
        }

        val waypoint = _currentSession.focusedWaypoint
        val newTargetPoint = _currentSession.currentTargetPoint

        if (waypoint == null || newTargetPoint == null) {
            return null
        }

        val updatedWaypoint = waypoint.copy(
            targetPoint = newTargetPoint,
            isTargetPointCustomized = _currentSession.hasMovedTargetPoint
        )

        _currentSession = _currentSession.copy(hasUnsavedChanges = false)

        println("💾 AAT: Saved changes to ${waypoint.title}")
        return updatedWaypoint
    }

    /**
     * Discard changes and revert to original position
     */
    fun discardChanges() {
        if (_currentSession.originalTargetPoint != null) {
            _currentSession = _currentSession.copy(
                currentTargetPoint = _currentSession.originalTargetPoint,
                hasUnsavedChanges = false
            )
            println("↩️ AAT: Discarded changes, reverted to original position")
        }
    }

    /**
     * Check if specific area is currently focused
     */
    fun isAreaFocused(areaIndex: Int): Boolean {
        return _currentSession.isEditingArea && _currentSession.focusedAreaIndex == areaIndex
    }

    /**
     * Get current zoom level for map display
     */
    fun getCurrentZoomLevel(): Float = _currentSession.zoomLevel

    /**
     * Update zoom level
     */
    fun updateZoomLevel(newZoom: Float) {
        _currentSession = _currentSession.copy(zoomLevel = newZoom)
    }
}