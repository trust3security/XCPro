package com.example.xcpro.map

import androidx.compose.ui.geometry.Rect

/**
 * Describes a rectangular overlay region that should intercept or deflect map gestures.
 *
 * @property target identifies which overlay registered the region (for lookups/removal).
 * @property bounds screen-space bounds in root coordinates; used to hit-test pointer input.
 * @property consumeGestures when true, the map gesture layer must short-circuit handling.
 */
data class MapGestureRegion(
    val target: MapOverlayGestureTarget,
    val bounds: Rect,
    val consumeGestures: Boolean = true
)

/**
 * Enumerates overlay widgets that participate in gesture pass-through routing.
 */
enum class MapOverlayGestureTarget {
    SIDE_HAMBURGER,
    FLIGHT_MODE,
    VARIOMETER
}
