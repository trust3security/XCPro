package com.example.xcpro.core.flight.filters

import kotlin.math.sqrt

data class VarioFilterDiagnostics(
    val innovationBaro: Double,
    val innovationAccel: Double,
    val kalmanGainBaro: Double,
    val kalmanGainAccel: Double,
    val filteredAltitude: Double,
    val filteredVerticalSpeed: Double,
    val filteredAcceleration: Double,
    val confidence: Double,
    val responseTime: Long,
    val baroSampleRate: Double,
    val imuSampleRate: Double,
    val filterMode: String,
    val isConverged: Boolean,
    val estimatedBaroNoise: Double,
    val estimatedAccelNoise: Double,
    val adaptiveSigmaBaro: Double,
    val adaptiveMeasurementNoise: Double,
    val adaptiveProcessNoise: Double,
    val adaptiveTauSeconds: Double,
    val baroHealthScore: Double,
    val imuHealthScore: Double,
    val gpsHealthScore: Double,
    val timestamp: Long
)

class VarioFilterDiagnosticsCollector {
    private val baroUpdateTimes = mutableListOf<Long>()
    private val imuUpdateTimes = mutableListOf<Long>()
    private val baroInnovations = mutableListOf<Double>()
    private val accelInnovations = mutableListOf<Double>()
    private var lastBaroInnovation = 0.0
    private var lastAccelInnovation = 0.0
    private var lastKalmanGainBaro = 0.0
    private var lastKalmanGainAccel = 0.0
    private var lastAdaptiveSigma = 0.0
    private var lastAdaptiveMeasurementNoise = 0.0
    private var lastAdaptiveProcessNoise = 0.0
    private var lastAdaptiveTau = 0.0
    private val maxHistory = 100

    fun recordBaroUpdate(innovation: Double, kalmanGain: Double, timestampMillis: Long) {
        baroUpdateTimes.add(timestampMillis)
        baroInnovations.add(innovation)
        lastBaroInnovation = innovation
        lastKalmanGainBaro = kalmanGain
        if (baroUpdateTimes.size > maxHistory) {
            baroUpdateTimes.removeAt(0)
            baroInnovations.removeAt(0)
        }
    }

    fun recordIMUUpdate(innovation: Double, kalmanGain: Double, timestampMillis: Long) {
        imuUpdateTimes.add(timestampMillis)
        accelInnovations.add(innovation)
        lastAccelInnovation = innovation
        lastKalmanGainAccel = kalmanGain
        if (imuUpdateTimes.size > maxHistory) {
            imuUpdateTimes.removeAt(0)
            accelInnovations.removeAt(0)
        }
    }

    fun recordAdaptiveStats(
        sigmaBaro: Double,
        measurementNoise: Double,
        processNoise: Double,
        tauSeconds: Double
    ) {
        lastAdaptiveSigma = sigmaBaro
        lastAdaptiveMeasurementNoise = measurementNoise
        lastAdaptiveProcessNoise = processNoise
        lastAdaptiveTau = tauSeconds
    }

    private fun calculateSampleRate(updateTimes: List<Long>): Double {
        if (updateTimes.size < 2) return 0.0
        val recentUpdates = updateTimes.takeLast(10)
        if (recentUpdates.size < 2) return 0.0
        val deltaMs = recentUpdates.last() - recentUpdates.first()
        val numUpdates = recentUpdates.size - 1
        return if (deltaMs > 0) (numUpdates * 1000.0) / deltaMs else 0.0
    }

    private fun estimateNoise(innovations: List<Double>): Double {
        if (innovations.size < 10) return 0.0
        val recentInnovations = innovations.takeLast(50)
        if (recentInnovations.isEmpty()) return 0.0
        val mean = recentInnovations.average()
        val variance = recentInnovations.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun calculateHealthScore(
        innovations: List<Double>,
        expectedNoise: Double
    ): Double {
        if (innovations.size < 10) return 0.5
        val actualNoise = estimateNoise(innovations)
        if (expectedNoise <= 0.0) return 0.5
        val noiseRatio = actualNoise / expectedNoise
        return when {
            noiseRatio < 1.5 -> 1.0
            noiseRatio < 3.0 -> 0.8
            noiseRatio < 5.0 -> 0.5
            noiseRatio < 10.0 -> 0.3
            else -> 0.1
        }.coerceIn(0.0, 1.0)
    }

    fun generateDiagnostics(
        filteredAltitude: Double,
        filteredVerticalSpeed: Double,
        filteredAcceleration: Double,
        confidence: Double,
        filterMode: String,
        gpsAccuracy: Double,
        gpsSatelliteCount: Int,
        timestampMillis: Long
    ): VarioFilterDiagnostics {
        val baroSampleRate = calculateSampleRate(baroUpdateTimes)
        val imuSampleRate = calculateSampleRate(imuUpdateTimes)
        val estimatedBaroNoise = estimateNoise(baroInnovations)
        val estimatedAccelNoise = estimateNoise(accelInnovations)
        val baroHealthScore = calculateHealthScore(baroInnovations, 0.5)
        val imuHealthScore = calculateHealthScore(accelInnovations, 0.3)
        val gpsHealthScore = when {
            gpsSatelliteCount >= 8 && gpsAccuracy < 5.0 -> 1.0
            gpsSatelliteCount >= 6 && gpsAccuracy < 10.0 -> 0.8
            gpsSatelliteCount >= 5 && gpsAccuracy < 15.0 -> 0.6
            gpsSatelliteCount >= 4 -> 0.4
            else -> 0.2
        }
        val isConverged = estimatedBaroNoise < 1.0 &&
            estimatedAccelNoise < 0.5 &&
            baroInnovations.size >= 20
        val responseTime = if (baroUpdateTimes.isNotEmpty()) {
            (timestampMillis - baroUpdateTimes.last()).coerceAtLeast(0L)
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
            adaptiveSigmaBaro = lastAdaptiveSigma,
            adaptiveMeasurementNoise = lastAdaptiveMeasurementNoise,
            adaptiveProcessNoise = lastAdaptiveProcessNoise,
            adaptiveTauSeconds = lastAdaptiveTau,
            baroHealthScore = baroHealthScore,
            imuHealthScore = imuHealthScore,
            gpsHealthScore = gpsHealthScore,
            timestamp = timestampMillis
        )
    }

    fun getStats(): String = buildString {
        append("Baro: ${calculateSampleRate(baroUpdateTimes).toInt()}Hz, ")
        append("IMU: ${calculateSampleRate(imuUpdateTimes).toInt()}Hz, ")
        append("Baro noise: ${String.format("%.3f", estimateNoise(baroInnovations))}m, ")
        append("IMU noise: ${String.format("%.3f", estimateNoise(accelInnovations))}m/s")
    }
}
