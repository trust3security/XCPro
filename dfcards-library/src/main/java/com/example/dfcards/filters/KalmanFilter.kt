package com.example.dfcards.filters

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Kalman Filter for barometric pressure sensor smoothing
 * Optimized specifically for variometer applications
 */
class BarometricKalmanFilter {
    
    // State vector: [altitude, vertical_velocity]
    private val stateVector = Array(2) { 0.0 }
    private val errorCovariance = Array(2) { Array(2) { 0.0 } }
    
    // Tuning parameters - critical for variometer performance
    private var processNoise = 0.04  // Reduced - more stable model
    private var measurementNoise = 2.5 // Increased - more sensor smoothing
    
    private var isInitialized = false
    private var lastUpdate = 0L
    
    init {
        // Initialize error covariance matrix
        errorCovariance[0][0] = 1.0  // Initial altitude uncertainty
        errorCovariance[1][1] = 1.0  // Initial velocity uncertainty
        errorCovariance[0][1] = 0.0  // No cross-correlation initially
        errorCovariance[1][0] = 0.0
    }
    
    /**
     * Update the filter with new barometric altitude measurement
     * @param measuredAltitude Raw barometric altitude in meters
     * @param deltaTime Time since last measurement in seconds
     * @param confidence Confidence in the measurement (0.0 to 1.0)
     * @return Filtered altitude and vertical speed
     */
    fun update(measuredAltitude: Double, deltaTime: Double, confidence: Double = 1.0): FilteredResult {
        val currentTime = System.currentTimeMillis()
        
        if (!isInitialized) {
            // Initialize with first measurement
            stateVector[0] = measuredAltitude
            stateVector[1] = 0.0
            isInitialized = true
            lastUpdate = currentTime
            return FilteredResult(measuredAltitude, 0.0, 0.5)
        }
        
        // Adaptive noise based on flight conditions
        adaptFilterParameters(stateVector[1], confidence)
        
        // Prediction Step (Time Update)
        val predictedAltitude = stateVector[0] + stateVector[1] * deltaTime
        val predictedVelocity = stateVector[1] // Assume constant velocity model
        
        // Update state transition uncertainty
        val processMatrix = Array(2) { Array(2) { 0.0 } }
        val dt2 = deltaTime * deltaTime
        val dt3 = dt2 * deltaTime
        processMatrix[0][0] = processNoise * dt3 / 3.0
        processMatrix[0][1] = processNoise * dt2 / 2.0
        processMatrix[1][0] = processMatrix[0][1]
        processMatrix[1][1] = processNoise * deltaTime
        
        // Update error covariance (prediction)
        val newErrorCov = Array(2) { Array(2) { 0.0 } }
        newErrorCov[0][0] = errorCovariance[0][0] + errorCovariance[1][1] * deltaTime * deltaTime + 
                           2 * errorCovariance[0][1] * deltaTime + processMatrix[0][0]
        newErrorCov[0][1] = errorCovariance[0][1] + errorCovariance[1][1] * deltaTime + processMatrix[0][1]
        newErrorCov[1][0] = newErrorCov[0][1] // Symmetric
        newErrorCov[1][1] = errorCovariance[1][1] + processMatrix[1][1]
        
        // Measurement Update (Correction Step)
        val innovation = measuredAltitude - predictedAltitude
        val innovationCovariance = newErrorCov[0][0] + measurementNoise * (2.0 - confidence)
        
        // Calculate Kalman gain
        val kalmanGain = Array(2) { 0.0 }
        if (innovationCovariance > 0.001) { // Avoid division by zero
            kalmanGain[0] = newErrorCov[0][0] / innovationCovariance
            kalmanGain[1] = newErrorCov[1][0] / innovationCovariance
        }
        
        // Update state estimate
        stateVector[0] = predictedAltitude + kalmanGain[0] * innovation
        var filteredVelocity = predictedVelocity + kalmanGain[1] * innovation
        
        // ✅ DEADBAND: Zero out small velocities to eliminate phantom readings
        if (abs(filteredVelocity) < 0.05) {  // 0.05 m/s = 10 fpm deadband
            filteredVelocity = 0.0
        }
        
        stateVector[1] = filteredVelocity
        
        // Update error covariance
        errorCovariance[0][0] = newErrorCov[0][0] - kalmanGain[0] * newErrorCov[0][0]
        errorCovariance[0][1] = newErrorCov[0][1] - kalmanGain[0] * newErrorCov[0][1]  
        errorCovariance[1][0] = newErrorCov[1][0] - kalmanGain[1] * newErrorCov[0][0]
        errorCovariance[1][1] = newErrorCov[1][1] - kalmanGain[1] * newErrorCov[1][0]
        
        // Calculate confidence based on innovation and gain
        val confidenceLevel = calculateConfidence(abs(innovation), kalmanGain[0])
        
        lastUpdate = currentTime
        
        return FilteredResult(
            altitude = stateVector[0],
            verticalSpeed = stateVector[1],
            confidence = confidenceLevel
        )
    }
    
    /**
     * Adapt filter parameters based on flight conditions
     */
    private fun adaptFilterParameters(currentVSpeed: Double, sensorConfidence: Double) {
        when {
            // Thermal flying - need more responsiveness
            abs(currentVSpeed) > 1.0 -> {
                processNoise = 0.12
                measurementNoise = 1.0 * (2.0 - sensorConfidence)
            }
            // Cruising flight - more stability  
            abs(currentVSpeed) < 0.5 -> {
                processNoise = 0.05
                measurementNoise = 2.0 * (2.0 - sensorConfidence)
            }
            // Normal conditions
            else -> {
                processNoise = 0.08
                measurementNoise = 1.5 * (2.0 - sensorConfidence)
            }
        }
    }
    
    /**
     * Calculate confidence level based on innovation and Kalman gain
     */
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
    
    /**
     * Reset the filter (useful when GPS recalibrates barometric altitude)
     */
    fun reset() {
        isInitialized = false
        stateVector[0] = 0.0
        stateVector[1] = 0.0
        errorCovariance[0][0] = 1.0
        errorCovariance[1][1] = 1.0
        errorCovariance[0][1] = 0.0
        errorCovariance[1][0] = 0.0
    }
    
    /**
     * Get current filtered state without updating
     */
    fun getCurrentState(): FilteredResult {
        return FilteredResult(stateVector[0], stateVector[1], 0.8)
    }
}

/**
 * Additional smoothing filter for display purposes  
 */
class ExponentialSmoothingFilter(private val alpha: Double = 0.11) {
    private var previousValue: Double = 0.0
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

/**
 * Outlier detection and removal
 */
class OutlierFilter(private val windowSize: Int = 5) {
    private val buffer = mutableListOf<Double>()
    private var runningMean = 0.0
    private var runningStdDev = 0.0
    
    fun filter(newValue: Double): Double {
        buffer.add(newValue)
        
        if (buffer.size > windowSize) {
            buffer.removeAt(0)
        }
        
        if (buffer.size < 3) {
            return newValue
        }
        
        // Calculate running statistics
        runningMean = buffer.average()
        val variance = buffer.map { (it - runningMean) * (it - runningMean) }.average()
        runningStdDev = sqrt(variance)
        
        // Check if new value is an outlier (more than 2.5 standard deviations)
        val zScore = abs(newValue - runningMean) / (runningStdDev + 0.001) // Avoid division by zero
        
        return if (zScore > 2.5) {
            // Return median of recent values instead of outlier
            buffer.sorted()[buffer.size / 2]
        } else {
            newValue
        }
    }
}

/**
 * Combined multi-stage filtering system
 */
class AdvancedBarometricFilter {
    private val outlierFilter = OutlierFilter()
    private val kalmanFilter = BarometricKalmanFilter()
    private val displayFilter = ExponentialSmoothingFilter(0.08)
    
    private var lastUpdateTime = 0L
    
    fun processReading(
        rawBaroAltitude: Double, 
        gpsAltitude: Double? = null,
        gpsAccuracy: Double? = null
    ): FilteredBarometricData {
        
        val currentTime = System.currentTimeMillis()
        val deltaTime = if (lastUpdateTime > 0) {
            (currentTime - lastUpdateTime) / 1000.0
        } else {
            0.1 // Default 100ms for first reading
        }
        
        // Stage 1: Remove outliers
        val cleanAltitude = outlierFilter.filter(rawBaroAltitude)
        
        // Stage 2: Calculate sensor confidence
        val sensorConfidence = calculateSensorConfidence(gpsAltitude, gpsAccuracy, cleanAltitude)
        
        // Stage 3: Kalman filtering
        val kalmanResult = kalmanFilter.update(cleanAltitude, deltaTime, sensorConfidence)
        
        // Stage 4: Final smoothing for display (optional)
        val displayAltitude = displayFilter.filter(kalmanResult.altitude)
        
        lastUpdateTime = currentTime
        
        return FilteredBarometricData(
            rawAltitude = rawBaroAltitude,
            filteredAltitude = kalmanResult.altitude,
            displayAltitude = displayAltitude,
            verticalSpeed = kalmanResult.verticalSpeed,
            confidence = kalmanResult.confidence,
            processingStages = "Outlier->Kalman->Display"
        )
    }
    
    private fun calculateSensorConfidence(
        gpsAltitude: Double?, 
        gpsAccuracy: Double?, 
        baroAltitude: Double
    ): Double {
        return if (gpsAltitude != null && gpsAccuracy != null && gpsAccuracy < 10.0) {
            // Compare GPS and barometric altitude
            val altitudeDiff = abs(gpsAltitude - baroAltitude)
            when {
                altitudeDiff < 5.0 -> 1.0  // Excellent agreement
                altitudeDiff < 15.0 -> 0.8 // Good agreement
                altitudeDiff < 30.0 -> 0.6 // Fair agreement  
                else -> 0.4 // Poor agreement
            }
        } else {
            0.7 // Default confidence without GPS reference
        }
    }
}

// Data classes for results
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
