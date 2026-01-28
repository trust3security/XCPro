package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.hypot

/**
 * Kotlin port of the WindEKF estimator (src/Computer/Wind/WindEKF.cpp).
 */
class WindEkf {

    private var east = 0f
    private var north = 0f
    private var scale = 1f
    private var gain = WIND_K0 * 4f

    fun reset() {
        east = 0f
        north = 0f
        scale = 1f
        gain = WIND_K0 * 4f
    }

    /**
     * Updates the EKF and returns the latest wind vector (towards direction).
     */
    fun update(trueAirspeed: Float, groundEast: Float, groundNorth: Float): WindVector? {
        if (!trueAirspeed.isFinite() || trueAirspeed < MIN_TAS) return null

        val dx = groundEast - east
        val dy = groundNorth - north
        val mag = hypot(dx, dy)
        if (!mag.isFinite() || mag < 1e-3f) return null

        val k0 = -scale * dx / mag * gain
        val k1 = -scale * dy / mag * gain
        val k2 = mag * WIND_K1

        gain += 0.01f * (WIND_K0 - gain)

        val innovation = trueAirspeed - scale * mag
        east += k0 * innovation
        north += k1 * innovation
        scale = (scale + k2 * innovation).coerceIn(0.5f, 1.5f)

        return WindVector(east = -east.toDouble(), north = -north.toDouble())
    }

    companion object {
        private const val WIND_K0 = 1.0e-2f
        private const val WIND_K1 = 1.0e-5f
        private const val MIN_TAS = 1f
    }
}
