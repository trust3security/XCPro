package com.trust3.xcpro.map

/**
 * UI-facing wind summary derived from RealTimeFlightData.
 * Direction is the last known wind-from bearing in degrees.
 */
data class WindIndicatorState(
    val directionFromDeg: Float? = null,
    val isValid: Boolean = false,
    val quality: Int = 0,
    val ageSeconds: Long = -1
)

/**
 * Screen-relative wind arrow state for UI rendering.
 */
data class WindArrowUiState(
    val directionScreenDeg: Float = 0f,
    val isValid: Boolean = false
)
