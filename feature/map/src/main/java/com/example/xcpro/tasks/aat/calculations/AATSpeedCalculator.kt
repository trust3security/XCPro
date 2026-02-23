package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATResult
import java.time.Duration

/**
 * Calculator for speed-related calculations in AAT tasks.
 * Internal speed contracts are SI (m/s).
 */
class AATSpeedCalculator {
    /**
     * Calculate AAT speed according to official rules.
     * Speed = distance / MAX(elapsed_time, minimum_time).
     *
     * @param distanceMeters Distance flown in meters
     * @return Speed in meters per second
     */
    fun calculateAATSpeedMs(
        distanceMeters: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val scoringTime = maxOf(elapsedTime, minimumTime)
        val scoringSeconds = scoringTime.toMillis() / 1000.0
        return if (scoringSeconds > 0.0) distanceMeters / scoringSeconds else 0.0
    }

    fun calculateAATSpeedMs(result: AATResult, minimumTime: Duration): Double {
        return calculateAATSpeedMs(result.actualDistance, result.elapsedTime, minimumTime)
    }

    fun calculateScoringTime(elapsedTime: Duration, minimumTime: Duration): Duration {
        return maxOf(elapsedTime, minimumTime)
    }

    fun calculateTheoreticalSpeedMs(
        alternativeDistanceMeters: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        return calculateAATSpeedMs(alternativeDistanceMeters, elapsedTime, minimumTime)
    }

    fun calculateEarlyFinishPenaltyMs(
        distanceMeters: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val actualSpeedMs = calculateAATSpeedMs(distanceMeters, elapsedTime, minimumTime)
        val theoreticalSpeedMs = if (elapsedTime < minimumTime) {
            val minimumTimeSeconds = minimumTime.toMillis() / 1000.0
            if (minimumTimeSeconds > 0.0) distanceMeters / minimumTimeSeconds else 0.0
        } else {
            actualSpeedMs
        }
        return theoreticalSpeedMs - actualSpeedMs
    }

    fun calculateDistanceForTargetSpeedMs(
        targetSpeedMs: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val scoringTime = maxOf(elapsedTime, minimumTime)
        val scoringSeconds = scoringTime.toMillis() / 1000.0
        return targetSpeedMs * scoringSeconds
    }

    fun calculateTimeForTargetSpeedMs(
        targetSpeedMs: Double,
        distanceMeters: Double,
        minimumTime: Duration
    ): Duration {
        val requiredSeconds = if (targetSpeedMs > 0.0) distanceMeters / targetSpeedMs else Double.MAX_VALUE
        val requiredTime = Duration.ofMillis((requiredSeconds * 1000.0).toLong())
        return maxOf(requiredTime, minimumTime)
    }

    fun calculateSpeedStatisticsMs(results: List<AATResult>, minimumTime: Duration): SpeedStatistics {
        if (results.isEmpty()) {
            return SpeedStatistics(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val speedsMs = results.map { calculateAATSpeedMs(it, minimumTime) }
        val sortedSpeedsMs = speedsMs.sorted()

        return SpeedStatistics(
            averageSpeedMs = speedsMs.average(),
            maxSpeedMs = sortedSpeedsMs.last(),
            minSpeedMs = sortedSpeedsMs.first(),
            medianSpeedMs = if (sortedSpeedsMs.size % 2 == 0) {
                (sortedSpeedsMs[sortedSpeedsMs.size / 2 - 1] + sortedSpeedsMs[sortedSpeedsMs.size / 2]) / 2.0
            } else {
                sortedSpeedsMs[sortedSpeedsMs.size / 2]
            },
            standardDeviationMs = calculateStandardDeviation(speedsMs)
        )
    }

    fun calculateSpeedForMinimumTimeMs(distanceMeters: Double, minimumTime: Duration): Double {
        val minimumTimeSeconds = minimumTime.toMillis() / 1000.0
        return if (minimumTimeSeconds > 0.0) distanceMeters / minimumTimeSeconds else 0.0
    }

    fun calculateSpeedEfficiency(
        actualSpeedMs: Double,
        maximumDistanceMeters: Double,
        minimumTime: Duration
    ): Double {
        val maxPossibleSpeedMs = calculateSpeedForMinimumTimeMs(maximumDistanceMeters, minimumTime)
        return if (maxPossibleSpeedMs > 0.0) actualSpeedMs / maxPossibleSpeedMs else 0.0
    }

    fun calculateSpeedScenariosMs(
        distanceMeters: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): SpeedScenarios {
        return SpeedScenarios(
            actualSpeedMs = calculateAATSpeedMs(distanceMeters, elapsedTime, minimumTime),
            speedIfMinimumTimeMs = calculateSpeedForMinimumTimeMs(distanceMeters, minimumTime),
            earlyFinishPenaltyMs = calculateEarlyFinishPenaltyMs(distanceMeters, elapsedTime, minimumTime),
            isUnderMinimumTime = elapsedTime < minimumTime
        )
    }

    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        val mean = values.average()
        val squaredDifferences = values.map { (it - mean) * (it - mean) }
        val variance = squaredDifferences.average()

        return kotlin.math.sqrt(variance)
    }
}

data class SpeedStatistics(
    val averageSpeedMs: Double,
    val maxSpeedMs: Double,
    val minSpeedMs: Double,
    val medianSpeedMs: Double,
    val standardDeviationMs: Double
) {
    private companion object {
        const val KMH_PER_MS = 3.6
    }

    fun averageSpeedKmh(): Double = averageSpeedMs * KMH_PER_MS
    fun maxSpeedKmh(): Double = maxSpeedMs * KMH_PER_MS
    fun minSpeedKmh(): Double = minSpeedMs * KMH_PER_MS
    fun medianSpeedKmh(): Double = medianSpeedMs * KMH_PER_MS
    fun standardDeviationKmh(): Double = standardDeviationMs * KMH_PER_MS
}

data class SpeedScenarios(
    val actualSpeedMs: Double,
    val speedIfMinimumTimeMs: Double,
    val earlyFinishPenaltyMs: Double,
    val isUnderMinimumTime: Boolean
) {
    private companion object {
        const val KMH_PER_MS = 3.6
    }

    fun actualSpeedKmh(): Double = actualSpeedMs * KMH_PER_MS
    fun speedIfMinimumTimeKmh(): Double = speedIfMinimumTimeMs * KMH_PER_MS
    fun earlyFinishPenaltyKmh(): Double = earlyFinishPenaltyMs * KMH_PER_MS
}
