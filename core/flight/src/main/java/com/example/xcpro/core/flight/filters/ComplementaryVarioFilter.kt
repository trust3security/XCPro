package com.example.xcpro.core.flight.filters

import kotlin.math.abs
import kotlin.math.sqrt

class ComplementaryVarioFilter(
    private val config: AdaptiveVarioConfig = AdaptiveVarioConfig()
) {
    private var baroVerticalSpeed = 0.0
    private var accelVerticalSpeed = 0.0
    private var lastBaroAltitude = 0.0
    private var isInitialized = false
    private var accelBias = 0.0
    private val biasTimeConstant = 5.0
    private var lastBaroVSpeed = 0.0
    private val baroVarianceTracker = AdaptiveVarianceTracker(config.baroVarianceWindowSize)
    private var lastFusedVerticalSpeed = 0.0

    fun update(
        baroAltitude: Double,
        verticalAccel: Double,
        deltaTime: Double
    ): ComplementaryVarioResult {
        if (!isInitialized) {
            lastBaroAltitude = baroAltitude
            isInitialized = true
            accelBias = verticalAccel
            lastBaroVSpeed = 0.0
            return ComplementaryVarioResult(0.0, 0.0, 0.0)
        }
        if (deltaTime <= 0.001 || deltaTime > 1.0) {
            return ComplementaryVarioResult(lastFusedVerticalSpeed, baroVerticalSpeed, accelVerticalSpeed)
        }

        val rawBaroVSpeed = (baroAltitude - lastBaroAltitude) / deltaTime
        lastBaroAltitude = baroAltitude
        val baroLpf = 0.7
        baroVerticalSpeed = baroLpf * rawBaroVSpeed + (1.0 - baroLpf) * lastBaroVSpeed
        lastBaroVSpeed = baroVerticalSpeed
        baroVarianceTracker.add(baroVerticalSpeed)

        val biasAlpha = deltaTime / biasTimeConstant
        accelBias += biasAlpha * (verticalAccel - accelBias)
        val accelCorrected = verticalAccel - accelBias
        val accelDeltaV = accelCorrected * deltaTime
        val accelHpf = 0.98
        accelVerticalSpeed = accelHpf * (accelVerticalSpeed + accelDeltaV)

        val sigma2Baro = baroVarianceTracker.variance()
        val tauEff = (config.tauBaseSeconds / (1.0 + sqrt(sigma2Baro)))
            .coerceIn(config.tauMinSeconds, config.tauMaxSeconds)
        val accelWeight = (deltaTime / (tauEff + deltaTime)).coerceIn(0.0, 1.0)
        val baroWeight = 1.0 - accelWeight
        val fusedVerticalSpeed = baroWeight * baroVerticalSpeed + accelWeight * accelVerticalSpeed
        val finalVerticalSpeed = if (abs(fusedVerticalSpeed) < 0.02) 0.0 else fusedVerticalSpeed
        lastFusedVerticalSpeed = finalVerticalSpeed
        return ComplementaryVarioResult(finalVerticalSpeed, baroVerticalSpeed, accelVerticalSpeed)
    }

    fun reset() {
        baroVerticalSpeed = 0.0
        accelVerticalSpeed = 0.0
        lastBaroAltitude = 0.0
        isInitialized = false
        accelBias = 0.0
        lastBaroVSpeed = 0.0
        lastFusedVerticalSpeed = 0.0
        baroVarianceTracker.reset()
    }

    fun getVerticalSpeed(): Double = lastFusedVerticalSpeed

    fun getDiagnostics(): String {
        val vSpeed = lastFusedVerticalSpeed
        return "Comp: V/S=${String.format("%.2f", vSpeed)}m/s, " +
            "Baro=${String.format("%.2f", baroVerticalSpeed)}m/s, " +
            "Accel=${String.format("%.2f", accelVerticalSpeed)}m/s, " +
            "Bias=${String.format("%.3f", accelBias)}m/s"
    }
}

data class ComplementaryVarioResult(
    val verticalSpeed: Double,
    val baroComponent: Double,
    val accelComponent: Double
)
