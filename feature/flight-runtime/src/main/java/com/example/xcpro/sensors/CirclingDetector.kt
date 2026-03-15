package com.example.xcpro.sensors

import kotlin.math.PI
import kotlin.math.abs

/**
 * Mirrors legacy circling detector heuristics (turn-rate threshold with
 * enter/exit timers) so calculations that depend on circling mode (thermal
 * averages, wind, etc.) can share a single source of truth.
 */
data class CirclingDecision(
    val isCircling: Boolean,
    val turnRateRad: Double?,
    val isTurning: Boolean
)

class CirclingDetector {

    private enum class TurnMode {
        CRUISE,
        POSSIBLE_CLIMB,
        CLIMB,
        POSSIBLE_CRUISE
    }

    private val turnRateDeltaTime = DeltaTime()
    private val turningDeltaTime = DeltaTime()
    private var lastTrackRad: Double? = null
    private var isCircling = false
    private var turnMode = TurnMode.CRUISE
    private var turnStartTimeMs: Long = 0L
    private var turnRateSmoothedRad = 0.0

    fun reset() {
        turnRateDeltaTime.reset()
        turningDeltaTime.reset()
        lastTrackRad = null
        isCircling = false
        turnMode = TurnMode.CRUISE
        turnStartTimeMs = 0L
        turnRateSmoothedRad = 0.0
    }

    fun update(trackDegrees: Double, timestampMillis: Long, isFlying: Boolean): CirclingDecision {
        if (!isFlying) {
            reset()
            return CirclingDecision(isCircling = false, turnRateRad = null, isTurning = false)
        }

        val trackRad = Math.toRadians(trackDegrees)
        val turnRate = updateTurnRate(trackRad, timestampMillis)
        val turningDeltaMs = turningDeltaTime.update(
            timestampMillis = timestampMillis,
            minDeltaMs = 0L,
            warpToleranceMs = 0L
        )

        val turning = abs(turnRateSmoothedRad) >= MIN_TURN_RATE_RAD

        if (turningDeltaMs == null || turningDeltaMs <= 0L) {
            return CirclingDecision(
                isCircling = isCircling,
                turnRateRad = turnRate,
                isTurning = turning
            )
        }

        when (turnMode) {
            TurnMode.CRUISE -> {
                if (turning) {
                    turnStartTimeMs = timestampMillis
                    turnMode = TurnMode.POSSIBLE_CLIMB
                }
            }

            TurnMode.POSSIBLE_CLIMB -> {
                if (turning) {
                    if (timestampMillis - turnStartTimeMs > ENTER_THRESHOLD_MS) {
                        isCircling = true
                        turnMode = TurnMode.CLIMB
                    }
                } else {
                    turnMode = TurnMode.CRUISE
                }
            }

            TurnMode.CLIMB -> {
                if (!turning) {
                    turnStartTimeMs = timestampMillis
                    turnMode = TurnMode.POSSIBLE_CRUISE
                }
            }

            TurnMode.POSSIBLE_CRUISE -> {
                if (turning) {
                    turnMode = TurnMode.CLIMB
                } else if (timestampMillis - turnStartTimeMs > EXIT_THRESHOLD_MS) {
                    isCircling = false
                    turnMode = TurnMode.CRUISE
                }
            }
        }

        return CirclingDecision(
            isCircling = isCircling,
            turnRateRad = turnRate,
            isTurning = turning
        )
    }

    private fun updateTurnRate(trackRad: Double, timestampMillis: Long): Double? {
        val dtMs = turnRateDeltaTime.update(
            timestampMillis = timestampMillis,
            minDeltaMs = TURN_RATE_MIN_DELTA_MS,
            warpToleranceMs = TURN_RATE_WARP_TOLERANCE_MS
        )
        if (dtMs == null) {
            lastTrackRad = trackRad
            return null
        }
        if (dtMs < 0) {
            reset()
            lastTrackRad = trackRad
            return null
        }
        if (dtMs == 0L) {
            return null
        }

        val previousTrack = lastTrackRad
        lastTrackRad = trackRad

        if (previousTrack == null) {
            return null
        }

        val dtSeconds = dtMs / 1000.0
        if (dtSeconds <= 0.0) {
            return null
        }

        var delta = trackRad - previousTrack
        while (delta <= -PI) delta += 2 * PI
        while (delta > PI) delta -= 2 * PI
        val rawRate = delta / dtSeconds

        val clamped = rawRate.coerceIn(-MAX_TURN_RATE_RAD, MAX_TURN_RATE_RAD)
        turnRateSmoothedRad = lowPass(turnRateSmoothedRad, clamped, LOW_PASS_ALPHA)

        return rawRate
    }

    companion object {
        // Keep values in degrees for parity; convert at runtime to avoid const-val restrictions.
        private val MIN_TURN_RATE_RAD = Math.toRadians(4.0)
        private val MAX_TURN_RATE_RAD = Math.toRadians(50.0)

        private const val TURN_RATE_MIN_DELTA_MS = 333L
        private const val TURN_RATE_WARP_TOLERANCE_MS = 10_000L

        private const val ENTER_THRESHOLD_MS = 15_000L
        private const val EXIT_THRESHOLD_MS = 10_000L

        private const val LOW_PASS_ALPHA = 0.3
        private const val TIME_WARP_LONG_MS = 4 * 60 * 60 * 1000L

        private fun lowPass(previous: Double, current: Double, alpha: Double): Double =
            (1.0 - alpha) * previous + alpha * current
    }

    private class DeltaTime {
        private var lastTimestampMs: Long? = null

        fun reset() {
            lastTimestampMs = null
        }

        fun update(timestampMillis: Long, minDeltaMs: Long, warpToleranceMs: Long): Long? {
            val last = lastTimestampMs
            if (last == null) {
                lastTimestampMs = timestampMillis
                return null
            }

            val deltaMs = timestampMillis - last
            if (deltaMs < 0) {
                val warp = -deltaMs
                lastTimestampMs = timestampMillis
                return if (warp < warpToleranceMs) 0L else -1L
            }

            if (deltaMs < minDeltaMs) {
                return 0L
            }

            lastTimestampMs = timestampMillis

            if (deltaMs > TIME_WARP_LONG_MS) {
                return -1L
            }

            return deltaMs
        }
    }
}
