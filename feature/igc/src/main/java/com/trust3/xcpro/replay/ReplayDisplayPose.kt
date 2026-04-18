package com.trust3.xcpro.replay

/**
 * UI-friendly replay pose snapshot derived from the runtime interpolator.
 */
data class ReplayDisplayPose(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val bearingDeg: Double,
    val speedMs: Double
)
