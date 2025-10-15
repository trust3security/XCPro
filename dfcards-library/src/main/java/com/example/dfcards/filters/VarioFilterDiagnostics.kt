package com.example.dfcards.filters

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Real-time vario filter diagnostics
 *
 * Purpose:
 * - Monitor filter performance in real flights
 * - Detect sensor failures (drift, noise spikes)
 * - Optimize filter parameters based on flight data
 * - Debug thermal detection issues
 *
 * Priority 7: VARIO_IMPROVEMENTS.md
 */
data class VarioFilterDiagnostics(
    // Kalman filter internals
    val innovationBaro: Double,        // m - How much baro differs from prediction
    val innovationAccel: Double,       // m/s² - How much accel differs from prediction
    val kalmanGainBaro: Double,        // 0-1 - How much filter trusts barometer
    val kalmanGainAccel: Double,       // 0-1 - How much filter trusts accelerometer

    // Filter outputs
    val filteredAltitude: Double,      // m
    val filteredVerticalSpeed: Double, // m/s
    val filteredAcceleration: Double,  // m/s²
    val confidence: Double,            // 0-1 - Overall confidence

    // Performance metrics
    val responseTime: Long,            // ms - Time since last update
    val baroSampleRate: Double,        // Hz - Actual barometer sample rate
    val imuSampleRate: Double,         // Hz - Actual IMU sample rate

    // Filter state
    val filterMode: String,            // "KALMAN", "COMPLEMENTARY", or "AUTO"
    val isConverged: Boolean,          // Has filter converged to stable state?

    // Noise estimates (runtime)
    val estimatedBaroNoise: Double,    // m - Estimated from innovation variance
    val estimatedAccelNoise: Double,   // m/s² - Estimated from innovation variance

    // Sensor health
    val baroHealthScore: Double,       // 0-1 - Is barometer behaving normally?
    val imuHealthScore: Double,        // 0-1 - Is IMU behaving normally?
    val gpsHealthScore: Double,        // 0-1 - Is GPS providing good data?

    // Timestamp
    val timestamp: Long                // ms
)

/**
 * Vario filter diagnostics collector
 */
class VarioFilterDiagnosticsCollector {

    private val baroUpdateTimes = mutableListOf<Long>()
    private val imuUpdateTimes = mutableListOf<Long>()
    private val baroInnovations = mutableListOf<Double>()
    private val accelInnovations = mutableListOf<Double>()

    // Store last values for diagnostics generation
    private var lastBaroInnovation = 0.0
    private var lastAccelInnovation = 0.0
    private var lastKalmanGainBaro = 0.0
    private var lastKalmanGainAccel = 0.0

    private val MAX_HISTORY = 100

    /**
     * Record barometer update
     */
    fun recordBaroUpdate(innovation: Double, kalmanGain: Double) {
        baroUpdateTimes.add(System.currentTimeMillis())
        baroInnovations.add(innovation)
        lastBaroInnovation = innovation
        lastKalmanGainBaro = kalmanGain

        if (baroUpdateTimes.size > MAX_HISTORY) {
            baroUpdateTimes.removeAt(0)
            baroInnovations.removeAt(0)
        }
    }

    /**
     * Record IMU update
     */
    fun recordIMUUpdate(innovation: Double, kalmanGain: Double) {
        imuUpdateTimes.add(System.currentTimeMillis())
        accelInnovations.add(innovation)
        lastAccelInnovation = innovation
        lastKalmanGainAccel = kalmanGain

        if (imuUpdateTimes.size > MAX_HISTORY) {
            imuUpdateTimes.removeAt(0)
            accelInnovations.removeAt(0)
        }
    }

    /**
     * Calculate actual sample rates
     */
    private fun calculateSampleRate(updateTimes: List<Long>): Double {
        if (updateTimes.size < 2) return 0.0

        val recentUpdates = updateTimes.takeLast(10)
        if (recentUpdates.size < 2) return 0.0

        val deltaMs = recentUpdates.last() - recentUpdates.first()
        val numUpdates = recentUpdates.size - 1

        return if (deltaMs > 0) {
            (numUpdates * 1000.0) / deltaMs  // Hz
        } else {
            0.0
        }
    }

    /**
     * Estimate noise from innovation variance
     */
    private fun estimateNoise(innovations: List<Double>): Double {
        if (innovations.size < 10) return 0.0

        val recentInnovations = innovations.takeLast(50)
        if (recentInnovations.isEmpty()) return 0.0

        val mean = recentInnovations.average()
        val variance = recentInnovations.map { (it - mean) * (it - mean) }.average()

        return sqrt(variance)
    }

    /**
     * Calculate sensor health score
     */
    private fun calculateHealthScore(
        innovations: List<Double>,
        expectedNoise: Double
    ): Double {
        if (innovations.size < 10) return 0.5  // Unknown

        val actualNoise = estimateNoise(innovations)
        if (expectedNoise <= 0.0) return 0.5

        val noiseRatio = actualNoise / expectedNoise

        // Health score based on noise ratio
        return when {
            noiseRatio < 1.5 -> 1.0      // Excellent (noise as expected)
            noiseRatio < 3.0 -> 0.8      // Good (slightly noisy)
            noiseRatio < 5.0 -> 0.5      // Fair (noisy)
            noiseRatio < 10.0 -> 0.3     // Poor (very noisy)
            else -> 0.1                  // Critical (sensor failure?)
        }.coerceIn(0.0, 1.0)
    }

    /**
     * Generate diagnostics report
     */
    fun generateDiagnostics(
        filteredAltitude: Double,
        filteredVerticalSpeed: Double,
        filteredAcceleration: Double,
        confidence: Double,
        filterMode: String,
        gpsAccuracy: Double,
        gpsSatelliteCount: Int
    ): VarioFilterDiagnostics {

        val baroSampleRate = calculateSampleRate(baroUpdateTimes)
        val imuSampleRate = calculateSampleRate(imuUpdateTimes)

        val estimatedBaroNoise = estimateNoise(baroInnovations)
        val estimatedAccelNoise = estimateNoise(accelInnovations)

        val baroHealthScore = calculateHealthScore(baroInnovations, 0.5)  // Expected 0.5m noise
        val imuHealthScore = calculateHealthScore(accelInnovations, 0.3)  // Expected 0.3 m/s² noise
        val gpsHealthScore = when {
            gpsSatelliteCount >= 8 && gpsAccuracy < 5.0 -> 1.0
            gpsSatelliteCount >= 6 && gpsAccuracy < 10.0 -> 0.8
            gpsSatelliteCount >= 5 && gpsAccuracy < 15.0 -> 0.6
            gpsSatelliteCount >= 4 -> 0.4
            else -> 0.2
        }

        // Filter is converged if innovations are small and stable
        val isConverged = estimatedBaroNoise < 1.0 &&
                         estimatedAccelNoise < 0.5 &&
                         baroInnovations.size >= 20

        val currentTime = System.currentTimeMillis()
        val responseTime = if (baroUpdateTimes.isNotEmpty()) {
            currentTime - baroUpdateTimes.last()
        } else {
            999L
        }

        return VarioFilterDiagnostics(
            innovationBaro = lastBaroInnovation,
            innovationAccel = lastAccelInnovation,
            kalmanGainBaro = lastKalmanGainBaro,
            kalmanGainAccel = lastKalmanGainAccel,
            filteredAltitude = filteredAltitude,
            filteredVerticalSpeed = filteredVerticalSpeed,
            filteredAcceleration = filteredAcceleration,
            confidence = confidence,
            responseTime = responseTime,
            baroSampleRate = baroSampleRate,
            imuSampleRate = imuSampleRate,
            filterMode = filterMode,
            isConverged = isConverged,
            estimatedBaroNoise = estimatedBaroNoise,
            estimatedAccelNoise = estimatedAccelNoise,
            baroHealthScore = baroHealthScore,
            imuHealthScore = imuHealthScore,
            gpsHealthScore = gpsHealthScore,
            timestamp = currentTime
        )
    }

    /**
     * Get basic stats for logging
     */
    fun getStats(): String {
        return buildString {
            append("Baro: ${calculateSampleRate(baroUpdateTimes).toInt()}Hz, ")
            append("IMU: ${calculateSampleRate(imuUpdateTimes).toInt()}Hz, ")
            append("Baro noise: ${String.format("%.3f", estimateNoise(baroInnovations))}m, ")
            append("IMU noise: ${String.format("%.3f", estimateNoise(accelInnovations))}m/s²")
        }
    }
}
