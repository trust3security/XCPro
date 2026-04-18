package com.trust3.xcpro.sensors.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Determines whether the aircraft is flying based on speed, time and altitude cues.
 *
 * This mirrors legacy takeoff/landing gating logic (timers, airspeed handling,
 * AGL override, and altitude-based takeoff-speed reduction).
 */
class FlyingStateDetector(
    private val defaultTakeoffSpeedMs: Double = DEFAULT_TAKEOFF_SPEED_MS
) {
    private val deltaTime = DeltaTime()
    private val movingClock = StateClock(maxValueMs = MOVING_CLOCK_MAX_MS, maxDeltaMs = CLOCK_MAX_DELTA_MS)
    private val stationaryClock = StateClock(maxValueMs = STATIONARY_CLOCK_MAX_MS, maxDeltaMs = CLOCK_MAX_DELTA_MS)
    private val climbingClock = StateClock(maxValueMs = CLIMBING_CLOCK_MAX_MS, maxDeltaMs = CLOCK_MAX_DELTA_MS)

    private var isFlying = false
    private var movingSinceMs: Long? = null
    private var stationarySinceMs: Long? = null
    private var lastGroundAltitudeMeters = Double.NaN
    private var climbingAltitudeMeters = Double.NaN

    fun reset() {
        deltaTime.reset()
        movingClock.reset()
        stationaryClock.reset()
        climbingClock.reset()
        isFlying = false
        movingSinceMs = null
        stationarySinceMs = null
        lastGroundAltitudeMeters = Double.NaN
        climbingAltitudeMeters = Double.NaN
    }

    fun update(
        timestampMillis: Long,
        groundSpeedMs: Double,
        trueAirspeedMs: Double?,
        airspeedReal: Boolean,
        altitudeMeters: Double?,
        aglMeters: Double?
    ): FlyingState {
        val dtMs = deltaTime.update(
            timestampMillis = timestampMillis,
            minDeltaMs = MIN_UPDATE_DELTA_MS,
            warpToleranceMs = WARP_TOLERANCE_MS
        )
        if (dtMs == null) {
            return currentState()
        }
        if (dtMs < 0) {
            reset()
            return currentState()
        }
        if (dtMs == 0L) {
            return currentState()
        }

        val airspeedAvailable = trueAirspeedMs != null && trueAirspeedMs.isFinite()
        val altitude = altitudeMeters?.takeIf { it.isFinite() }
        val agl = aglMeters?.takeIf { it.isFinite() }

        var takeoffSpeed = defaultTakeoffSpeedMs

        if (!airspeedAvailable && agl == null && altitude != null && lastGroundAltitudeMeters.isFinite()) {
            val deltaAlt = altitude - lastGroundAltitudeMeters
            if (deltaAlt > ALTITUDE_REDUCTION_TRIGGER_M) {
                takeoffSpeed = when {
                    deltaAlt > ALTITUDE_REDUCTION_HIGH_M -> takeoffSpeed / 4.0
                    deltaAlt > ALTITUDE_REDUCTION_MED_M -> takeoffSpeed / 2.0
                    else -> takeoffSpeed * 2.0 / 3.0
                }
            }
        }

        val takeoffDetected = checkTakeoffSpeed(
            takeoffSpeed = takeoffSpeed,
            groundSpeedMs = groundSpeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedAvailable = airspeedAvailable,
            airspeedReal = airspeedReal
        ) || checkAltitudeAgl(agl)

        if (takeoffDetected) {
            moving(dtMs, timestampMillis)
        } else if (!isFlying || (checkLandingSpeed(takeoffSpeed, groundSpeedMs, trueAirspeedMs, airspeedAvailable, airspeedReal)
                && !checkClimbing(dtMs, altitude))) {
            stationary(dtMs, timestampMillis)
        }

        val onGround = !isFlying && stationaryClock.isActive(ON_GROUND_CONFIRM_MS)
        if (onGround && altitude != null) {
            lastGroundAltitudeMeters = altitude
        }

        return currentState(onGroundOverride = onGround)
    }

    private fun moving(dtMs: Long, timestampMillis: Long) {
        movingClock.add(dtMs)
        if (movingSinceMs == null) {
            movingSinceMs = timestampMillis
        }
        stationaryClock.reset()
        stationarySinceMs = null
        updateFlyingState(timestampMillis)
    }

    private fun stationary(dtMs: Long, timestampMillis: Long) {
        movingClock.subtract(dtMs)
        stationaryClock.add(dtMs)
        if (stationarySinceMs == null) {
            stationarySinceMs = timestampMillis
        }
        updateFlyingState(timestampMillis)
    }

    private fun updateFlyingState(timestampMillis: Long) {
        if (!isFlying) {
            if (movingClock.isActive(TAKEOFF_CONFIRM_MS)) {
                isFlying = true
                // keep timestamps internal for future extensions; current consumers only need the flag
            }
        } else {
            if (!movingClock.isDefined()) {
                isFlying = false
                // landing time tracked implicitly via stationarySinceMs
            }
        }
    }

    private fun checkClimbing(dtMs: Long, altitudeMeters: Double?): Boolean {
        val altitude = altitudeMeters ?: return false
        if (!climbingAltitudeMeters.isFinite()) {
            climbingAltitudeMeters = altitude
        }
        if (altitude > climbingAltitudeMeters + CLIMBING_ALTITUDE_EPS_M) {
            climbingClock.add(dtMs)
        } else {
            climbingClock.subtract(dtMs)
        }
        climbingAltitudeMeters = altitude
        return climbingClock.isActive(dtMs + CLIMBING_CONFIRM_MS)
    }

    private fun checkAltitudeAgl(aglMeters: Double?): Boolean =
        aglMeters != null && aglMeters >= MIN_AGL_FLYING_M

    private fun checkTakeoffSpeed(
        takeoffSpeed: Double,
        groundSpeedMs: Double,
        trueAirspeedMs: Double?,
        airspeedAvailable: Boolean,
        airspeedReal: Boolean
    ): Boolean {
        val speed = if (airspeedAvailable) {
            val trueSpeed = trueAirspeedMs ?: 0.0
            if (airspeedReal || groundSpeedMs >= takeoffSpeed / 4.0) {
                max(trueSpeed, groundSpeedMs)
            } else {
                (trueSpeed + groundSpeedMs) / 2.0
            }
        } else {
            groundSpeedMs
        }
        return speed >= takeoffSpeed
    }

    private fun checkLandingSpeed(
        takeoffSpeed: Double,
        groundSpeedMs: Double,
        trueAirspeedMs: Double?,
        airspeedAvailable: Boolean,
        airspeedReal: Boolean
    ): Boolean = !checkTakeoffSpeed(
        takeoffSpeed = takeoffSpeed / 2.0,
        groundSpeedMs = groundSpeedMs,
        trueAirspeedMs = trueAirspeedMs,
        airspeedAvailable = airspeedAvailable,
        airspeedReal = airspeedReal
    )

    private fun currentState(onGroundOverride: Boolean? = null): FlyingState =
        FlyingState(
            isFlying = isFlying,
            onGround = onGroundOverride ?: (!isFlying && stationaryClock.isActive(ON_GROUND_CONFIRM_MS))
        )

    private class StateClock(
        private val maxValueMs: Long,
        private val maxDeltaMs: Long
    ) {
        private var valueMs: Long = 0L

        fun reset() {
            valueMs = 0L
        }

        fun isDefined(): Boolean = valueMs > 0L

        fun isActive(thresholdMs: Long): Boolean = valueMs >= thresholdMs

        fun add(deltaMs: Long) {
            val clamped = min(deltaMs, maxDeltaMs)
            valueMs = min(maxValueMs, valueMs + clamped)
        }

        fun subtract(deltaMs: Long) {
            val clamped = min(deltaMs, maxDeltaMs)
            valueMs = max(0L, valueMs - clamped)
        }
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

    private companion object {
        // Fallback until polar wiring exists.
        private const val DEFAULT_TAKEOFF_SPEED_MS = 10.0

        private const val MIN_UPDATE_DELTA_MS = 500L
        private const val WARP_TOLERANCE_MS = 20_000L
        private const val TIME_WARP_LONG_MS = 4 * 60 * 60 * 1000L

        private const val MOVING_CLOCK_MAX_MS = 30_000L
        private const val STATIONARY_CLOCK_MAX_MS = 60_000L
        private const val CLIMBING_CLOCK_MAX_MS = 20_000L
        private const val CLOCK_MAX_DELTA_MS = 5_000L

        private const val TAKEOFF_CONFIRM_MS = 10_000L
        private const val ON_GROUND_CONFIRM_MS = 10_000L

        private const val CLIMBING_ALTITUDE_EPS_M = 0.1
        private const val CLIMBING_CONFIRM_MS = 1_000L

        private const val MIN_AGL_FLYING_M = 300.0
        private const val ALTITUDE_REDUCTION_TRIGGER_M = 250.0
        private const val ALTITUDE_REDUCTION_MED_M = 500.0
        private const val ALTITUDE_REDUCTION_HIGH_M = 1000.0
    }
}
