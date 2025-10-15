package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATResult
import com.example.xcpro.tasks.aat.models.AATTask
import java.time.Duration

/**
 * Calculator for speed-related calculations in AAT tasks.
 * This class is completely autonomous and handles all speed calculations
 * according to FAI AAT rules.
 * 
 * AAT Speed Formula: Speed = Distance / MAX(elapsed_time, minimum_time)
 */
class AATSpeedCalculator {
    
    /**
     * Calculate AAT speed according to official rules.
     * Speed = Distance / MAX(elapsed_time, minimum_time)
     * 
     * @param distance Distance flown in meters
     * @param elapsedTime Actual flight time
     * @param minimumTime Minimum task time
     * @return Speed in km/h
     */
    fun calculateAATSpeed(
        distance: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val scoringTime = maxOf(elapsedTime, minimumTime)
        val scoringHours = scoringTime.toMinutes() / 60.0
        val distanceKm = distance / 1000.0
        
        return if (scoringHours > 0) distanceKm / scoringHours else 0.0
    }
    
    /**
     * Calculate AAT speed from an AATResult
     */
    fun calculateAATSpeed(result: AATResult, minimumTime: Duration): Double {
        return calculateAATSpeed(result.actualDistance, result.elapsedTime, minimumTime)
    }
    
    /**
     * Calculate the scoring time used for AAT speed calculation
     */
    fun calculateScoringTime(elapsedTime: Duration, minimumTime: Duration): Duration {
        return maxOf(elapsedTime, minimumTime)
    }
    
    /**
     * Calculate the theoretical optimal speed if pilot had chosen different distance
     * 
     * @param alternativeDistance Different distance choice in meters
     * @param elapsedTime Actual flight time
     * @param minimumTime Minimum task time  
     * @return Theoretical speed in km/h
     */
    fun calculateTheoreticalSpeed(
        alternativeDistance: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        return calculateAATSpeed(alternativeDistance, elapsedTime, minimumTime)
    }
    
    /**
     * Calculate the speed advantage/disadvantage of finishing early vs. late
     * 
     * @param distance Distance flown in meters
     * @param elapsedTime Actual flight time
     * @param minimumTime Minimum task time
     * @return Speed difference in km/h (positive = advantage of finishing at minimum time)
     */
    fun calculateEarlyFinishPenalty(
        distance: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val actualSpeed = calculateAATSpeed(distance, elapsedTime, minimumTime)
        
        // If finished before minimum time, calculate what speed would have been
        // if they had flown the same distance but finished at minimum time
        val theoreticalSpeed = if (elapsedTime < minimumTime) {
            val minimumTimeHours = minimumTime.toMinutes() / 60.0
            val distanceKm = distance / 1000.0
            if (minimumTimeHours > 0) distanceKm / minimumTimeHours else 0.0
        } else {
            actualSpeed // No penalty if finished after minimum time
        }
        
        return theoreticalSpeed - actualSpeed
    }
    
    /**
     * Calculate the minimum distance needed to achieve a target speed
     * 
     * @param targetSpeed Target speed in km/h
     * @param elapsedTime Actual flight time
     * @param minimumTime Minimum task time
     * @return Required distance in meters
     */
    fun calculateDistanceForTargetSpeed(
        targetSpeed: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): Double {
        val scoringTime = maxOf(elapsedTime, minimumTime)
        val scoringHours = scoringTime.toMinutes() / 60.0
        
        return targetSpeed * scoringHours * 1000.0 // Convert to meters
    }
    
    /**
     * Calculate the time needed to achieve a target speed with a given distance
     * 
     * @param targetSpeed Target speed in km/h
     * @param distance Distance in meters
     * @param minimumTime Minimum task time
     * @return Required time (will not be less than minimum time)
     */
    fun calculateTimeForTargetSpeed(
        targetSpeed: Double,
        distance: Double,
        minimumTime: Duration
    ): Duration {
        val distanceKm = distance / 1000.0
        val requiredHours = if (targetSpeed > 0) distanceKm / targetSpeed else Double.MAX_VALUE
        val requiredMinutes = (requiredHours * 60).toLong()
        val requiredTime = Duration.ofMinutes(requiredMinutes)
        
        return maxOf(requiredTime, minimumTime)
    }
    
    /**
     * Calculate speed statistics for multiple results
     */
    fun calculateSpeedStatistics(results: List<AATResult>, minimumTime: Duration): SpeedStatistics {
        if (results.isEmpty()) {
            return SpeedStatistics(0.0, 0.0, 0.0, 0.0, 0.0)
        }
        
        val speeds = results.map { calculateAATSpeed(it, minimumTime) }
        val sortedSpeeds = speeds.sorted()
        
        return SpeedStatistics(
            averageSpeed = speeds.average(),
            maxSpeed = sortedSpeeds.last(),
            minSpeed = sortedSpeeds.first(),
            medianSpeed = if (sortedSpeeds.size % 2 == 0) {
                (sortedSpeeds[sortedSpeeds.size / 2 - 1] + sortedSpeeds[sortedSpeeds.size / 2]) / 2.0
            } else {
                sortedSpeeds[sortedSpeeds.size / 2]
            },
            standardDeviation = calculateStandardDeviation(speeds)
        )
    }
    
    /**
     * Calculate what the speed would be if pilot had flown for exactly minimum time
     * 
     * @param distance Distance flown in meters
     * @param minimumTime Minimum task time
     * @return Speed if flown for minimum time in km/h
     */
    fun calculateSpeedForMinimumTime(distance: Double, minimumTime: Duration): Double {
        val minimumTimeHours = minimumTime.toMinutes() / 60.0
        val distanceKm = distance / 1000.0
        
        return if (minimumTimeHours > 0) distanceKm / minimumTimeHours else 0.0
    }
    
    /**
     * Calculate the percentage of maximum possible speed achieved
     * 
     * @param actualSpeed Achieved speed in km/h
     * @param maximumDistance Maximum possible distance for the task in meters
     * @param minimumTime Minimum task time
     * @return Percentage (0.0 to 1.0) of maximum speed achieved
     */
    fun calculateSpeedEfficiency(
        actualSpeed: Double,
        maximumDistance: Double,
        minimumTime: Duration
    ): Double {
        val maxPossibleSpeed = calculateSpeedForMinimumTime(maximumDistance, minimumTime)
        return if (maxPossibleSpeed > 0) actualSpeed / maxPossibleSpeed else 0.0
    }
    
    /**
     * Calculate speed for different time scenarios
     */
    fun calculateSpeedScenarios(
        distance: Double,
        elapsedTime: Duration,
        minimumTime: Duration
    ): SpeedScenarios {
        return SpeedScenarios(
            actualSpeed = calculateAATSpeed(distance, elapsedTime, minimumTime),
            speedIfMinimumTime = calculateSpeedForMinimumTime(distance, minimumTime),
            earlyFinishPenalty = calculateEarlyFinishPenalty(distance, elapsedTime, minimumTime),
            isUnderMinimumTime = elapsedTime < minimumTime
        )
    }
    
    /**
     * Helper function to calculate standard deviation
     */
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        
        val mean = values.average()
        val squaredDifferences = values.map { (it - mean) * (it - mean) }
        val variance = squaredDifferences.average()
        
        return kotlin.math.sqrt(variance)
    }
}

/**
 * Speed statistics for a group of AAT results
 */
data class SpeedStatistics(
    val averageSpeed: Double,    // km/h
    val maxSpeed: Double,        // km/h
    val minSpeed: Double,        // km/h
    val medianSpeed: Double,     // km/h
    val standardDeviation: Double // km/h
)

/**
 * Speed analysis for different time scenarios
 */
data class SpeedScenarios(
    val actualSpeed: Double,         // km/h - actual achieved speed
    val speedIfMinimumTime: Double,  // km/h - speed if flown for minimum time
    val earlyFinishPenalty: Double,  // km/h - penalty for finishing early
    val isUnderMinimumTime: Boolean  // true if finished before minimum time
)
