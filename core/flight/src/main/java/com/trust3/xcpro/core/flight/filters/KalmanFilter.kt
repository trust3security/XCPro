package com.trust3.xcpro.core.flight.filters

import kotlin.math.abs
import kotlin.math.sqrt

class BarometricKalmanFilter {
    private val stateVector = Array(2) { 0.0 }
    private val errorCovariance = Array(2) { Array(2) { 0.0 } }
    private var processNoise = 0.04
    private var measurementNoise = 2.5
    private var isInitialized = false

    init {
        errorCovariance[0][0] = 1.0
        errorCovariance[1][1] = 1.0
    }

    fun update(measuredAltitude: Double, deltaTime: Double, confidence: Double = 1.0): FilteredResult {
        if (!isInitialized) {
            stateVector[0] = measuredAltitude
            isInitialized = true
            return FilteredResult(measuredAltitude, 0.0, 0.5)
        }

        adaptFilterParameters(stateVector[1], confidence)
        val predictedAltitude = stateVector[0] + stateVector[1] * deltaTime
        val predictedVelocity = stateVector[1]
        val processMatrix = Array(2) { Array(2) { 0.0 } }
        val dt2 = deltaTime * deltaTime
        val dt3 = dt2 * deltaTime
        processMatrix[0][0] = processNoise * dt3 / 3.0
        processMatrix[0][1] = processNoise * dt2 / 2.0
        processMatrix[1][0] = processMatrix[0][1]
        processMatrix[1][1] = processNoise * deltaTime
        val newErrorCov = Array(2) { Array(2) { 0.0 } }
        newErrorCov[0][0] = errorCovariance[0][0] + errorCovariance[1][1] * deltaTime * deltaTime +
            2 * errorCovariance[0][1] * deltaTime + processMatrix[0][0]
        newErrorCov[0][1] = errorCovariance[0][1] + errorCovariance[1][1] * deltaTime + processMatrix[0][1]
        newErrorCov[1][0] = newErrorCov[0][1]
        newErrorCov[1][1] = errorCovariance[1][1] + processMatrix[1][1]
        val innovation = measuredAltitude - predictedAltitude
        val innovationCovariance = newErrorCov[0][0] + measurementNoise * (2.0 - confidence)
        val kalmanGain = Array(2) { 0.0 }
        if (innovationCovariance > 0.001) {
            kalmanGain[0] = newErrorCov[0][0] / innovationCovariance
            kalmanGain[1] = newErrorCov[1][0] / innovationCovariance
        }
        stateVector[0] = predictedAltitude + kalmanGain[0] * innovation
        stateVector[1] = predictedVelocity + kalmanGain[1] * innovation
        errorCovariance[0][0] = newErrorCov[0][0] - kalmanGain[0] * newErrorCov[0][0]
        errorCovariance[0][1] = newErrorCov[0][1] - kalmanGain[0] * newErrorCov[0][1]
        errorCovariance[1][0] = newErrorCov[1][0] - kalmanGain[1] * newErrorCov[0][0]
        errorCovariance[1][1] = newErrorCov[1][1] - kalmanGain[1] * newErrorCov[1][0]
        return FilteredResult(stateVector[0], stateVector[1], calculateConfidence(abs(innovation), kalmanGain[0]))
    }

    private fun adaptFilterParameters(currentVSpeed: Double, sensorConfidence: Double) {
        when {
            abs(currentVSpeed) > 1.0 -> {
                processNoise = 0.12
                measurementNoise = 1.0 * (2.0 - sensorConfidence)
            }
            abs(currentVSpeed) < 0.5 -> {
                processNoise = 0.05
                measurementNoise = 2.0 * (2.0 - sensorConfidence)
            }
            else -> {
                processNoise = 0.08
                measurementNoise = 1.5 * (2.0 - sensorConfidence)
            }
        }
    }

    private fun calculateConfidence(innovation: Double, gain: Double): Double {
        val innovationConfidence = when {
            innovation < 0.5 -> 1.0
            innovation < 2.0 -> 1.0 - (innovation - 0.5) / 1.5 * 0.5
            else -> 0.5
        }
        val gainConfidence = when {
            gain < 0.3 -> 1.0
            gain < 0.7 -> 1.0 - (gain - 0.3) / 0.4 * 0.3
            else -> 0.7
        }
        return (innovationConfidence * gainConfidence).coerceIn(0.1, 1.0)
    }

    fun reset() {
        isInitialized = false
        stateVector[0] = 0.0
        stateVector[1] = 0.0
        errorCovariance[0][0] = 1.0
        errorCovariance[1][1] = 1.0
        errorCovariance[0][1] = 0.0
        errorCovariance[1][0] = 0.0
    }

    fun getCurrentState(): FilteredResult = FilteredResult(stateVector[0], stateVector[1], 0.8)
}

class ExponentialSmoothingFilter(private val alpha: Double = 0.11) {
    private var previousValue = 0.0
    private var isInitialized = false

    fun filter(newValue: Double): Double {
        if (!isInitialized) {
            previousValue = newValue
            isInitialized = true
            return newValue
        }
        previousValue = alpha * newValue + (1 - alpha) * previousValue
        return previousValue
    }

    fun reset() {
        isInitialized = false
        previousValue = 0.0
    }
}

class OutlierFilter(private val windowSize: Int = 5) {
    private val buffer = mutableListOf<Double>()
    private var runningMean = 0.0
    private var runningStdDev = 0.0

    fun filter(newValue: Double): Double {
        buffer.add(newValue)
        if (buffer.size > windowSize) {
            buffer.removeAt(0)
        }
        if (buffer.size < 3) return newValue
        runningMean = buffer.average()
        val variance = buffer.map { (it - runningMean) * (it - runningMean) }.average()
        runningStdDev = sqrt(variance)
        val zScore = abs(newValue - runningMean) / (runningStdDev + 0.001)
        return if (zScore > 2.5) buffer.sorted()[buffer.size / 2] else newValue
    }

    fun reset() {
        buffer.clear()
        runningMean = 0.0
        runningStdDev = 0.0
    }
}

class AdvancedBarometricFilter {
    private val outlierFilter = OutlierFilter()
    private val kalmanFilter = BarometricKalmanFilter()
    private val displayFilter = ExponentialSmoothingFilter(0.08)
    private var lastUpdateTime = 0L

    fun processReading(
        rawBaroAltitude: Double,
        gpsAltitude: Double? = null,
        gpsAccuracy: Double? = null,
        timestampMillis: Long
    ): FilteredBarometricData {
        val resolvedTime = if (timestampMillis > 0L) timestampMillis else lastUpdateTime
        val deltaTime = if (lastUpdateTime > 0L) {
            val deltaMs = resolvedTime - lastUpdateTime
            if (deltaMs <= 0L) 0.1 else deltaMs / 1000.0
        } else {
            0.1
        }
        val cleanAltitude = outlierFilter.filter(rawBaroAltitude)
        val sensorConfidence = calculateSensorConfidence(gpsAltitude, gpsAccuracy, cleanAltitude)
        val kalmanResult = kalmanFilter.update(cleanAltitude, deltaTime, sensorConfidence)
        val displayAltitude = displayFilter.filter(kalmanResult.altitude)
        lastUpdateTime = resolvedTime
        return FilteredBarometricData(
            rawAltitude = rawBaroAltitude,
            filteredAltitude = kalmanResult.altitude,
            displayAltitude = displayAltitude,
            verticalSpeed = kalmanResult.verticalSpeed,
            confidence = kalmanResult.confidence,
            processingStages = "Outlier->Kalman->Display"
        )
    }

    private fun calculateSensorConfidence(gpsAltitude: Double?, gpsAccuracy: Double?, baroAltitude: Double): Double =
        if (gpsAltitude != null && gpsAccuracy != null && gpsAccuracy < 10.0) {
            val altitudeDiff = abs(gpsAltitude - baroAltitude)
            when {
                altitudeDiff < 5.0 -> 1.0
                altitudeDiff < 15.0 -> 0.8
                altitudeDiff < 30.0 -> 0.6
                else -> 0.4
            }
        } else {
            0.7
        }

    fun reset() {
        outlierFilter.reset()
        kalmanFilter.reset()
        displayFilter.reset()
        lastUpdateTime = 0L
    }
}

data class FilteredResult(
    val altitude: Double,
    val verticalSpeed: Double,
    val confidence: Double
)

data class FilteredBarometricData(
    val rawAltitude: Double,
    val filteredAltitude: Double,
    val displayAltitude: Double,
    val verticalSpeed: Double,
    val confidence: Double,
    val processingStages: String
)
