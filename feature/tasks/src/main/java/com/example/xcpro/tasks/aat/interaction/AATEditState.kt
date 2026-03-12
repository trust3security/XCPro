package com.example.xcpro.tasks.aat.interaction

import com.example.xcpro.tasks.aat.models.AATWaypoint

/**
 * Minimal state holder for AAT edit interactions.
 * Keeps UI-facing flags separate from the main coordinator logic.
 */
data class AATEditState(
    val activeWaypointIndex: Int? = null,
    val activeWaypoint: AATWaypoint? = null,
    val isDragging: Boolean = false
)
