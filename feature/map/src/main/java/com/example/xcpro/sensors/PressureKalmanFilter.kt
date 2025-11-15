package com.example.xcpro.sensors

import kotlin.math.max

/**
 * Lightweight 1D Kalman filter that mirrors XCSoar's SelfTimingKalmanFilter1d.
 *
 * We operate directly on atmospheric pressure (hPa) so that altitude
 * conversions downstream receive a smoothed signal even when IMU data
 * is unavailable.  The model tracks absolute pressure (x) and its
 * first derivative (velocity) with configurable process/measurement
 * variances and automatically resets if samples arrive too far apart.
 */
class PressureKalmanFilter(
    private val processVariance: Double = PHONE_PROCESS_VARIANCE,
    private val measurementVariance: Double = MEASUREMENT_VARIANCE,
    private val maxDeltaMillis: Long = MAX_DELTA_MILLIS
 ) {

    private var xAbs = 0.0
    private var xVel = 0.0
    private var pAbsAbs = LARGE_VARIANCE
    private var pAbsVel = 0.0
    private var pVelVel = processVariance

    private var lastTimestampMillis: Long = 0L
    private var initialized = false

    /**
     * Reset the filter. Optionally seed it with the latest measurement.
     */
    fun reset(measurement: Double? = null, timestampMillis: Long? = null) {
        xAbs = measurement ?: 0.0
        xVel = 0.0
        pAbsAbs = LARGE_VARIANCE
        pAbsVel = 0.0
        pVelVel = processVariance
        if (measurement != null && timestampMillis != null) {
            initialized = true
            lastTimestampMillis = timestampMillis
        } else {
            initialized = false
            lastTimestampMillis = 0L
        }
    }

    /**
     * Update with a new pressure sample (hPa) and return the smoothed value.
     */
    fun update(measurement: Double, timestampMillis: Long): Double {
        if (!initialized) {
            reset(measurement, timestampMillis)
            return measurement
        }

        var deltaMillis = timestampMillis - lastTimestampMillis
        lastTimestampMillis = timestampMillis

        if (deltaMillis <= 0L) {
            deltaMillis = MIN_DELTA_MILLIS
        }

        if (deltaMillis > maxDeltaMillis) {
            reset(measurement, timestampMillis)
            return measurement
        }

        val dtSeconds = max(deltaMillis, MIN_DELTA_MILLIS).toDouble() / MILLIS_PER_SECOND
        predict(dtSeconds)
        applyMeasurement(measurement)

        return xAbs
    }

    private fun predict(dt: Double) {
        val dt2 = dt * dt
        val dt3 = dt * dt2
        val dt4 = dt2 * dt2

        xAbs += xVel * dt
        pAbsAbs += 2 * dt * pAbsVel + dt2 * pVelVel + processVariance * dt4 / 4.0
        pAbsVel += dt * pVelVel + processVariance * dt3 / 2.0
        pVelVel += processVariance * dt2
    }

    private fun applyMeasurement(measurement: Double) {
        val innovation = measurement - xAbs
        val innovationPrecision = 1.0 / (pAbsAbs + measurementVariance)
        val kAbs = pAbsAbs * innovationPrecision
        val kVel = pAbsVel * innovationPrecision

        xAbs += kAbs * innovation
        xVel += kVel * innovation

        pVelVel -= pAbsVel * kVel
        pAbsVel -= pAbsVel * kAbs
        pAbsAbs -= pAbsAbs * kAbs
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
        private const val MIN_DELTA_MILLIS = 1L
        private const val LARGE_VARIANCE = 1e6

        // Tune values mirror XCSoar constants for phone sensors.
        private const val PHONE_PROCESS_VARIANCE = 0.0075
        private const val MEASUREMENT_VARIANCE = 0.25
        private const val MAX_DELTA_MILLIS = 60_000L
    }
}
