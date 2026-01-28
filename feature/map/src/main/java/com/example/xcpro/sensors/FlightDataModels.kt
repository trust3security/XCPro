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
 * - Quad: Helper for combining 4 sensor flows
 */

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
 * Thermal climb information mirroring OneClimbInfo behavior
 */
internal data class ThermalClimbInfo(
    var startTime: Long = 0L,
    var endTime: Long = 0L,
    var startTeAltitude: Double = Double.NaN,
    var endTeAltitude: Double = Double.NaN,
    var gain: Double = 0.0,
    var liftRate: Double = 0.0
) {
    val durationSeconds: Double
        get() = if (isDefined()) (endTime - startTime).coerceAtLeast(0L) / 1000.0 else 0.0

    fun isDefined(): Boolean =
        startTime > 0L && endTime >= startTime &&
            startTeAltitude.isFinite() && endTeAltitude.isFinite()

    fun clear() {
        startTime = 0L
        endTime = 0L
        startTeAltitude = Double.NaN
        endTeAltitude = Double.NaN
        gain = 0.0
        liftRate = 0.0
    }

    fun copyFrom(other: ThermalClimbInfo) {
        startTime = other.startTime
        endTime = other.endTime
        startTeAltitude = other.startTeAltitude
        endTeAltitude = other.endTeAltitude
        gain = other.gain
        liftRate = other.liftRate
    }
}
