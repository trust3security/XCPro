package com.example.xcpro.sensors

import android.location.Location

/**
 * Data models for Flight Data Calculator
 *
 * Extracted from FlightDataCalculator.kt to maintain 500-line file limit.
 *
 * Models:
 * - WindData: Wind speed/direction calculation result
 * - LocationWithTime: GPS history tracking point
 * - VerticalSpeedPoint: Vertical speed history point
 * - Quad: Helper for combining 4 sensor flows
 */

/**
 * Wind data result
 */
data class WindData(
    val speed: Float,       // m/s
    val direction: Float,   // 0-360° (direction wind is coming FROM)
    val confidence: Float   // 0-1 (confidence in calculation)
)

/**
 * Location history point with timestamp and motion data
 * Used for wind and L/D calculations
 */
internal data class LocationWithTime(
    val location: Location,
    val timestamp: Long,
    val groundSpeed: Float,
    val track: Float
)

/**
 * Vertical speed history point
 * Used for thermal average calculations
 */
internal data class VerticalSpeedPoint(
    val verticalSpeed: Float,
    val timestamp: Long,
    val altitude: Double
)

/**
 * Helper data class for combining 4 sensor flows
 */
internal data class Quad<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
