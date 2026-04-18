// Role: Hold shared style values derived during trail planning.
// Invariants: Width arrays align with SnailTrailPalette.NUM_COLORS.
package com.trust3.xcpro.map.trail

internal data class SnailTrailStyleCache(
    val type: TrailType,
    val valueMin: Double,
    val valueMax: Double,
    val useScaledLines: Boolean,
    val scaledWidths: FloatArray,
    val minWidth: Float
)
