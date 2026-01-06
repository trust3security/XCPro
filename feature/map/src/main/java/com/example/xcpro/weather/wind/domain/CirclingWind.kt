package com.example.xcpro.weather.wind.domain

import com.example.xcpro.weather.wind.model.WindVector
import com.example.xcpro.weather.wind.model.normalizeRadians
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

data class CirclingWindSample(
    val timestampMillis: Long,
    val trackRad: Double,
    val groundSpeed: Double,
    val isCircling: Boolean
)

data class CirclingWindResult(
    val quality: Int,
    val windVector: WindVector,
    val timestampMillis: Long
)

/**
 * Kotlin port of XCSoar's CirclingWind estimator (Cumulus origin).
 */
class CirclingWind(
    private val maxSamples: Int = 50,
    private val maxSampleSpacingMs: Long = 2_000,
    private val maxWindSpeed: Double = 30.0
) {

    private data class Sample(
        val timestamp: Long,
        val trackRad: Double,
        val groundSpeed: Double
    )

    private val samples = ArrayDeque<Sample>()
    private var active = false
    private var circleCount = 0
    private var currentCircle = 0.0
    private var lastTrackRad: Double? = null
    private var lastTimestamp: Long = 0L

    fun reset() {
        active = false
        circleCount = 0
        currentCircle = 0.0
        lastTrackRad = null
        lastTimestamp = 0L
        samples.clear()
    }

    fun addSample(sample: CirclingWindSample): CirclingWindResult? {
        if (!sample.isCircling) {
            reset()
            return null
        }

        if (!active) {
            active = true
            circleCount = 0
            currentCircle = 0.0
            samples.clear()
        }

        if (lastTimestamp != 0L && sample.timestampMillis - lastTimestamp > TIME_WARP_MS) {
            reset()
            active = true
        }

        lastTimestamp = sample.timestampMillis

        lastTrackRad?.let { previous ->
            currentCircle += abs(angularDifference(sample.trackRad, previous))
        }
        lastTrackRad = sample.trackRad

        val fullCircle = currentCircle >= 2 * PI
        if (fullCircle) {
            currentCircle -= 2 * PI
            circleCount++
        }

        if (samples.size >= maxSamples) {
            samples.removeFirst()
        }
        samples.addLast(
            Sample(
                timestamp = sample.timestampMillis,
                trackRad = sample.trackRad,
                groundSpeed = sample.groundSpeed
            )
        )

        if (!fullCircle) {
            return null
        }

        val result = calculateWind()
        samples.clear()
        return result
    }

    private fun calculateWind(): CirclingWindResult? {
        if (samples.size < MIN_SAMPLES || circleCount == 0) {
            return null
        }

        val spanMs = samples.last().timestamp - samples.first().timestamp
        if (spanMs <= 0 || (spanMs / (samples.size - 1)) > maxSampleSpacingMs) {
            return null
        }

        val averageSpeed = samples.map { it.groundSpeed }.average()

        var rMax = 0.0
        var rMin = 0.0
        var idxMax = -1
        var idxMin = -1

        val size = samples.size
        for (j in 0 until size) {
            var accumulator = 0.0
            for (i in 1 until size) {
                val ithis = (i + j) % size
                var idiff = i
                if (idiff > size / 2) {
                    idiff = size - idiff
                }
                accumulator += samples[ithis].groundSpeed * idiff
            }

            if (accumulator < rMax || idxMax == -1) {
                rMax = accumulator
                idxMax = j
            }

            if (accumulator > rMin || idxMin == -1) {
                rMin = accumulator
                idxMin = j
            }
        }

        if (idxMax == -1 || idxMin == -1) {
            return null
        }

        val magnitude = (samples[idxMax].groundSpeed - samples[idxMin].groundSpeed) / 2.0
        if (magnitude >= maxWindSpeed) {
            return null
        }

        var residual = 0.0
        for (sample in samples) {
            val sin = sin(sample.trackRad)
            val cos = cos(sample.trackRad)
            val wx = cos * averageSpeed + magnitude
            val wy = sin * averageSpeed
            val predicted = hypot(wx, wy)
            val diff = predicted - sample.groundSpeed
            residual += diff * diff
        }
        residual = kotlin.math.sqrt(residual / size)

        var quality = if (magnitude > 1.0) {
            5 - ((residual / magnitude) * 3.0).roundToInt()
        } else {
            5 - residual.roundToInt()
        }

        if (circleCount < 3) quality--
        if (circleCount < 2) quality--

        if (quality < 1) {
            return null
        }
        if (quality > 5) quality = 5

        val windBearingFrom = normalizeRadians(samples[idxMax].trackRad + PI)
        val windVector = WindVector.fromSpeedAndBearing(magnitude, windBearingFrom)
        val timestamp = samples[idxMax].timestamp
        return CirclingWindResult(
            quality = quality,
            windVector = windVector,
            timestampMillis = timestamp
        )
    }

    private fun angularDifference(a: Double, b: Double): Double {
        var diff = a - b
        while (diff <= -PI) diff += 2 * PI
        while (diff > PI) diff -= 2 * PI
        return diff
    }

    companion object {
        private const val MIN_SAMPLES = 12
        private const val TIME_WARP_MS = 30_000L
    }
}
