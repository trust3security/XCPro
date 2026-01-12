package com.example.xcpro.weather.wind

import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindResult
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CirclingWindTest {

    @Test
    fun `circling estimator recovers injected wind`() {
        val estimator = CirclingWind()
        val samples = generateCircularSamples()

        var finalResult: CirclingWindResult? = null
        for (sample in samples) {
            val newResult = estimator.addSample(sample)
            if (newResult != null) {
                finalResult = newResult
            }
        }

        val result = finalResult
        val debugInfo = "state=${estimator.debugState()} analysis=${estimator.debugAnalysis()}"
        assertNotNull("Expected circling estimator to emit a result ($debugInfo)", result)
        val expectedEast = TEST_WIND_EAST
        val expectedNorth = TEST_WIND_NORTH
        val actual = result!!.windVector

        assertTrue("East component mismatch", abs(actual.east - expectedEast) < 0.5)
        assertTrue("North component mismatch", abs(actual.north - expectedNorth) < 0.5)
        assertTrue("Expected usable quality result", result.quality >= 2)
    }

    @Test
    fun `circling estimator resets immediately when circling stops`() {
        val estimator = CirclingWind()
        val samples = generateCircularSamples().take(6)
        samples.forEach { estimator.addSample(it) }

        val sizeBefore = estimator.sampleSize()
        assertTrue("Expected samples before reset", sizeBefore > 0)

        val lastTimestamp = samples.last().clockMillis
        estimator.addSample(
            CirclingWindSample(
                clockMillis = lastTimestamp + SAMPLE_INTERVAL_MS,
                trackRad = 0.0,
                groundSpeed = 10.0,
                isCircling = false
            )
        )

        assertTrue("Expected samples cleared after circling=false", estimator.sampleSize() == 0)
        assertTrue("Expected circleCount reset", estimator.circleCount() == 0)
        assertTrue("Expected currentCircle reset", estimator.currentCircle() == 0.0)
    }

    private fun CirclingWind.debugState(): String {
        val circleCount: Int = getFieldValue("circleCount")
        val currentCircle: Double = getFieldValue("currentCircle")
        val samples: ArrayDeque<*> = getFieldValue("samples")
        val lastClockMillis: Long = getFieldValue("lastClockMillis")
        return "circleCount=$circleCount currentCircle=$currentCircle sampleSize=${samples.size} lastClockMillis=$lastClockMillis"
    }

    private fun CirclingWind.debugAnalysis(): String {
        val samples: List<SampleSnapshot> = snapshotSamples()
        val circleCount: Int = getFieldValue("circleCount")
        val maxSampleSpacingMs: Long = getFieldValue("maxSampleSpacingMs")
        val maxWindSpeed: Double = getFieldValue("maxWindSpeed")

        if (samples.size < 2) {
            return "analysis: insufficient samples ${samples.size}"
        }

        val spanMs = samples.last().clockMillis - samples.first().clockMillis
        if (spanMs <= 0) {
            return "analysis: non-positive span"
        }

        val avgSpacing = spanMs.toDouble() / (samples.size - 1)
        if (avgSpacing > maxSampleSpacingMs) {
            return "analysis: spacing=$avgSpacing"
        }

        val averageSpeed = samples.map { it.groundSpeed }.average()
        val size = samples.size
        var rMax = 0.0
        var rMin = 0.0
        var idxMax = -1
        var idxMin = -1
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
            return "analysis: idx invalid idxMax=$idxMax idxMin=$idxMin"
        }

        val magnitude = (samples[idxMax].groundSpeed - samples[idxMin].groundSpeed) / 2.0
        if (magnitude >= maxWindSpeed) {
            return "analysis: magnitude too high $magnitude"
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

        return "analysis: samples=$size circleCount=$circleCount magnitude=$magnitude residual=$residual quality=$quality idxMax=$idxMax idxMin=$idxMin avgSpacing=$avgSpacing"
    }

    private fun CirclingWind.sampleSize(): Int {
        val samples: ArrayDeque<*> = getFieldValue("samples")
        return samples.size
    }

    private fun CirclingWind.circleCount(): Int = getFieldValue("circleCount")

    private fun CirclingWind.currentCircle(): Double = getFieldValue("currentCircle")

    private fun CirclingWind.snapshotSamples(): List<SampleSnapshot> {
        val samples: ArrayDeque<*> = getFieldValue("samples")
        val list = mutableListOf<SampleSnapshot>()
        for (sample in samples) {
            if (sample != null) {
                list += SampleSnapshot(
                    clockMillis = sample.readLongField("clockMillis"),
                    trackRad = sample.readDoubleField("trackRad"),
                    groundSpeed = sample.readDoubleField("groundSpeed")
                )
            }
        }
        return list
    }

    private inline fun <reified T> CirclingWind.getFieldValue(name: String): T {
        val field = CirclingWind::class.java.getDeclaredField(name)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as T
    }

    private fun Any.readDoubleField(name: String): Double {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return (field.get(this) as Number).toDouble()
    }

    private fun Any.readLongField(name: String): Long {
        val field = javaClass.getDeclaredField(name)
        field.isAccessible = true
        return (field.get(this) as Number).toLong()
    }

    private data class SampleSnapshot(
        val clockMillis: Long,
        val trackRad: Double,
        val groundSpeed: Double
    )

    private fun generateCircularSamples(): List<CirclingWindSample> {
        val samples = mutableListOf<CirclingWindSample>()
        var timestamp = 0L
        val totalSteps = CIRCLES * STEPS_PER_CIRCLE
        for (step in 0..totalSteps) {
            val angle = (step % STEPS_PER_CIRCLE).toDouble() / STEPS_PER_CIRCLE * 2 * PI
            val airEast = TAS * sin(angle)
            val airNorth = TAS * cos(angle)
            val groundEast = airEast + TEST_WIND_EAST
            val groundNorth = airNorth + TEST_WIND_NORTH
            val groundSpeed = hypot(groundEast, groundNorth)
            val trackRad = atan2(groundEast, groundNorth)

            samples += CirclingWindSample(
                clockMillis = timestamp,
                trackRad = trackRad,
                groundSpeed = groundSpeed,
                isCircling = true
            )

            timestamp += SAMPLE_INTERVAL_MS
        }
        return samples
    }

    companion object {
        private const val TAS = 30.0
        private const val TEST_WIND_EAST = 5.0
        private const val TEST_WIND_NORTH = -2.0
        private const val STEPS_PER_CIRCLE = 36
        private const val CIRCLES = 3
        private const val SAMPLE_INTERVAL_MS = 500L
    }
}



