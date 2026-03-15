package com.example.xcpro.sensors.domain

import com.example.xcpro.glider.SpeedBoundsMs
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
        val minGlideSpeedMs = resolveMinGlideSpeedMs(input.iasBounds)
        val canUpdate = straight &&
            input.wMeasMs.isFinite() &&
            input.iasMs.isFinite() &&
            input.iasMs > 0.5 &&
            speedMs.isFinite() &&
            speedMs >= minGlideSpeedMs

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
        val tauSeconds = resolveTauSeconds(speedMs, input.iasBounds)
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

    private fun resolveMinGlideSpeedMs(bounds: SpeedBoundsMs?): Double {
        if (bounds == null) return LEGACY_MIN_GLIDE_SPEED_MS
        val maxMs = bounds.maxMs
        return when {
            maxMs <= LOW_SPEED_PROFILE_MAX_MS -> LOW_SPEED_MIN_GLIDE_SPEED_MS
            maxMs <= MID_SPEED_PROFILE_MAX_MS -> MID_SPEED_MIN_GLIDE_SPEED_MS
            else -> LEGACY_MIN_GLIDE_SPEED_MS
        }
    }

    private fun resolveTauSeconds(speedMs: Double, bounds: SpeedBoundsMs?): Double {
        val safeSpeed = speedMs.coerceAtLeast(MIN_WINDOW_SPEED_MS)
        if (bounds == null) {
            return LEGACY_WINDOW_METERS / safeSpeed
        }

        val targetTau = resolveTargetTauSeconds(bounds)
        val dynamicWindowMeters = (targetTau * safeSpeed)
            .coerceIn(MIN_DYNAMIC_WINDOW_METERS, MAX_DYNAMIC_WINDOW_METERS)
        return dynamicWindowMeters / safeSpeed
    }

    private fun resolveTargetTauSeconds(bounds: SpeedBoundsMs): Double {
        val maxMs = bounds.maxMs
        return when {
            maxMs <= LOW_SPEED_PROFILE_MAX_MS -> LOW_SPEED_TARGET_TAU_SECONDS
            maxMs <= MID_SPEED_PROFILE_MAX_MS -> MID_SPEED_TARGET_TAU_SECONDS
            else -> HIGH_SPEED_TARGET_TAU_SECONDS
        }
    }

    companion object {
        private const val LEGACY_WINDOW_METERS = 600.0
        private const val LEGACY_MIN_GLIDE_SPEED_MS = 12.0
        private const val LOW_SPEED_MIN_GLIDE_SPEED_MS = 6.0
        private const val MID_SPEED_MIN_GLIDE_SPEED_MS = 8.0
        private const val MIN_WINDOW_SPEED_MS = 6.0
        private const val LOW_SPEED_PROFILE_MAX_MS = 20.0
        private const val MID_SPEED_PROFILE_MAX_MS = 32.0
        private const val LOW_SPEED_TARGET_TAU_SECONDS = 22.0
        private const val MID_SPEED_TARGET_TAU_SECONDS = 28.0
        private const val HIGH_SPEED_TARGET_TAU_SECONDS = 33.0
        private const val MIN_DYNAMIC_WINDOW_METERS = 120.0
        private const val MAX_DYNAMIC_WINDOW_METERS = 600.0
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
    val hasPolar: Boolean,
    val iasBounds: SpeedBoundsMs? = null
)
