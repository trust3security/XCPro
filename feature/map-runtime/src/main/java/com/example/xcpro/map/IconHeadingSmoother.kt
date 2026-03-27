package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.orientation.normalizeBearing
import com.example.xcpro.orientation.shortestDeltaDegrees
import kotlin.math.abs
import kotlin.math.max

/**
 * Visual-only icon heading smoother.
 * - Applies heading validity + speed hysteresis gates.
 * - Uses time-based angular velocity clamp and deadband.
 * - Does not alter SSOT data or sensor pipelines.
 */
class IconHeadingSmoother(
    private val config: IconRotationConfig
) {
    data class IconHeadingInput(
        val headingDeg: Double,
        val headingValid: Boolean,
        val trackDeg: Double,
        val bearingAccuracyDeg: Double? = null,
        val speedAccuracyMs: Double? = null,
        val mapBearing: Double,
        val orientationMode: MapOrientationMode,
        val speedMs: Double,
        val nowMs: Long
    )

    private var lastOutputDeg: Double? = null
    private var lastStableHeadingDeg: Double? = null
    private var headingModeActive: Boolean = false
    private var lastUpdateMs: Long? = null
    private var lastMode: MapOrientationMode? = null

    fun reset() {
        lastOutputDeg = null
        lastStableHeadingDeg = null
        headingModeActive = false
        lastUpdateMs = null
        lastMode = null
    }

    fun update(input: IconHeadingInput): Double {
        if (lastMode != null && lastMode != input.orientationMode) {
            reset()
        }
        lastMode = input.orientationMode

        val speedMs = if (input.speedMs.isFinite()) input.speedMs.coerceAtLeast(0.0) else 0.0
        val headingValid = input.headingValid && input.headingDeg.isFinite()

        if (headingModeActive) {
            if (!headingValid || speedMs <= config.exitSpeedMs) {
                headingModeActive = false
            }
        } else {
            if (headingValid && speedMs >= config.enterSpeedMs) {
                headingModeActive = true
            }
        }

        if (headingModeActive) {
            lastStableHeadingDeg = input.headingDeg
        }

        val targetHeading = resolveTargetHeading(
            headingModeActive = headingModeActive,
            input = input
        )

        val desired = normalizeBearing(targetHeading)
        val previousUpdateMs = lastUpdateMs
        val dtMs = previousUpdateMs?.let { (input.nowMs - it).coerceAtLeast(0L) } ?: 0L
        lastUpdateMs = input.nowMs

        if (lastOutputDeg == null) {
            lastOutputDeg = desired
            return desired
        }

        if (dtMs == 0L) {
            return lastOutputDeg!!
        }

        val bearingAccuracyDeg = when {
            input.orientationMode == MapOrientationMode.TRACK_UP -> {
                input.bearingAccuracyDeg
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.coerceIn(0.0, MAX_BEARING_ACCURACY_DEG)
            }
            !headingModeActive -> {
                input.bearingAccuracyDeg
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.coerceIn(0.0, MAX_BEARING_ACCURACY_DEG)
            }
            else -> null
        }

        val deadbandDeg = bearingAccuracyDeg?.let { accuracyDeg ->
            config.deadbandDeg + (config.accuracyDeadbandScaleDegPerDeg * accuracyDeg)
        } ?: config.deadbandDeg

        val maxAngularVelocity = bearingAccuracyDeg?.let { accuracyDeg ->
            val scale = config.accuracyMaxTurnScale
            if (scale > 0.0) {
                config.maxAngularVelocityDegPerSec / (1.0 + (accuracyDeg / scale))
            } else {
                config.maxAngularVelocityDegPerSec
            }
        } ?: config.maxAngularVelocityDegPerSec

        val maxStep = maxAngularVelocity * (dtMs.coerceAtMost(config.maxDtMs) / 1000.0)
        val delta = shortestDeltaDegrees(lastOutputDeg!!, desired)
        val absDelta = abs(delta)

        if (absDelta < deadbandDeg) {
            return lastOutputDeg!!
        }

        val limitedDelta = if (absDelta > maxStep) {
            kotlin.math.sign(delta) * maxStep
        } else {
            delta
        }

        val next = normalizeBearing(lastOutputDeg!! + limitedDelta)
        lastOutputDeg = next
        return next
    }

    private fun resolveTargetHeading(
        headingModeActive: Boolean,
        input: IconHeadingInput
    ): Double {
        val trackFallback = if (input.trackDeg.isFinite()) input.trackDeg else input.mapBearing
        return when (input.orientationMode) {
            MapOrientationMode.HEADING_UP -> {
                if (headingModeActive) {
                    input.headingDeg
                } else {
                    // Keep icon pointing up when heading is invalid.
                    input.mapBearing
                }
            }
            MapOrientationMode.TRACK_UP -> {
                // Always align icon with ground track in TRACK_UP to match travel direction.
                trackFallback
            }
            MapOrientationMode.NORTH_UP -> {
                if (headingModeActive) {
                    input.headingDeg
                } else {
                    // Use track when speed is stable; otherwise keep last stable heading.
                    if (input.speedMs >= config.enterSpeedMs) {
                        trackFallback
                    } else {
                        lastStableHeadingDeg ?: trackFallback
                    }
                }
            }
        }
    }

    private companion object {
        private const val MAX_BEARING_ACCURACY_DEG = 30.0
    }
}

data class IconRotationConfig(
    val enterSpeedMs: Double,
    val exitSpeedMs: Double,
    val maxAngularVelocityDegPerSec: Double,
    val deadbandDeg: Double,
    val maxDtMs: Long,
    val accuracyDeadbandScaleDegPerDeg: Double = DEFAULT_ACCURACY_DEADBAND_SCALE_DEG_PER_DEG,
    val accuracyMaxTurnScale: Double = DEFAULT_ACCURACY_MAX_TURN_SCALE
) {
    companion object {
        private const val DEFAULT_MIN_SPEED_MS = 2.0
        private const val DEFAULT_HYSTERESIS_FRACTION = 0.25
        private const val DEFAULT_HYSTERESIS_MIN_BAND_MS = 0.5
        private const val DEFAULT_MAX_ANGULAR_VELOCITY_DEG_PER_SEC = 90.0
        private const val DEFAULT_DEADBAND_DEG = 1.0
        private const val DEFAULT_MAX_DT_MS = 500L
        private const val DEFAULT_ACCURACY_DEADBAND_SCALE_DEG_PER_DEG = 0.05
        private const val DEFAULT_ACCURACY_MAX_TURN_SCALE = 10.0

        fun fromMinSpeedThreshold(
            minSpeedMs: Double,
            maxAngularVelocityDegPerSec: Double = DEFAULT_MAX_ANGULAR_VELOCITY_DEG_PER_SEC,
            deadbandDeg: Double = DEFAULT_DEADBAND_DEG,
            maxDtMs: Long = DEFAULT_MAX_DT_MS,
            accuracyDeadbandScaleDegPerDeg: Double = DEFAULT_ACCURACY_DEADBAND_SCALE_DEG_PER_DEG,
            accuracyMaxTurnScale: Double = DEFAULT_ACCURACY_MAX_TURN_SCALE
        ): IconRotationConfig {
            val safeMin = if (minSpeedMs.isFinite() && minSpeedMs >= 0.0) minSpeedMs else DEFAULT_MIN_SPEED_MS
            val band = if (safeMin == 0.0) {
                0.0
            } else {
                max(DEFAULT_HYSTERESIS_MIN_BAND_MS, safeMin * DEFAULT_HYSTERESIS_FRACTION)
            }
            val enter = safeMin + (band * 0.5)
            val exit = (safeMin - (band * 0.5)).coerceAtLeast(0.0)
            return IconRotationConfig(
                enterSpeedMs = enter,
                exitSpeedMs = exit,
                maxAngularVelocityDegPerSec = maxAngularVelocityDegPerSec,
                deadbandDeg = deadbandDeg,
                maxDtMs = maxDtMs,
                accuracyDeadbandScaleDegPerDeg = accuracyDeadbandScaleDegPerDeg,
                accuracyMaxTurnScale = accuracyMaxTurnScale
            )
        }

        fun fromPreferences(preferences: com.example.xcpro.MapOrientationPreferences): IconRotationConfig {
            return fromMinSpeedThreshold(preferences.getMinSpeedThreshold())
        }

        fun defaults(): IconRotationConfig = fromMinSpeedThreshold(DEFAULT_MIN_SPEED_MS)
    }
}
