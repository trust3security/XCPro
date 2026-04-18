package com.trust3.xcpro.sensors

import kotlin.math.exp

/**
 * Deterministic pneumatic-style needle response (first-order, no overshoot).
 *
 * The response is defined by a 95% step time (t95). This is converted to a
 * time constant so the needle can be updated with variable dt.
 */
internal class NeedleVarioDynamics(
    t95Seconds: Double,
    private val clamp: Double
) {
    private var state = 0.0
    private val tauSeconds = t95Seconds / LN_20

    fun update(target: Double, deltaTimeSeconds: Double, isValid: Boolean): Double {
        if (!state.isFinite()) state = 0.0
        val dt = deltaTimeSeconds.coerceAtLeast(0.0)
        val goal = if (isValid && target.isFinite()) target else 0.0
        if (dt > 0.0) {
            val alpha = 1.0 - exp(-dt / tauSeconds)
            state += alpha * (goal - state)
        }
        if (!state.isFinite()) state = 0.0
        state = state.coerceIn(-clamp, clamp)
        return state
    }

    fun reset() {
        state = 0.0
    }

    private companion object {
        private const val LN_20 = 2.995732273553991 // ln(20)
    }
}
