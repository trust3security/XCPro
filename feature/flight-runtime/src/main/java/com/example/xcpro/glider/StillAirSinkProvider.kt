package com.example.xcpro.glider

/**
 * Provides still-air sink (m/s) for the currently selected glider at a given
 * true airspeed. This is a pure runtime port used by flight metrics and glide
 * calculations across feature modules.
 */
interface StillAirSinkProvider {
    fun sinkAtSpeed(airspeedMs: Double): Double?

    fun iasBoundsMs(): SpeedBoundsMs?

    fun ldAtSpeed(airspeedMs: Double): Double? {
        val sinkMs = sinkAtSpeed(airspeedMs) ?: return null
        if (!airspeedMs.isFinite() || airspeedMs <= 0.0) return null
        if (!sinkMs.isFinite() || sinkMs <= 0.0) return null
        val ld = airspeedMs / sinkMs
        return ld.takeIf { it.isFinite() && it > 0.0 }
    }

    fun bestLd(): Double? = null
}
