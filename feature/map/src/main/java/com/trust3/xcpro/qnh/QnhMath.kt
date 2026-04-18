package com.trust3.xcpro.qnh

import kotlin.math.pow

/**
 * Pure QNH math helpers (ISA-based).
 */
object QnhMath {
    private const val TEMPERATURE_SEA_LEVEL_K = 288.15
    private const val LAPSE_RATE_K_PER_M = 0.0065
    private const val GAS_CONSTANT = 287.04
    private const val GRAVITY = 9.80665

    /**
     * Compute QNH (sea level pressure) from pressure at altitude (ISA).
     */
    fun computeQnhFromPressure(pressureHpa: Double, altitudeMeters: Double): Double {
        val temperatureAtAltitude = TEMPERATURE_SEA_LEVEL_K - (LAPSE_RATE_K_PER_M * altitudeMeters)
        val exponent = GRAVITY / (GAS_CONSTANT * LAPSE_RATE_K_PER_M)
        val pressureRatio = (temperatureAtAltitude / TEMPERATURE_SEA_LEVEL_K).pow(exponent)
        return pressureHpa / pressureRatio
    }
}

