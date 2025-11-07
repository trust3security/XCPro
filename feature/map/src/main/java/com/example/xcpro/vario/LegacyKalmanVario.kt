package com.example.xcpro.vario

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Legacy Kalman Filter Vario (Baseline for comparison)
 *
 * Algorithm: 3-State Kalman Filter with ORIGINAL noise parameters
 * - R_altitude = 2.0m (old conservative value)
 * - R_accel = 0.5 m/s² (old value)
 *
 * Purpose:
 * - Baseline for comparison with optimized version
 * - Shows impact of Priority 1 improvements
 * - Expected to be 30-50% slower than optimized
 *
 * This is the "before" state - slower response but proven stable.
 */
class LegacyKalmanVario : IVarioCalculator {

    override val name = "Legacy Kalman"
    override val description = "3-State Kalman (R=2.0m) - Original parameters"

    // State vector: [altitude(m), velocity(m/s), acceleration(m/s²)]
    private val state = Array(3) { 0.0 }

    // Error covariance matrix (3x3)
    private val P = Array(3) { Array(3) { 0.0 } }

    // Process noise covariance (tunable)
    private var Q = Array(3) { Array(3) { 0.0 } }

    // Measurement noise - LEGACY VALUES (conservative)
    private var R_altitude = 2.0      // Barometer noise (m) - OLD VALUE
    private var R_accel = 0.5         // Accelerometer noise (m/s²) - OLD VALUE

    private var isInitialized = false
    private var lastUpdateTime = 0L

    init {
        // Initialize error covariance
        P[0][0] = 10.0   // altitude uncertainty
        P[1][1] = 5.0    // velocity uncertainty
        P[2][2] = 2.0    // acceleration uncertainty

        // Initialize process noise
        Q[2][2] = 0.3 // Acceleration process noise
    }

    override fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double,
        gpsSpeed: Double,
        gpsAltitude: Double
    ): Double {
        val currentTime = System.currentTimeMillis()

        if (!isInitialized) {
            // Initialize with first measurements
            state[0] = baroAltitude
            state[1] = 0.0
            state[2] = verticalAccel
            isInitialized = true
            lastUpdateTime = currentTime
            return 0.0
        }

        // Adaptive barometer noise based on motion state
        R_altitude = when {
            gpsSpeed < 0.5 -> 20.0   // Stationary: Heavy filtering
            gpsSpeed < 2.0 -> 10.0   // Slow movement
            gpsSpeed < 5.0 -> 5.0    // Moderate speed
            else -> 2.0              // Fast flight - LEGACY BASELINE
        }

        // Adaptive process noise based on flight conditions
        adaptProcessNoise(state[1], abs(verticalAccel))

        // PREDICTION STEP
        val dt = deltaTime
        val dt2 = dt * dt

        val predictedAltitude = state[0] + state[1]*dt + 0.5*state[2]*dt2
        val predictedVelocity = state[1] + state[2]*dt
        val predictedAccel = state[2]

        // Update process noise based on dt
        Q[0][0] = 0.25 * dt2 * dt2 * Q[2][2]
        Q[0][1] = 0.5 * dt * dt2 * Q[2][2]
        Q[0][2] = 0.5 * dt2 * Q[2][2]
        Q[1][0] = Q[0][1]
        Q[1][1] = dt2 * Q[2][2]
        Q[1][2] = dt * Q[2][2]
        Q[2][0] = Q[0][2]
        Q[2][1] = Q[1][2]

        // Predict error covariance: P(k+1) = F*P*F' + Q
        val F = arrayOf(
            arrayOf(1.0, dt, 0.5*dt2),
            arrayOf(0.0, 1.0, dt),
            arrayOf(0.0, 0.0, 1.0)
        )

        val P_predicted = matrixMultiply(matrixMultiply(F, P), transpose(F))
        addMatrix(P_predicted, Q)

        // MEASUREMENT UPDATE
        val y1 = baroAltitude - predictedAltitude
        val y2 = verticalAccel - predictedAccel

        // Innovation covariance
        val S11 = P_predicted[0][0] + R_altitude
        val S22 = P_predicted[2][2] + R_accel

        // Kalman gain
        val K1 = Array(3) { 0.0 }
        val K2 = Array(3) { 0.0 }

        if (S11 > 0.001) {
            K1[0] = P_predicted[0][0] / S11
            K1[1] = P_predicted[1][0] / S11
            K1[2] = P_predicted[2][0] / S11
        }

        if (S22 > 0.001) {
            K2[0] = P_predicted[0][2] / S22
            K2[1] = P_predicted[1][2] / S22
            K2[2] = P_predicted[2][2] / S22
        }

        // Update state
        state[0] = predictedAltitude + K1[0]*y1 + K2[0]*y2
        state[1] = predictedVelocity + K1[1]*y1 + K2[1]*y2
        state[2] = predictedAccel + K1[2]*y1 + K2[2]*y2

        // Apply deadband to velocity
        if (abs(state[1]) < 0.02) {
            state[1] = 0.0
        }

        // Update error covariance
        for (i in 0..2) {
            for (j in 0..2) {
                P[i][j] = P_predicted[i][j] -
                         (K1[i] * P_predicted[0][j] + K2[i] * P_predicted[2][j])
            }
        }

        lastUpdateTime = currentTime

        return state[1]  // Return vertical speed
    }

    private fun adaptProcessNoise(velocity: Double, accelMagnitude: Double) {
        val baseNoise = when {
            accelMagnitude > 1.5 || abs(velocity) > 2.0 -> 0.8
            accelMagnitude > 0.5 || abs(velocity) > 0.5 -> 0.3
            else -> 0.1
        }
        Q[2][2] = baseNoise
    }

    override fun reset() {
        isInitialized = false
        state[0] = 0.0
        state[1] = 0.0
        state[2] = 0.0
        P[0][0] = 10.0
        P[1][1] = 5.0
        P[2][2] = 2.0
    }

    override fun getVerticalSpeed(): Double {
        return state[1]
    }

    override fun getDiagnostics(): String {
        return "$name: ${String.format("%.2f", state[1])} m/s | R=${String.format("%.1f", R_altitude)}m"
    }

    // Matrix operations
    private fun matrixMultiply(A: Array<Array<Double>>, B: Array<Array<Double>>): Array<Array<Double>> {
        val result = Array(A.size) { Array(B[0].size) { 0.0 } }
        for (i in A.indices) {
            for (j in B[0].indices) {
                for (k in A[0].indices) {
                    result[i][j] += A[i][k] * B[k][j]
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

    private fun addMatrix(A: Array<Array<Double>>, B: Array<Array<Double>>) {
        for (i in A.indices) {
            for (j in A[0].indices) {
                A[i][j] += B[i][j]
            }
        }
    }
}
