package com.example.xcpro.sensors

import com.example.dfcards.calculations.ConfidenceLevel
import org.maplibre.android.geometry.LatLng
import com.example.xcpro.weather.wind.model.WindSource

/**
 * Raw GPS data from LocationManager
 * Single Source of Truth for GPS sensor readings
 */
data class GPSData(
    val latLng: LatLng,
    val altitude: Double,       // MSL in meters (Mean Sea Level)
    val speed: Double,          // m/s (ground speed)
    val bearing: Double,        // 0-360° (accurate when moving > 2 m/s)
    val accuracy: Float,        // meters (horizontal accuracy)
    val timestamp: Long
) {
    /**
     * Check if this is a high-accuracy GPS fix
     */
    val isHighAccuracy: Boolean get() = accuracy <= 10f // Within 10 meters

    /**
     * Check if device is moving (bearing is only accurate when moving)
     */
    val isMoving: Boolean get() = speed > 2.0  // > 2 m/s
}

/**
 * Raw barometer data from pressure sensor
 * Single Source of Truth for barometric sensor readings
 */
data class BaroData(
    val pressureHPa: Double,    // Atmospheric pressure in hPa
    val timestamp: Long
)

/**
 * Raw compass data from magnetometer
 * Single Source of Truth for magnetometer sensor readings
 */
data class CompassData(
    val heading: Double,        // 0-360° (magnetic north)
    val accuracy: Int,          // SensorManager.SENSOR_STATUS_*
    val timestamp: Long
)

/**
 * Raw accelerometer data from IMU
 * Single Source of Truth for linear acceleration sensor readings
 *
 * TYPE_LINEAR_ACCELERATION: Gravity already removed by Android
 * Used for zero-lag variometer with barometer fusion
 */
data class AccelData(
    val verticalAcceleration: Double,  // m/s² (earth-Z axis, positive = upward)
    val timestamp: Long,
    val isReliable: Boolean = true     // Whether orientation projection is valid
)

/**
 * Device attitude derived from rotation vector.
 */
data class AttitudeData(
    val headingDeg: Double,  // degrees, 0 = North
    val pitchDeg: Double,    // degrees, positive = nose up
    val rollDeg: Double,     // degrees, positive = right wing down
    val timestamp: Long,
    val isReliable: Boolean
)

/**
 * Complete flight data combining all sensors + calculations
 * Single Source of Truth for calculated flight parameters
 *
 * This data class combines:
 * - Raw sensor data (GPS, Barometer, Compass)
 * - Calculated barometric altitude and QNH
 * - Calculated vertical speed (from barometer)
 * - Calculated wind speed and direction
 * - Calculated thermal average
 * - Calculated L/D ratio
 * - Calculated netto variometer
 * - AGL (Above Ground Level) from network service
 */
data class CompleteFlightData(
    // Raw sensor data (nullable - sensors may not be available)
    val gps: GPSData?,
    val baro: BaroData?,
    val compass: CompassData?,

    // Calculated barometric values
    val baroAltitude: Double,   // Barometric altitude in meters (from pressure)
    val qnh: Double,            // QNH pressure setting in hPa (sea level pressure)
    val isQNHCalibrated: Boolean, // Whether QNH was calibrated by GPS (vs standard 1013.25)
    val verticalSpeed: Double,  // m/s (selected brutto vario)
    val displayVario: Double = 0.0,
    val bruttoVario: Double = 0.0, // m/s (TE if available else GPS)
    val bruttoAverage30s: Double = 0.0,
    val nettoAverage30s: Double = 0.0,
    val varioSource: String = "UNKNOWN",
    val varioValid: Boolean = false,
    val pressureAltitude: Double, // meters (QNH 1013.25 reference)
    val baroGpsDelta: Double?,  // meters difference between baro altitude and GPS altitude
    val baroConfidence: ConfidenceLevel, // Confidence supplied by baro calculator
    val qnhCalibrationAgeSeconds: Long,  // Seconds since last calibration (-1 if unknown)

    // AGL (Above Ground Level) - from network service
    val agl: Double,            // meters above ground (GPS altitude - terrain elevation)

    // Calculated wind
    val windSpeed: Float,       // m/s (wind speed magnitude)
    val windDirection: Float,   // 0-360° (direction wind is coming FROM)
    val windHeadwind: Double = 0.0,
    val windCrosswind: Double = 0.0,
    val windQuality: Int = 0,
    val windSource: WindSource = WindSource.NONE,

    // Calculated thermal average
    val thermalAverage: Float,  // m/s (average climb rate in thermal)
    val thermalAverageCircle: Float = 0f,
    val thermalAverageTotal: Float = 0f,
    val thermalGain: Double = 0.0,

    // Calculated L/D ratio
    val currentLD: Float,       // Distance traveled / altitude lost (glide ratio)

    // Calculated netto variometer
    val netto: Float,           // m/s (variometer + sink rate compensation)
    val displayNetto: Double = 0.0,
    val nettoValid: Boolean = false,
    val trueAirspeed: Double = 0.0,    // m/s
    val indicatedAirspeed: Double = 0.0, // m/s
    val airspeedSource: String = "UNKNOWN",

    // NEW: Multiple vario implementations for testing (VARIO_IMPROVEMENTS.md)
    val varioOptimized: Double = 0.0,      // Optimized Kalman (R=0.5m) - Priority 1
    val varioLegacy: Double = 0.0,         // Legacy Kalman (R=2.0m) - Baseline
    val varioRaw: Double = 0.0,            // Raw barometer differentiation
    val varioGPS: Double = 0.0,            // GPS vertical speed
    val varioComplementary: Double = 0.0,  // Complementary filter (future)

    // Metadata
    val timestamp: Long,
    val dataQuality: String     // "GPS+BARO+COMPASS", "GPS_ONLY", etc.
) {
    /**
     * Check if all sensors are available
     */
    val hasAllSensors: Boolean get() = gps != null && baro != null && compass != null

    /**
     * Check if GPS fix is good
     */
    val hasGoodGPS: Boolean get() = gps?.isHighAccuracy == true

    /**
     * Get effective heading (GPS bearing when moving, compass when stationary)
     */
    val effectiveHeading: Double get() =
        if (gps?.isMoving == true) gps.bearing else compass?.heading ?: 0.0
}
