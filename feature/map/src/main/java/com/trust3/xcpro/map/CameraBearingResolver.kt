package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode

/**
 * Resolves the map camera bearing for the requested orientation mode.
 */
internal fun resolveCameraBearing(
    trackBearing: Double,
    headingDeg: Double,
    orientationMode: MapOrientationMode
): Double = when (orientationMode) {
    MapOrientationMode.NORTH_UP -> 0.0
    MapOrientationMode.TRACK_UP -> trackBearing
    MapOrientationMode.HEADING_UP -> headingDeg
}
