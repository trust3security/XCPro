package com.example.xcpro.tasks.aat.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.xcpro.tasks.aat.SimpleAATTask

/**
 * AAT Navigation Manager
 *
 * Manages current leg navigation for AAT tasks during flight.
 * Tracks which leg the pilot is currently flying and provides
 * navigation between task legs.
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 3 - Navigation Extraction)
 * DEPENDENCIES: SimpleAATTask model
 */
class AATNavigationManager {

    // Current leg state - exposed as observable state for UI
    private var _currentLeg by mutableStateOf(0)
    val currentLeg: Int get() = _currentLeg

    /**
     * Set the current leg directly
     *
     * Used when initializing or resetting navigation state.
     *
     * @param leg The leg index (0-based)
     */
    fun setCurrentLeg(leg: Int) {
        _currentLeg = leg
        println("🔜 AAT NAVIGATION: Current leg set to: $_currentLeg")
    }

    /**
     * Navigate to previous leg in AAT task
     *
     * Decrements the current leg counter if not already at the first leg.
     * Useful for correcting navigation mistakes or reviewing previous legs.
     *
     * @param task The current task (used to validate leg bounds)
     */
    fun goToPreviousLeg(task: SimpleAATTask) {
        if (_currentLeg > 0) {
            _currentLeg--
            println("🔙 AAT NAVIGATION: Moved to previous leg: $_currentLeg")
        } else {
            println("🔙 AAT NAVIGATION: Already at first leg")
        }
    }

    /**
     * Navigate to next leg in AAT task
     *
     * Advances to the next leg if not already at the finish.
     * Called when pilot completes current leg and enters next assigned area.
     *
     * @param task The current task (used to validate leg bounds)
     */
    fun advanceToNextLeg(task: SimpleAATTask) {
        if (_currentLeg < task.waypoints.size - 1) {
            _currentLeg++
            println("🔜 AAT NAVIGATION: Advanced to next leg: $_currentLeg")
        } else {
            println("🔜 AAT NAVIGATION: Already at last leg")
        }
    }

    /**
     * Reset navigation to start of task
     *
     * Sets current leg to 0 (first leg).
     */
    fun resetToStart() {
        _currentLeg = 0
        println("🔄 AAT NAVIGATION: Reset to start (leg 0)")
    }

    /**
     * Get the current leg index
     *
     * @return The 0-based leg index
     */
    fun getCurrentLegIndex(): Int = _currentLeg

    /**
     * Check if at the first leg
     *
     * @return true if current leg is 0
     */
    fun isAtFirstLeg(): Boolean = _currentLeg == 0

    /**
     * Check if at the last leg
     *
     * @param task The current task
     * @return true if current leg is the last waypoint
     */
    fun isAtLastLeg(task: SimpleAATTask): Boolean = _currentLeg >= task.waypoints.size - 1
}
