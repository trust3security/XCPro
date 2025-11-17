package com.example.xcpro.weather.wind.domain

import android.util.Log
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.jvm.Volatile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WindEkfGlue(
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

    @Volatile
    var lastRejectReason: DropReason? = null
        private set

    @Volatile
    var lastRejectTimestamp: Long = 0L
        private set

    private var lastLoggedReason: DropReason? = null
    private var lastLogTime: Long = 0L

    fun reset() {
        resetPending = true
        lastTimestamp = 0L
        sampleCount = 0
        blackoutUntil = 0L
        lastRejectReason = null
        lastRejectTimestamp = 0L
        lastLoggedReason = null
        lastLogTime = 0L
    }

    fun update(
        sample: CompleteFlightData,
        isCircling: Boolean,
        turnRateRad: Double?
    ): Result? {
        val gps = sample.gps ?: run {
            logDrop(DropReason.NO_GPS, sample.timestamp, "gps=null")
            return null
        }
        val timestamp = gps.timestamp
        val tas = sample.trueAirspeed.value
        if (!tas.isFinite() || tas < minTrueAirspeed) {
            logDrop(DropReason.NO_TAS, timestamp, "tas=$tas")
            resetBlackout()
            return null
        }

        if (lastTimestamp != 0L && abs(timestamp - lastTimestamp) > TIME_WARP_MS) {
            logDrop(DropReason.TIME_WARP, timestamp, "delta=${abs(timestamp - lastTimestamp)}ms")
            reset()
        }
        lastTimestamp = timestamp

        if (isCircling) {
            sampleCount = (sampleCount * 0.5).toInt()
            setBlackout(timestamp, DropReason.CIRCLING, "sustained circling")
            return null
        }

        if (turnRateRad != null && abs(turnRateRad) > maxTurnRateRad) {
            sampleCount = (sampleCount * 0.8).toInt()
            setBlackout(timestamp, DropReason.TURNING, "turn=${Math.toDegrees(turnRateRad)}deg/s")
            return null
        }

        if (inBlackout(timestamp)) {
            logDrop(DropReason.BLACKOUT, timestamp, "cooldown=${blackoutUntil - timestamp}ms")
            return null
        }

        if (resetPending) {
            ekf.reset()
            resetPending = false
        }

        val trackRad = Math.toRadians(gps.bearing)
        val groundEast = (gps.speed.value * sin(trackRad)).toFloat()
        val groundNorth = (gps.speed.value * cos(trackRad)).toFloat()
        val vector = ekf.update(tas.toFloat(), groundEast, groundNorth) ?: run {
            logDrop(DropReason.EKF_OUTPUT, timestamp, "ekf returned null")
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

    private fun setBlackout(timestamp: Long, reason: DropReason, detail: String) {
        blackoutUntil = max(blackoutUntil, timestamp + blackoutDurationMs)
        logDrop(reason, timestamp, "$detail (until=$blackoutUntil)")
    }

    private fun resetBlackout() {
        blackoutUntil = 0L
    }

    private fun inBlackout(timestamp: Long): Boolean =
        blackoutUntil != 0L && timestamp < blackoutUntil

    companion object {
        private const val SAMPLE_STRIDE = 5
        private const val TIME_WARP_MS = 30_000
        private const val LOG_INTERVAL_MS = 2_000L
        private const val TAG = "WindEkfGlue"

        private fun counterToQuality(counter: Int): Int = when {
            counter >= 300 -> 4
            counter >= 80 -> 3
            counter >= 20 -> 2
            else -> 1
        }
    }

    private fun logDrop(reason: DropReason, timestamp: Long, detail: String) {
        lastRejectReason = reason
        lastRejectTimestamp = timestamp
        if (reason != lastLoggedReason || timestamp - lastLogTime >= LOG_INTERVAL_MS) {
            Log.d(TAG, "Rejected sample: reason=$reason detail=$detail")
            lastLoggedReason = reason
            lastLogTime = timestamp
        }
    }
}
