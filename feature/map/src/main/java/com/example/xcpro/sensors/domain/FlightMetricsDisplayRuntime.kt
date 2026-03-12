package com.example.xcpro.sensors.domain

import com.example.xcpro.sensors.DisplayVarioSmoother
import com.example.xcpro.sensors.NeedleVarioDynamics
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_DECAY_FACTOR
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_SMOOTH_TIME_S
import com.example.xcpro.sensors.domain.FlightMetricsConstants.DISPLAY_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.FAST_NEEDLE_T95_SECONDS
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_VAR_CLAMP
import com.example.xcpro.sensors.domain.FlightMetricsConstants.NEEDLE_T95_SECONDS
import kotlin.math.abs

internal class FlightMetricsDisplayRuntime {
    private val displaySmoother = DisplayVarioSmoother(
        smoothTimeSeconds = DISPLAY_SMOOTH_TIME_S,
        decayFactor = DISPLAY_DECAY_FACTOR,
        clamp = DISPLAY_VAR_CLAMP
    )
    private val baselineDisplaySmoother = DisplayVarioSmoother(
        smoothTimeSeconds = DISPLAY_SMOOTH_TIME_S,
        decayFactor = DISPLAY_DECAY_FACTOR,
        clamp = DISPLAY_VAR_CLAMP
    )
    private val needleDynamics = NeedleVarioDynamics(
        t95Seconds = NEEDLE_T95_SECONDS,
        clamp = NEEDLE_VAR_CLAMP
    )
    private val fastNeedleDynamics = NeedleVarioDynamics(
        t95Seconds = FAST_NEEDLE_T95_SECONDS,
        clamp = DISPLAY_VAR_CLAMP
    )
    private var groundZeroAccumulatedSeconds: Double = 0.0

    fun update(
        bruttoVario: Double,
        varioValid: Boolean,
        baselineVario: Double,
        baselineVarioValid: Boolean,
        rawDisplayNetto: Double,
        nettoValid: Boolean,
        deltaTimeSeconds: Double,
        isFlying: Boolean,
        groundSpeedMs: Double
    ): DisplayOutputs {
        val displayVarioRaw = displaySmoother.smoothVario(
            raw = bruttoVario,
            deltaTime = deltaTimeSeconds,
            isValid = varioValid
        )
        val displayVario = if (isFlying) {
            displayVarioRaw
        } else {
            applyGroundZeroBias(
                displayVario = displayVarioRaw,
                groundSpeedMs = groundSpeedMs,
                deltaTimeSeconds = deltaTimeSeconds
            )
        }
        val displayBaselineVario = baselineDisplaySmoother.smoothVario(
            raw = baselineVario,
            deltaTime = deltaTimeSeconds,
            isValid = baselineVarioValid
        )
        val displayNetto = displaySmoother.smoothNetto(
            raw = rawDisplayNetto,
            deltaTime = deltaTimeSeconds,
            isValid = nettoValid
        )
        val displayNeedleVario = needleDynamics.update(
            target = bruttoVario,
            deltaTimeSeconds = deltaTimeSeconds,
            isValid = varioValid
        )
        val displayNeedleVarioFast = fastNeedleDynamics.update(
            target = bruttoVario,
            deltaTimeSeconds = deltaTimeSeconds,
            isValid = varioValid
        )
        return DisplayOutputs(
            displayVario = displayVario,
            displayBaselineVario = displayBaselineVario,
            displayNetto = displayNetto,
            displayNeedleVario = displayNeedleVario,
            displayNeedleVarioFast = displayNeedleVarioFast
        )
    }

    fun reset() {
        displaySmoother.reset()
        baselineDisplaySmoother.reset()
        needleDynamics.reset()
        fastNeedleDynamics.reset()
        groundZeroAccumulatedSeconds = 0.0
    }

    private fun applyGroundZeroBias(
        displayVario: Double,
        groundSpeedMs: Double,
        deltaTimeSeconds: Double
    ): Double {
        if (abs(displayVario) < GROUND_ZERO_THRESHOLD_MS && groundSpeedMs < GROUND_ZERO_SPEED_MS) {
            groundZeroAccumulatedSeconds += deltaTimeSeconds
            if (groundZeroAccumulatedSeconds >= GROUND_ZERO_SETTLE_SECONDS) {
                return 0.0
            }
        } else {
            groundZeroAccumulatedSeconds = 0.0
        }
        return displayVario
    }

    data class DisplayOutputs(
        val displayVario: Double,
        val displayBaselineVario: Double,
        val displayNetto: Double,
        val displayNeedleVario: Double,
        val displayNeedleVarioFast: Double
    )

    private companion object {
        private const val GROUND_ZERO_THRESHOLD_MS = 0.05
        private const val GROUND_ZERO_SPEED_MS = 0.5
        private const val GROUND_ZERO_SETTLE_SECONDS = 3.0
    }
}
