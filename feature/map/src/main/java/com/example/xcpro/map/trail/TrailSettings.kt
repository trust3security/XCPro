// Role: Define snail-trail display settings for map rendering.
// Invariants: Defaults match the reference behavior for length, type, drift, and scaling.
package com.example.xcpro.map.trail

/**
 * Immutable trail configuration used by map state and rendering.
 */
data class TrailSettings(
    val length: TrailLength = TrailLength.LONG,
    val type: TrailType = TrailType.VARIO_1,
    val windDriftEnabled: Boolean = true,
    val scalingEnabled: Boolean = true
)

/**
 * Time window for the trail history.
 */
enum class TrailLength {
    FULL,
    LONG,
    MEDIUM,
    SHORT,
    OFF
}

/**
 * Color/shape style for the trail segments.
 */
enum class TrailType {
    VARIO_1,
    VARIO_2,
    ALTITUDE,
    VARIO_1_DOTS,
    VARIO_2_DOTS,
    VARIO_DOTS_AND_LINES,
    VARIO_EINK
}
