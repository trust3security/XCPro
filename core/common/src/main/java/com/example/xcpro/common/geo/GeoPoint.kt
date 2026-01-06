package com.example.xcpro.common.geo

/**
 * Map-agnostic geographic coordinate in WGS84 degrees.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = latitude in -90.0..90.0 && longitude in -180.0..180.0

    val isZero: Boolean
        get() = latitude == 0.0 && longitude == 0.0
}
