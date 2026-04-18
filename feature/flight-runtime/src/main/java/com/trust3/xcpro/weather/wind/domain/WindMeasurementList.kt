package com.trust3.xcpro.weather.wind.domain

import com.trust3.xcpro.weather.wind.model.WindSource
import com.trust3.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private data class WindMeasurement(
    val vector: WindVector,
    val quality: Int,
    val clockMillis: Long,
    val altitudeMeters: Double,
    val source: WindSource
)

data class WeightedWind(
    val vector: WindVector,
    val approximateQuality: Int,
    val source: WindSource
)

class WindMeasurementList(
    private val maxMeasurements: Int = 200
) {

    private val measurements = mutableListOf<WindMeasurement>()

    fun addMeasurement(
        clockMillis: Long,
        vector: WindVector,
        altitudeMeters: Double,
        quality: Int,
        source: WindSource
    ) {
        if (measurements.size >= maxMeasurements) {
            val index = leastImportantIndex(clockMillis)
            measurements.removeAt(index)
        }
        measurements.add(
            WindMeasurement(
                vector = vector,
                quality = quality,
                clockMillis = clockMillis,
                altitudeMeters = altitudeMeters,
                source = source
            )
        )
    }

    fun getWeightedWind(currentClockMillis: Long, altitudeMeters: Double): WeightedWind? {
        if (measurements.isEmpty()) return null

        var totalWeight = 0.0
        var weightedEast = 0.0
        var weightedNorth = 0.0
        var weightedQuality = 0.0
        var dominantSource: WindSource = WindSource.NONE
        var dominantWeight = Double.NEGATIVE_INFINITY

        var overrideTime = 1.1
        var overridden = false

        for (measurement in measurements) {
            if (currentClockMillis < measurement.clockMillis) {
                continue
            }

            val timeDiff =
                (currentClockMillis - measurement.clockMillis).toDouble() / TIME_RANGE_MILLIS
            if (timeDiff >= 1.0) {
                continue
            }

            val altDiff = abs(altitudeMeters - measurement.altitudeMeters) / ALTITUDE_RANGE_METERS
            if (altDiff >= 1.0) {
                continue
            }

            if (measurement.quality == 6) {
                if (timeDiff < overrideTime) {
                    overrideTime = timeDiff
                    totalWeight = 0.0
                    weightedEast = 0.0
                    weightedNorth = 0.0
                    weightedQuality = 0.0
                    overridden = true
                } else {
                    continue
                }
            } else {
                if (timeDiff < overrideTime) {
                    overrideTime = timeDiff
                    if (overridden) {
                        overridden = false
                        totalWeight = 0.0
                        weightedEast = 0.0
                        weightedNorth = 0.0
                        weightedQuality = 0.0
                    }
                }
            }

            val qualityFactor =
                (min(5, measurement.quality) * REL_FACTOR_QUALITY) / 5.0
            val altitudeFactor =
                ((2.0 / (altDiff * altDiff + 1.0)) - 1.0) * REL_FACTOR_ALTITUDE
            val timeFactor =
                K_FACTOR * (1 - timeDiff) / (timeDiff * timeDiff + K_FACTOR) * REL_FACTOR_TIME

            val combinedWeight = qualityFactor * (altitudeFactor * timeFactor)
            if (combinedWeight <= 0.0) continue

            weightedEast += measurement.vector.east * combinedWeight
            weightedNorth += measurement.vector.north * combinedWeight
            weightedQuality += measurement.quality * combinedWeight
            totalWeight += combinedWeight
            if (combinedWeight > dominantWeight) {
                dominantWeight = combinedWeight
                dominantSource = measurement.source
            }
        }

        if (totalWeight <= 0.0) {
            return null
        }

        val vector = WindVector(
            east = weightedEast / totalWeight,
            north = weightedNorth / totalWeight
        )

        val approxQuality = (weightedQuality / totalWeight).roundToIntBounded(1, 5)
        return WeightedWind(vector, approxQuality, dominantSource)
    }

    private fun leastImportantIndex(nowClockMillis: Long): Int {
        var maxScore = Double.NEGATIVE_INFINITY
        var targetIndex = measurements.lastIndex
        for ((index, measurement) in measurements.withIndex()) {
            val ageSeconds = max(0L, nowClockMillis - measurement.clockMillis) / 1000.0
            val score = 600.0 * (6 - measurement.quality) + ageSeconds
            if (score > maxScore) {
                maxScore = score
                targetIndex = index
            }
        }
        return targetIndex
    }

    fun reset() {
        measurements.clear()
    }

    companion object {
        private const val REL_FACTOR_QUALITY = 100.0
        private const val REL_FACTOR_ALTITUDE = 100.0
        private const val REL_FACTOR_TIME = 200.0
        private const val ALTITUDE_RANGE_METERS = 1000.0
        private const val TIME_RANGE_MILLIS = 3_600_000.0 // 1 hour
        private const val K_FACTOR = 0.0025
    }
}

private fun Double.roundToIntBounded(minValue: Int, maxValue: Int): Int {
    val rounded = kotlin.math.round(this).toInt()
    return rounded.coerceIn(minValue, maxValue)
}
