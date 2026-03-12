package com.example.xcpro.tasks.domain.model

/**
 * Lightweight stats container the domain emits after validation/calculation.
 * Distances in meters, speeds in m/s; callers can format as needed.
 */
data class TaskStats(
    val distanceNominal: Double = 0.0,
    val distanceMin: Double = 0.0,
    val distanceMax: Double = 0.0,
    val activeIndex: Int = 0,
    val hasTargets: Boolean = false,
    val isTaskValid: Boolean = false
)
