package com.example.xcpro.core.common.geometry

/**
 * Simple, UI-agnostic geometry types for ViewModel and domain layers.
 */
data class OffsetPx(val x: Float, val y: Float) {
    companion object {
        val Zero = OffsetPx(0f, 0f)
    }
}

data class IntSizePx(val width: Int, val height: Int)

data class DensityScale(val density: Float, val fontScale: Float = 1f)

fun DensityScale.dpToPx(dp: Float): Float = dp * density
