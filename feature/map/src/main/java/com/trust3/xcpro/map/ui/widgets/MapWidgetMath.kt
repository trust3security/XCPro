package com.trust3.xcpro.map.ui.widgets

import androidx.compose.ui.geometry.Offset

/**
 * Shared math helpers for draggable map widgets.
 */
internal object MapWidgetMath {
    fun boundedOffset(current: Offset, drag: Offset, maxX: Float, maxY: Float): Offset {
        return Offset(
            x = (current.x + drag.x).coerceIn(0f, maxX.coerceAtLeast(0f)),
            y = (current.y + drag.y).coerceIn(0f, maxY.coerceAtLeast(0f))
        )
    }
}
