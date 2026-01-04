package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.jvm.Volatile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WindEkfUseCase(
    private val maxTurnRateRad: Double = Math.toRadians(20.0),
    private val blackoutDurationMs: Long = 3_000,
    private val minTrueAirspeed: Double = 1.0,
    private val sampleStride: Int = 10
) {

    data class Result(
        val windVector: WindVector,
        val quality: Int,
        val timestampMillis: Long
    )

    enum class DropReason {
        NO_AIRSPEED,
        NOT_INSTRUMENT,
        NO_GPS,
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
    private var lastGpsTimestamp = 0L
    private var lastAirspeedTimestamp = 0L

    @Volatile
    var lastRejectReason: DropReason? = null
        private set

    @Volatile
    var lastRejectTimestamp: Long = 0L
        private set

    fun reset() {
        resetPending = true
        lastTimestamp = 0L
        sampleCount = 0
        blackoutUntil = 0L
        lastGpsTimestamp = 0L
        lastAirspeedTimestamp = 0L
        lastRejectReason = null
        lastRejectTimestamp = 0L
    }

    fun update(
        gps: GpsSample,
        airspeed: AirspeedSample?,
        isCircling: Boolean,
        turnRateRad: Double?,
        gLoad: Double? = null
    ): Result? {
        if (!gps.groundSpeedMs.isFinite() || !gps.trackDeg.isFinite()) {
            recordDrop(DropReason.NO_GPS, gps.timestampMillis)
            return null
        }

        val airspeedSample = airspeed ?: run {
            recordDrop(DropReason.NO_AIRSPEED, gps.timestampMillis)
            resetBlackout()
            return null
        }

        // AI-NOTE: EKF requires instrument airspeed; skip wind-derived TAS to avoid circular wind estimates.
        if (!airspeedSample.isInstrument) {
            recordDrop(DropReason.NOT_INSTRUMENT, gps.timestampMillis)
            resetBlackout()
            return null
        }

        val tas = airspeedSample.trueMs
        if (!tas.isFinite() || tas < minTrueAirspeed) {
            recordDrop(DropReason.NO_TAS, gps.timestampMillis)
            resetBlackout()
            return null
        }

        if (lastTimestamp != 0L && abs(gps.timestampMillis - lastTimestamp) > TIME_WARP_MS) {
            recordDrop(DropReason.TIME_WARP, gps.timestampMillis)
            reset()
        }
        lastTimestamp = gps.timestampMillis

        val gpsUpdated = gps.timestampMillis != lastGpsTimestamp
        val airspeedUpdated = airspeedSample.timestampMillis != lastAirspeedTimestamp
        if (!gpsUpdated || !airspeedUpdated) {
            return null
        }
        lastGpsTimestamp = gps.timestampMillis
        lastAirspeedTimestamp = airspeedSample.timestampMillis

        if (isCircling) {
            sampleCount = 0
            recordDrop(DropReason.CIRCLING, gps.timestampMillis)
            return null
        }

        if (turnRateRad != null && abs(turnRateRad) > maxTurnRateRad) {
            setBlackout(gps.timestampMillis)
            recordDrop(DropReason.TURNING, gps.timestampMillis)
            return null
        }

        if (gLoad != null && abs(gLoad - 1.0) > G_LOAD_THRESHOLD) {
            setBlackout(gps.timestampMillis)
            recordDrop(DropReason.TURNING, gps.timestampMillis)
            return null
        }

        if (inBlackout(gps.timestampMillis)) {
            recordDrop(DropReason.BLACKOUT, gps.timestampMillis)
            return null
        }

        resetBlackout()

        if (resetPending) {
            ekf.reset()
            resetPending = false
        }

        val trackRad = Math.toRadians(gps.trackDeg)
        val groundEast = (gps.groundSpeedMs * sin(trackRad)).toFloat()
        val groundNorth = (gps.groundSpeedMs * cos(trackRad)).toFloat()
        val vector = ekf.update(tas.toFloat(), groundEast, groundNorth) ?: run {
            recordDrop(DropReason.EKF_OUTPUT, gps.timestampMillis)
            return null
        }

        sampleCount++
        if (sampleCount % sampleStride != 0) return null

        val quality = counterToQuality(sampleCount)
        return Result(
            windVector = vector,
            quality = quality,
            timestampMillis = gps.timestampMillis
        )
    }

    private fun recordDrop(reason: DropReason, timestampMillis: Long) {
        lastRejectReason = reason
        lastRejectTimestamp = timestampMillis
    }

    private fun setBlackout(timestampMillis: Long) {
        blackoutUntil = max(blackoutUntil, timestampMillis + blackoutDurationMs)
    }

    private fun resetBlackout() {
        blackoutUntil = 0L
    }

    private fun inBlackout(timestampMillis: Long): Boolean =
        blackoutUntil != 0L && timestampMillis < blackoutUntil

    private fun counterToQuality(counter: Int): Int = when {
        counter >= 600 -> 4
        counter >= 120 -> 3
        counter >= 30 -> 2
        else -> 1
    }

    companion object {
        private const val TIME_WARP_MS = 30_000L
        private const val G_LOAD_THRESHOLD = 0.3
    }
}

