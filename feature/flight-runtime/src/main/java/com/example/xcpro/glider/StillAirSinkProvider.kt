package com.example.xcpro.glider

/**
 * Provides still-air sink (m/s) for the currently selected glider at a given
 * indicated airspeed (IAS) in SI units. This is the shared runtime port used
 * by flight metrics and final-glide calculations across feature modules.
 *
 * Phase 4 release contract:
 * - the active runtime path is IAS-based end-to-end
 * - the authoritative curve is the selected model polar unless a manual
 *   three-point polar overrides it upstream
 * - reference weight and user coefficients are not part of this runtime port
 */
interface StillAirSinkProvider {
    fun sinkAtSpeed(indicatedAirspeedMs: Double): Double?

    fun iasBoundsMs(): SpeedBoundsMs?

    fun ldAtSpeed(indicatedAirspeedMs: Double): Double? {
        val sinkMs = sinkAtSpeed(indicatedAirspeedMs) ?: return null
        if (!indicatedAirspeedMs.isFinite() || indicatedAirspeedMs <= 0.0) return null
        if (!sinkMs.isFinite() || sinkMs <= 0.0) return null
        val ld = indicatedAirspeedMs / sinkMs
        return ld.takeIf { it.isFinite() && it > 0.0 }
    }

    fun bestLd(): Double? = null
}
