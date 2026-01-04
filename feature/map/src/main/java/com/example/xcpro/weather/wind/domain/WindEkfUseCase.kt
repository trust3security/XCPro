package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WindEkfUseCase(
    private val maxTurnRateRad: Double = Math.toRadians(30.0),
    private val blackoutDurationMs: Long = 1_500,
    private val minTrueAirspeed: Double = 4.5
) {

    data class Result(
        val windVector: WindVector,
        val quality: Int,
        val timestampMillis: Long
    )

    enum class DropReason {
        NO_TAS,
        TIME_WARP,
        CIRCLING,
        TURNING,
        BLACKOUT,
        EKF_OUTPUT
    }

    private val ekf = WindEkf()
    private var resetPending = true
    private var lastTimestamp = 0L
    private var sampleCount = 0
    private var blackoutUntil = 0L
    var lastRejectReason: DropReason? = null
        private set
    var lastRejectTimestamp: Long = 0L
        private set

    fun reset() {
        resetPending = true
        lastTimestamp = 0L
        sampleCount = 0
        blackoutUntil = 0L
        lastRejectReason = null
        lastRejectTimestamp = 0L
    }

    fun update(
        gps: GpsSample,
        airspeed: AirspeedSample?,
        isCircling: Boolean,
        turnRateRad: Double?
    ): Result? {
        val tas = airspeed?.trueMs ?: Double.NaN
        if (airspeed == null || !airspeed.valid || !tas.isFinite() || tas < minTrueAirspeed) {
            recordDrop(DropReason.NO_TAS, gps.timestampMillis)
            resetBlackout()
            return null
        }

        val timestamp = gps.timestampMillis
        if (lastTimestamp != 0L && abs(timestamp - lastTimestamp) > TIME_WARP_MS) {
            recordDrop(DropReason.TIME_WARP, timestamp)
            reset()
        }
        lastTimestamp = timestamp

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
            timestampMillis = timestamp
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
        private const val SAMPLE_STRIDE = 5
        private const val TIME_WARP_MS = 30_000

        private fun counterToQuality(counter: Int): Int = when {
            counter >= 300 -> 4
            counter >= 80 -> 3
            counter >= 20 -> 2
            else -> 1
        }
    }
}
