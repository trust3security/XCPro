package com.trust3.xcpro.core.flight.filters

import com.trust3.xcpro.core.common.logging.AppLogger
import kotlin.math.abs
import kotlin.math.sqrt

class Modern3StateKalmanFilter(
    private val config: AdaptiveVarioConfig = AdaptiveVarioConfig()
) {
    private val state = Array(3) { 0.0 }
    private val p = Array(3) { Array(3) { 0.0 } }
    private var q = Array(3) { Array(3) { 0.0 } }
    private var rAltitude = 0.5
    private var rAccel = 0.3
    private val baroVarianceTracker = AdaptiveVarianceTracker(config.baroVarianceWindowSize)
    private val accelHighPassFilter = HighPassFilter(config.accelHighPassTauSeconds)
    private var lastBaroAltitudeForVariance: Double? = null
    private var smoothedVelocity = 0.0
    private var hasSmoothedVelocity = false
    private var isInitialized = false
    private var consecutiveAltitudeClamps = 0
    private var elapsedTimeMs = 0L
    val diagnosticsCollector = VarioFilterDiagnosticsCollector()

    init {
        p[0][0] = 10.0
        p[1][1] = 5.0
        p[2][2] = 2.0
        updateProcessNoise()
    }

    fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double = 0.0
    ): ModernVarioResult {
        if (!isInitialized) {
            state[0] = baroAltitude
            state[1] = 0.0
            state[2] = verticalAccel
            isInitialized = true
            return ModernVarioResult(baroAltitude, 0.0, verticalAccel, 0.5)
        }

        val deltaMs = (deltaTime * 1000.0).toLong()
        if (deltaMs > 0L) {
            elapsedTimeMs += deltaMs
        }
        val dtSeconds = deltaTime.coerceAtLeast(1e-3)

        lastBaroAltitudeForVariance?.let { previousAltitude ->
            val baroVerticalSpeed = (baroAltitude - previousAltitude) / dtSeconds
            baroVarianceTracker.add(baroVerticalSpeed)
        }
        lastBaroAltitudeForVariance = baroAltitude

        val sigma2Baro = baroVarianceTracker.variance()
        rAltitude = (config.baseBaroMeasurementNoise * (1.0 + config.baroVarianceScale * sigma2Baro))
            .coerceIn(config.measurementNoiseMin, config.measurementNoiseMax)

        val accelHighPass = accelHighPassFilter.update(verticalAccel, dtSeconds)
        val processNoise = if (abs(accelHighPass) > config.accelBoostThreshold) {
            config.baseProcessNoise + config.processNoiseBoost
        } else {
            config.baseProcessNoise
        }
        q[2][2] = processNoise

        val tauEff = (config.tauBaseSeconds / (1.0 + sqrt(sigma2Baro)))
            .coerceIn(config.tauMinSeconds, config.tauMaxSeconds)
        val dt = deltaTime
        val dt2 = dt * dt
        val predictedAltitude = state[0] + state[1] * dt + 0.5 * state[2] * dt2
        val predictedVelocity = state[1] + state[2] * dt
        val predictedAccel = state[2]

        q[0][0] = 0.25 * dt2 * dt2 * q[2][2]
        q[0][1] = 0.5 * dt * dt2 * q[2][2]
        q[0][2] = 0.5 * dt2 * q[2][2]
        q[1][0] = q[0][1]
        q[1][1] = dt2 * q[2][2]
        q[1][2] = dt * q[2][2]
        q[2][0] = q[0][2]
        q[2][1] = q[1][2]

        val f = arrayOf(
            arrayOf(1.0, dt, 0.5 * dt2),
            arrayOf(0.0, 1.0, dt),
            arrayOf(0.0, 0.0, 1.0)
        )
        val pPredicted = matrixMultiply(matrixMultiply(f, p), transpose(f))
        addMatrix(pPredicted, q)

        var y1 = baroAltitude - predictedAltitude
        val y2 = verticalAccel - predictedAccel
        val maxBaroInnovation = 5.0
        if (abs(y1) > maxBaroInnovation) {
            AppLogger.w("KalmanFilter", " BARO SPIKE DETECTED: Innovation=${String.format("%.2f", y1)}m - LIMITED to ${maxBaroInnovation}m")
            y1 = if (y1 > 0) maxBaroInnovation else -maxBaroInnovation
            consecutiveAltitudeClamps++
            if (consecutiveAltitudeClamps >= 3) {
                AppLogger.w("KalmanFilter", " Reinitializing Kalman filter after repeated baro spikes")
                reset()
                state[0] = baroAltitude
                state[1] = 0.0
                state[2] = verticalAccel
                consecutiveAltitudeClamps = 0
                return ModernVarioResult(baroAltitude, 0.0, verticalAccel, 0.2)
            }
        } else {
            consecutiveAltitudeClamps = 0
        }

        val s11 = pPredicted[0][0] + rAltitude
        val s22 = pPredicted[2][2] + rAccel
        val k1 = Array(3) { 0.0 }
        val k2 = Array(3) { 0.0 }

        if (s11 > 0.001) {
            k1[0] = pPredicted[0][0] / s11
            k1[1] = pPredicted[1][0] / s11
            k1[2] = pPredicted[2][0] / s11
        }
        if (s22 > 0.001) {
            k2[0] = pPredicted[0][2] / s22
            k2[1] = pPredicted[1][2] / s22
            k2[2] = pPredicted[2][2] / s22
        }

        state[0] = predictedAltitude + k1[0] * y1 + k2[0] * y2
        state[1] = predictedVelocity + k1[1] * y1 + k2[1] * y2
        state[2] = predictedAccel + k1[2] * y1 + k2[2] * y2
        if (abs(state[1]) < 0.02) {
            state[1] = 0.0
        }

        for (i in 0..2) {
            for (j in 0..2) {
                p[i][j] = pPredicted[i][j] - (k1[i] * pPredicted[0][j] + k2[i] * pPredicted[2][j])
            }
        }

        val confidence = calculateConfidence(abs(y1), abs(y2))
        val smoothingAlpha = (dtSeconds / (tauEff + dtSeconds)).coerceIn(0.0, 1.0)
        if (!hasSmoothedVelocity) {
            smoothedVelocity = state[1]
            hasSmoothedVelocity = true
        } else {
            smoothedVelocity += smoothingAlpha * (state[1] - smoothedVelocity)
        }

        diagnosticsCollector.recordBaroUpdate(y1, k1[0], elapsedTimeMs)
        diagnosticsCollector.recordIMUUpdate(y2, k2[2], elapsedTimeMs)
        diagnosticsCollector.recordAdaptiveStats(sigma2Baro, rAltitude, processNoise, tauEff)
        return ModernVarioResult(state[0], smoothedVelocity, state[2], confidence)
    }

    private fun calculateConfidence(altInnovation: Double, accelInnovation: Double): Double {
        val altConfidence = when {
            altInnovation < 1.0 -> 1.0
            altInnovation < 5.0 -> 1.0 - (altInnovation - 1.0) / 4.0 * 0.3
            else -> 0.7
        }
        val accelConfidence = when {
            accelInnovation < 0.5 -> 1.0
            accelInnovation < 2.0 -> 1.0 - (accelInnovation - 0.5) / 1.5 * 0.3
            else -> 0.7
        }
        return ((altConfidence + accelConfidence) / 2.0).coerceIn(0.1, 1.0)
    }

    fun getDiagnostics(
        gpsAccuracy: Double,
        gpsSatelliteCount: Int,
        filterMode: String = "KALMAN"
    ): VarioFilterDiagnostics = diagnosticsCollector.generateDiagnostics(
        filteredAltitude = state[0],
        filteredVerticalSpeed = state[1],
        filteredAcceleration = state[2],
        confidence = 0.8,
        filterMode = filterMode,
        gpsAccuracy = gpsAccuracy,
        gpsSatelliteCount = gpsSatelliteCount,
        timestampMillis = elapsedTimeMs
    )

    fun getDiagnosticStats(): String = diagnosticsCollector.getStats()

    fun reset() {
        isInitialized = false
        state[0] = 0.0
        state[1] = 0.0
        state[2] = 0.0
        p[0][0] = 10.0
        p[1][1] = 5.0
        p[2][2] = 2.0
        consecutiveAltitudeClamps = 0
        elapsedTimeMs = 0L
    }

    private fun matrixMultiply(a: Array<Array<Double>>, b: Array<Array<Double>>): Array<Array<Double>> {
        val result = Array(a.size) { Array(b[0].size) { 0.0 } }
        for (i in a.indices) {
            for (j in b[0].indices) {
                for (k in a[0].indices) {
                    result[i][j] += a[i][k] * b[k][j]
                }
            }
        }
        return result
    }

    private fun transpose(matrix: Array<Array<Double>>): Array<Array<Double>> {
        val result = Array(matrix[0].size) { Array(matrix.size) { 0.0 } }
        for (i in matrix.indices) {
            for (j in matrix[0].indices) {
                result[j][i] = matrix[i][j]
            }
        }
        return result
    }

    private fun addMatrix(a: Array<Array<Double>>, b: Array<Array<Double>>) {
        for (i in a.indices) {
            for (j in a[0].indices) {
                a[i][j] += b[i][j]
            }
        }
    }

    private fun updateProcessNoise() {
        q[2][2] = 0.3
    }
}

data class ModernVarioResult(
    val altitude: Double,
    val verticalSpeed: Double,
    val acceleration: Double,
    val confidence: Double
)
