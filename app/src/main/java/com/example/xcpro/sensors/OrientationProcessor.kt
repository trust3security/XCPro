package com.example.xcpro.sensors

import android.hardware.SensorManager
import android.os.SystemClock

/**
 * Projects linear acceleration readings into the earth frame using the device rotation vector.
 */
class OrientationProcessor(
    private val freshnessWindowMillis: Long = DEFAULT_FRESHNESS_WINDOW_MS
) {

    private val rotationMatrix = FloatArray(9)
    private val tempRotationMatrix = FloatArray(9)
    private var hasRotationMatrix = false
    private var lastRotationUpdateMillis = 0L

    fun updateRotationVector(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(tempRotationMatrix, values)
        System.arraycopy(tempRotationMatrix, 0, rotationMatrix, 0, rotationMatrix.size)
        hasRotationMatrix = true
        lastRotationUpdateMillis = SystemClock.elapsedRealtime()
    }

    fun projectVerticalAcceleration(linearAcceleration: FloatArray): AccelSample {
        val now = SystemClock.elapsedRealtime()
        val orientationFresh = hasRotationMatrix && (now - lastRotationUpdateMillis) <= freshnessWindowMillis

        val ax = linearAcceleration[0].toDouble()
        val ay = linearAcceleration[1].toDouble()
        val az = linearAcceleration[2].toDouble()

        val vertical = if (orientationFresh) {
            rotationMatrix[6] * ax + rotationMatrix[7] * ay + rotationMatrix[8] * az
        } else {
            az
        }

        return AccelSample(
            verticalAcceleration = vertical,
            isReliable = orientationFresh
        )
    }

    fun reset() {
        hasRotationMatrix = false
        lastRotationUpdateMillis = 0L
    }

    data class AccelSample(
        val verticalAcceleration: Double,
        val isReliable: Boolean
    )

    companion object {
        private const val DEFAULT_FRESHNESS_WINDOW_MS = 500L
    }
}
