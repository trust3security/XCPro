package com.trust3.xcpro.tasks.aat.models

import com.trust3.xcpro.tasks.aat.models.AATLatLng
import java.time.Duration
import java.time.LocalDateTime

/**
 * AAT flight result and scoring data.
 * Completely autonomous model for AAT scoring calculations.
 */
data class AATResult(
    val taskId: String,
    val pilotName: String = "",
    val actualDistance: Double,        // meters - distance through credited fixes
    val elapsedTime: Duration,         // actual flight time
    val scoringTime: Duration,         // MAX(elapsed_time, minimum_time)
    val averageSpeedMs: Double,        // m/s - actualDistance / scoringTime
    val creditedFixes: List<AATLatLng>, // Points where areas were achieved
    val flightPath: List<AATLatLng> = emptyList(),
    val startTime: LocalDateTime? = null,
    val finishTime: LocalDateTime? = null,
    val penalties: List<AATPenalty> = emptyList(),
    val taskStatus: AATFlightStatus = AATFlightStatus.INCOMPLETE
) {
    private companion object {
        const val KMH_PER_MS = 3.6
    }
    
    /**
     * Get actual distance in kilometers
     */
    fun getActualDistanceKm(): Double = actualDistance / 1000.0
    
    /**
     * Get elapsed time formatted (HH:MM:SS)
     */
    fun getElapsedTimeFormatted(): String {
        val hours = elapsedTime.toHours()
        val minutes = (elapsedTime.toMinutes() % 60)
        val seconds = (elapsedTime.seconds % 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Get scoring time formatted (HH:MM:SS)
     */
    fun getScoringTimeFormatted(): String {
        val hours = scoringTime.toHours()
        val minutes = (scoringTime.toMinutes() % 60)
        val seconds = (scoringTime.seconds % 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    /**
     * Get average speed formatted (0.0 km/h)
     */
    fun getAverageSpeedFormatted(): String {
        return String.format("%.1f km/h", averageSpeedMs * KMH_PER_MS)
    }
    
    /**
     * Check if the flight completed all assigned areas
     */
    fun isTaskCompleted(): Boolean {
        return taskStatus == AATFlightStatus.COMPLETED
    }
    
    /**
     * Get total penalty time in seconds
     */
    fun getTotalPenaltyTime(): Long {
        return penalties.sumOf { it.penaltySeconds }
    }
    
    /**
     * Get penalty-adjusted scoring time
     */
    fun getAdjustedScoringTime(): Duration {
        return scoringTime.plusSeconds(getTotalPenaltyTime())
    }
    
    /**
     * Get penalty-adjusted average speed
     */
    fun getAdjustedAverageSpeedMs(): Double {
        val adjustedScoringSeconds = getAdjustedScoringTime().toMillis() / 1000.0
        return if (adjustedScoringSeconds > 0.0) actualDistance / adjustedScoringSeconds else 0.0
    }
    
    /**
     * Calculate scoring points based on AAT rules
     * Speed = distance / MAX(elapsed_time, minimum_time)
     */
    fun calculateSpeedPoints(minimumTaskTime: Duration): Double {
        val scoringSeconds = maxOf(elapsedTime, minimumTaskTime).toMillis() / 1000.0
        return if (scoringSeconds > 0.0) actualDistance / scoringSeconds else 0.0
    }
    
    /**
     * Get a summary of the flight result
     */
    fun getResultSummary(): String {
        val status = when (taskStatus) {
            AATFlightStatus.COMPLETED -> "COMPLETED"
            AATFlightStatus.INCOMPLETE -> "INCOMPLETE"
            AATFlightStatus.LANDOUT -> "LAND OUT"
            AATFlightStatus.DNF -> "DNF"
            AATFlightStatus.DISQUALIFIED -> "DSQ"
        }
        return "$status - ${String.format("%.1f", getActualDistanceKm())}km - ${getAverageSpeedFormatted()}"
    }
}

/**
 * AAT flight status enumeration
 */
enum class AATFlightStatus {
    COMPLETED,      // All areas achieved and finished
    INCOMPLETE,     // Flight in progress or not all areas achieved
    LANDOUT,        // Landed out before completion
    DNF,            // Did not finish
    DISQUALIFIED    // Disqualified for rule violations
}

/**
 * AAT penalty types and values
 */
data class AATPenalty(
    val type: AATPenaltyType,
    val description: String,
    val penaltySeconds: Long,
    val location: AATLatLng? = null
) {
    /**
     * Get penalty time formatted
     */
    fun getPenaltyTimeFormatted(): String {
        val minutes = penaltySeconds / 60
        val seconds = penaltySeconds % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds}s"
        } else {
            "${seconds}s"
        }
    }
}

/**
 * Types of penalties in AAT scoring
 */
enum class AATPenaltyType {
    START_LINE_PENALTY,     // Started before gate open or wrong procedure
    AREA_NOT_ACHIEVED,      // Did not achieve required area
    WRONG_SEQUENCE,         // Areas achieved in wrong order
    AIRSPACE_VIOLATION,     // Flew through restricted airspace
    ALTITUDE_VIOLATION,     // Exceeded maximum start altitude
    FINISH_LINE_PENALTY,    // Finish line not crossed correctly
    OTHER                   // Other penalties
}

/**
 * Detailed analysis of AAT flight performance
 */
data class AATPerformanceAnalysis(
    val result: AATResult,
    val areaAchievements: List<AATAreaAchievement>,
    val timeAnalysis: AATTimeAnalysis,
    val speedAnalysis: AATSpeedAnalysis,
    val optimizationSuggestions: List<String> = emptyList()
)

/**
 * Details about achievement of each assigned area
 */
data class AATAreaAchievement(
    val areaName: String,
    val achieved: Boolean,
    val creditedFix: AATLatLng?,
    val entryTime: LocalDateTime?,
    val distanceFromCenter: Double? = null, // meters
    val timeSpentInArea: Duration? = null,
    val optimalPoint: AATLatLng? = null // Suggested optimal point for this area
)

/**
 * Time-based analysis of AAT performance
 */
data class AATTimeAnalysis(
    val minimumTaskTime: Duration,
    val actualElapsedTime: Duration,
    val timeUnderMinimum: Duration?, // How much under minimum time
    val timeOverMinimum: Duration?,  // How much over minimum time
    val timeInAreas: Duration,       // Total time spent inside areas
    val timeToReachAreas: Duration   // Time spent flying between areas
)

/**
 * Speed-based analysis of AAT performance
 */
data class AATSpeedAnalysis(
    val averageTaskSpeedMs: Double,      // m/s
    val averageInterAreaSpeedMs: Double, // m/s between areas
    val maxSpeedSegmentMs: Double,       // m/s fastest segment
    val minSpeedSegmentMs: Double,       // m/s slowest segment
    val speedConsistency: Double       // Coefficient of variation (0-1, lower is more consistent)
)

/**
 * AAT flight validation result
 */
data class AATFlightValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val areasAchieved: Int = 0,
    val totalAreas: Int = 0,
    val sequenceCorrect: Boolean = true
) {
    companion object {
        fun valid(areasAchieved: Int, totalAreas: Int) = 
            AATFlightValidation(true, areasAchieved = areasAchieved, totalAreas = totalAreas)
            
        fun invalid(vararg errors: String) = 
            AATFlightValidation(false, errors.toList())
            
        fun validWithWarnings(areasAchieved: Int, totalAreas: Int, vararg warnings: String) = 
            AATFlightValidation(true, emptyList(), warnings.toList(), areasAchieved, totalAreas)
    }
    
    /**
     * Check if all areas were achieved
     */
    fun allAreasAchieved(): Boolean = areasAchieved == totalAreas && totalAreas > 0
}
