package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WindEkfUseCase(
    private val maxTurnRateRad: Double = Math.toRadians(20.0),
    private val blackoutDurationMs: Long = 3_000,
    private val minTrueAirspeed: Double = DEFAULT_TAKEOFF_SPEED_MS
) {

    data class Result(
        val windVector: WindVector,
        val quality: Int,
        val clockMillis: Long
    )

    enum class DropReason {
        NO_TAS,
        TIME_WARP,
        NO_UPDATE,
        CIRCLING,
        TURNING,
        G_LOAD,
        BLACKOUT,
        EKF_OUTPUT
    }

    private val ekf = WindEkf()
    private var resetPending = true
    private var lastClockMillis = 0L
    private var lastGpsClockMillis = UNSET_TIMESTAMP
    private var lastAirspeedClockMillis = UNSET_TIMESTAMP
    private var sampleCount = 0
    private var blackoutUntil = 0L
    var lastRejectReason: DropReason? = null
        private set
    var lastRejectTimestamp: Long = 0L
        private set

    fun reset() {
        resetPending = true
        lastClockMillis = 0L
        lastGpsClockMillis = UNSET_TIMESTAMP
        lastAirspeedClockMillis = UNSET_TIMESTAMP
        sampleCount = 0
        blackoutUntil = 0L
        lastRejectReason = null
        lastRejectTimestamp = 0L
    }

    fun update(
        gps: GpsSample,
        airspeed: AirspeedSample?,
        isCircling: Boolean,
        turnRateRad: Double?,
        gLoad: GLoadSample?
    ): Result? {
        val tas = airspeed?.trueMs ?: Double.NaN
        if (airspeed == null || !airspeed.valid || !tas.isFinite() || tas < minTrueAirspeed) {
            recordDrop(DropReason.NO_TAS, gps.clockMillis)
            resetBlackout()
            return null
        }

        val timestamp = gps.clockMillis
        val airspeedTimestamp = airspeed.clockMillis
        if (airspeedTimestamp <= 0L) {
            recordDrop(DropReason.NO_UPDATE, timestamp)
            resetBlackout()
            return null
        }
        if (lastClockMillis != 0L && abs(timestamp - lastClockMillis) > TIME_WARP_MS) {
            recordDrop(DropReason.TIME_WARP, timestamp)
            reset()
        }
        lastClockMillis = timestamp
        val hasLastGps = lastGpsClockMillis != UNSET_TIMESTAMP
        val hasLastAirspeed = lastAirspeedClockMillis != UNSET_TIMESTAMP
        if ((hasLastGps && timestamp < lastGpsClockMillis) ||
            (hasLastAirspeed && airspeedTimestamp < lastAirspeedClockMillis)) {
            recordDrop(DropReason.TIME_WARP, timestamp)
            reset()
        }
        if ((hasLastGps && timestamp == lastGpsClockMillis) ||
            (hasLastAirspeed && airspeedTimestamp == lastAirspeedClockMillis)) {
            recordDrop(DropReason.NO_UPDATE, timestamp)
            return null
        }
        lastGpsClockMillis = timestamp
        lastAirspeedClockMillis = airspeedTimestamp

        if (isCircling) {
            sampleCount = (sampleCount * 0.5).toInt()
            setBlackout(timestamp, DropReason.CIRCLING)
            return null
        }

        if (turnRateRad != null && abs(turnRateRad) > maxTurnRateRad) {
            sampleCount = (sampleCount * 0.8).toInt()
            setBlackout(timestamp, DropReason.TURNING)
            return null
        }

        if (gLoad != null && gLoad.isReliable) {
            val gLoadAgeMs = abs(timestamp - gLoad.clockMillis)
            if (gLoadAgeMs <= G_LOAD_FRESHNESS_MS && abs(gLoad.gLoad - 1.0) > G_LOAD_THRESHOLD) {
                setBlackout(timestamp, DropReason.G_LOAD)
                return null
            }
        }

        if (inBlackout(timestamp)) {
            recordDrop(DropReason.BLACKOUT, timestamp)
            return null
        }

        if (resetPending) {
            ekf.reset()
            resetPending = false
        }

        val trackRad = gps.trackRad
        if (!trackRad.isFinite() || !gps.groundSpeedMs.isFinite()) {
            recordDrop(DropReason.EKF_OUTPUT, timestamp)
            return null
        }

        val groundEast = (gps.groundSpeedMs * sin(trackRad)).toFloat()
        val groundNorth = (gps.groundSpeedMs * cos(trackRad)).toFloat()
        val vector = ekf.update(tas.toFloat(), groundEast, groundNorth) ?: run {
            recordDrop(DropReason.EKF_OUTPUT, timestamp)
            return null
        }

        sampleCount++
        if (sampleCount % SAMPLE_STRIDE != 0) return null

        val quality = counterToQuality(sampleCount)
        return Result(
            windVector = vector,
            quality = quality,
            clockMillis = timestamp
        )
    }

    private fun setBlackout(timestamp: Long, reason: DropReason) {
        blackoutUntil = max(blackoutUntil, timestamp + blackoutDurationMs)
        recordDrop(reason, timestamp)
    }

    private fun resetBlackout() {
        blackoutUntil = 0L
    }

    private fun inBlackout(timestamp: Long): Boolean =
        blackoutUntil != 0L && timestamp < blackoutUntil

    private fun recordDrop(reason: DropReason, timestamp: Long) {
        lastRejectReason = reason
        lastRejectTimestamp = timestamp
    }

    companion object {
        // Fallback until polar VTakeoff wiring exists.
        private const val DEFAULT_TAKEOFF_SPEED_MS = 10.0
        private const val UNSET_TIMESTAMP = Long.MIN_VALUE
        private const val SAMPLE_STRIDE = 10
        private const val TIME_WARP_MS = 30_000
        private const val G_LOAD_THRESHOLD = 0.3
        private const val G_LOAD_FRESHNESS_MS = 500L

        private fun counterToQuality(counter: Int): Int = when {
            counter >= 600 -> 4
            counter >= 120 -> 3
            counter >= 30 -> 2
            else -> 1
        }
    }
}
