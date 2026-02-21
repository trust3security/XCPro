package com.example.xcpro.tasks.racing.models

import java.time.Duration
import java.time.LocalDateTime

/**
 * Racing task result/performance data
 */
data class RacingTaskResult(
    val taskId: String,
    val pilotName: String,
    val startTime: LocalDateTime?,
    val finishTime: LocalDateTime?,
    val turnpointTimes: List<LocalDateTime>,
    val actualDistance: Double, // meters
    val averageSpeed: Double?, // km/h
    val isCompleted: Boolean,
    val penalties: List<RacingPenalty> = emptyList()
) {
    fun getTaskDuration(): Duration? {
        return if (startTime != null && finishTime != null) {
            Duration.between(startTime, finishTime)
        } else {
            null
        }
    }

    fun getDurationFormatted(): String {
        val duration = getTaskDuration() ?: return "N/A"
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

/**
 * Racing penalty types
 */
data class RacingPenalty(
    val type: RacingPenaltyType,
    val points: Int,
    val description: String
)

enum class RacingPenaltyType {
    AIRSPACE_INFRINGEMENT,
    START_HEIGHT_EXCEEDED,
    MISSED_TURNPOINT,
    DANGEROUS_FLYING,
    OTHER
}
