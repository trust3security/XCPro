package com.example.xcpro.vario

/**
 * Interface for all variometer implementations
 *
 * Purpose: Allow side-by-side testing of different vario algorithms
 *
 * All implementations MUST:
 * - Be stateful (maintain internal filter state)
 * - Handle initialization gracefully
 * - Return stable results (no NaN/Infinity)
 * - Process at same rate (10-50 Hz typical)
 *
 * Reference: VARIO_IMPROVEMENTS.md - Testing & Validation Strategy
 */
interface IVarioCalculator {

    /**
     * Unique identifier for this vario implementation
     */
    val name: String

    /**
     * Short description of algorithm/approach
     */
    val description: String

    /**
     * Update vario with new sensor data
     *
     * @param baroAltitude Barometric altitude (m)
     * @param verticalAccel Vertical acceleration from IMU (m/s²) - optional
     * @param deltaTime Time since last update (s)
     * @param gpsSpeed GPS horizontal speed (m/s) - for motion detection
     * @param gpsAltitude GPS altitude (m) - optional, for GPS-based varios
     * @return Vertical speed (m/s)
     */
    fun update(
        baroAltitude: Double,
        verticalAccel: Double = 0.0,
        deltaTime: Double,
        gpsSpeed: Double = 0.0,
        gpsAltitude: Double = 0.0
    ): Double

    /**
     * Reset filter state (e.g., after GPS recalibration)
     */
    fun reset()

    /**
     * Get current vertical speed without updating
     * @return Last calculated vertical speed (m/s)
     */
    fun getVerticalSpeed(): Double

    /**
     * Get implementation-specific diagnostic info
     * @return Human-readable diagnostic string
     */
    fun getDiagnostics(): String {
        return "$name: ${String.format("%.2f", getVerticalSpeed())} m/s"
    }
}

/**
 * Result from vario calculation with metadata
 */
data class VarioResult(
    val verticalSpeed: Double,  // m/s
    val confidence: Double,     // 0-1
    val source: String          // "BARO", "GPS", "BARO+IMU", etc.
)
