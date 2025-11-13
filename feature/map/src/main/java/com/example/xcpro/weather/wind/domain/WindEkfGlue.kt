package com.example.xcpro.weather.wind.domain

import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class WindEkfGlue(
    private val maxTurnRateRad: Double = Math.toRadians(20.0),
    private val blackoutDurationMs: Long = 3_000,
    private val minTrueAirspeed: Double = 5.0
) {

    data class Result(
        val windVector: WindVector,
        val quality: Int,
        val timestampMillis: Long
    )

    private val ekf = WindEkf()
    private var resetPending = true
    private var lastTimestamp = 0L
    private var sampleCount = 0
    private var blackoutUntil = 0L

    fun reset() {
        resetPending = true
        lastTimestamp = 0L
        sampleCount = 0
        blackoutUntil = 0L
    }

    fun update(
        sample: CompleteFlightData,
        isCircling: Boolean,
        turnRateRad: Double?
    ): Result? {
        val gps = sample.gps ?: return null
        val timestamp = gps.timestamp
        val tas = sample.trueAirspeed
        if (!tas.isFinite() || tas < minTrueAirspeed) {
            resetBlackout()
            return null
        }

        if (lastTimestamp != 0L && abs(timestamp - lastTimestamp) > TIME_WARP_MS) {
            reset()
        }
        lastTimestamp = timestamp

        if (isCircling) {
            sampleCount = 0
            setBlackout(timestamp)
            return null
        }

        if (turnRateRad != null && abs(turnRateRad) > maxTurnRateRad) {
            setBlackout(timestamp)
            return null
        }

        if (inBlackout(timestamp)) return null

        if (resetPending) {
            ekf.reset()
            resetPending = false
        }

        val trackRad = Math.toRadians(gps.bearing)
        val groundEast = (gps.speed * sin(trackRad)).toFloat()
        val groundNorth = (gps.speed * cos(trackRad)).toFloat()
        val vector = ekf.update(tas.toFloat(), groundEast, groundNorth) ?: return null

        sampleCount++
        if (sampleCount % SAMPLE_STRIDE != 0) return null

        val quality = counterToQuality(sampleCount)
        return Result(
            windVector = vector,
            quality = quality,
            timestampMillis = timestamp
        )
    }

    private fun setBlackout(timestamp: Long) {
        blackoutUntil = max(blackoutUntil, timestamp + blackoutDurationMs)
    }

    private fun resetBlackout() {
        blackoutUntil = 0L
    }

    private fun inBlackout(timestamp: Long): Boolean =
        blackoutUntil != 0L && timestamp < blackoutUntil

    companion object {
        private const val SAMPLE_STRIDE = 10
        private const val TIME_WARP_MS = 30_000

        private fun counterToQuality(counter: Int): Int = when {
            counter >= 600 -> 4
            counter >= 120 -> 3
            counter >= 30 -> 2
            else -> 1
        }
    }
}
