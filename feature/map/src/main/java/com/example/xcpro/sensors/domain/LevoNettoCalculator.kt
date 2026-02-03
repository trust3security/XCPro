package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.StillAirSinkProvider
internal data class LevoNettoResult(
    val valueMs: Double,
    val valid: Boolean,
    val hasWind: Boolean,
    val hasPolar: Boolean,
    val confidence: Double
)

internal class LevoNettoCalculator(
    private val sinkProvider: StillAirSinkProvider
) {

    private var lastValueMs: Double = 0.0
    private var hasValidValue: Boolean = false

    fun update(input: LevoNettoInput): LevoNettoResult {
        if (!input.hasWind) {
            return LevoNettoResult(
                valueMs = lastValueMs,
                valid = false,
                hasWind = false,
                hasPolar = input.hasPolar,
                confidence = input.windConfidence
            )
        }
        if (!input.hasPolar) {
            return LevoNettoResult(
                valueMs = lastValueMs,
                valid = false,
                hasWind = true,
                hasPolar = false,
                confidence = input.windConfidence
            )
        }
        if (!input.isFlying) {
            return LevoNettoResult(
                valueMs = lastValueMs,
                valid = false,
                hasWind = true,
                hasPolar = true,
                confidence = input.windConfidence
            )
        }

        val straight = !input.isCircling && !input.isTurning
        val speedMs = resolveSpeedForWindow(input)
        val canUpdate = straight &&
            input.wMeasMs.isFinite() &&
            input.iasMs.isFinite() &&
            input.iasMs > 0.5 &&
            speedMs.isFinite() &&
            speedMs >= MIN_GLIDE_SPEED_MS

        if (!canUpdate) {
            return LevoNettoResult(
                valueMs = lastValueMs,
                valid = hasValidValue,
                hasWind = true,
                hasPolar = true,
                confidence = input.windConfidence
            )
        }

        val sink = sinkProvider.sinkAtSpeed(input.iasMs)
            ?.takeIf { it.isFinite() }
            ?: return LevoNettoResult(
                valueMs = lastValueMs,
                valid = false,
                hasWind = true,
                hasPolar = false,
                confidence = input.windConfidence
            )

        val raw = input.wMeasMs - sink
        val tauSeconds = WINDOW_METERS / speedMs.coerceAtLeast(MIN_WINDOW_SPEED_MS)
        val alpha = (input.deltaTimeSeconds / (tauSeconds + input.deltaTimeSeconds)).coerceIn(0.0, 1.0)
        val next = if (hasValidValue) {
            lastValueMs + alpha * (raw - lastValueMs)
        } else {
            raw
        }
        lastValueMs = next
        hasValidValue = true

        return LevoNettoResult(
            valueMs = next,
            valid = true,
            hasWind = true,
            hasPolar = true,
            confidence = input.windConfidence
        )
    }

    fun reset() {
        lastValueMs = 0.0
        hasValidValue = false
    }

    private fun resolveSpeedForWindow(input: LevoNettoInput): Double {
        val tas = input.tasMs?.takeIf { it.isFinite() && it > 0.5 }
        if (tas != null) return tas
        return input.iasMs
    }

    companion object {
        private const val WINDOW_METERS = 600.0
        private const val MIN_GLIDE_SPEED_MS = 12.0
        private const val MIN_WINDOW_SPEED_MS = 8.0
    }
}

internal data class LevoNettoInput(
    val wMeasMs: Double,
    val iasMs: Double,
    val tasMs: Double?,
    val deltaTimeSeconds: Double,
    val isFlying: Boolean,
    val isCircling: Boolean,
    val isTurning: Boolean,
    val hasWind: Boolean,
    val windConfidence: Double,
    val hasPolar: Boolean
)
