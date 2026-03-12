package com.example.xcpro.tasks.aat.navigation

import com.example.xcpro.tasks.aat.SimpleAATTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    // Current leg state
    private val currentLegState = MutableStateFlow(0)
    val currentLegFlow: StateFlow<Int> = currentLegState.asStateFlow()
    val currentLeg: Int get() = currentLegState.value

    /**
     * Set the current leg directly
     *
     * Used when initializing or resetting navigation state.
     *
     * @param leg The leg index (0-based)
     */
    fun setCurrentLeg(leg: Int) {
        currentLegState.value = leg
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
        if (currentLegState.value > 0) {
            currentLegState.value = currentLegState.value - 1
        } else {
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
        if (currentLegState.value < task.waypoints.size - 1) {
            currentLegState.value = currentLegState.value + 1
        } else {
        }
    }

    /**
     * Reset navigation to start of task
     *
     * Sets current leg to 0 (first leg).
     */
    fun resetToStart() {
        currentLegState.value = 0
    }

    /**
     * Get the current leg index
     *
     * @return The 0-based leg index
     */
    fun getCurrentLegIndex(): Int = currentLegState.value

    /**
     * Check if at the first leg
     *
     * @return true if current leg is 0
     */
    fun isAtFirstLeg(): Boolean = currentLegState.value == 0

    /**
     * Check if at the last leg
     *
     * @param task The current task
     * @return true if current leg is the last waypoint
     */
    fun isAtLastLeg(task: SimpleAATTask): Boolean = currentLegState.value >= task.waypoints.size - 1
}
