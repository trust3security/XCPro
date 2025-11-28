package com.example.xcpro.sensors

/**
 * Stateless wrapper around display smoothing/clamping for vario/netto.
 * Maintains internal state; callers hold one instance per stream.
 */
internal class DisplayVarioSmoother(
    private val smoothTimeSeconds: Double,
    private val decayFactor: Double,
    private val clamp: Double
) {
    private var displayVarioState = 0.0
    private var displayNettoState = 0.0

    fun smoothVario(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / smoothTimeSeconds).coerceIn(0.0, 1.0)
        displayVarioState += targetAlpha * (raw - displayVarioState)
        if (!isValid) {
            displayVarioState *= decayFactor
        }
        if (!displayVarioState.isFinite()) displayVarioState = 0.0
        return displayVarioState.coerceIn(-clamp, clamp)
    }

    fun smoothNetto(raw: Double, deltaTime: Double, isValid: Boolean): Double {
        val targetAlpha = (deltaTime / smoothTimeSeconds).coerceIn(0.0, 1.0)
        displayNettoState += targetAlpha * (raw - displayNettoState)
        if (!isValid) {
            displayNettoState *= decayFactor
        }
        if (!displayNettoState.isFinite()) displayNettoState = 0.0
        return displayNettoState.coerceIn(-clamp, clamp)
    }

    fun reset() {
        displayVarioState = 0.0
        displayNettoState = 0.0
    }
}
