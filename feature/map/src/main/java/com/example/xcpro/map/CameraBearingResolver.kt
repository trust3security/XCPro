package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode

/**
 * Resolves the map camera bearing for the requested orientation mode.
 */
internal fun resolveCameraBearing(
    trackBearing: Double,
    magneticHeading: Double,
    orientationMode: MapOrientationMode
): Double = when (orientationMode) {
    MapOrientationMode.NORTH_UP -> 0.0
    MapOrientationMode.TRACK_UP -> trackBearing
    MapOrientationMode.HEADING_UP -> magneticHeading
    MapOrientationMode.WIND_UP -> trackBearing
}
