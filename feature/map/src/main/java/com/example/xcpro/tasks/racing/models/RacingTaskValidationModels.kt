package com.example.xcpro.tasks.racing.models

/**
 * Task validation result for racing tasks
 */
data class RacingTaskValidation(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val taskDistance: RacingTaskDistance? = null
) {
    companion object {
        fun valid(taskDistance: RacingTaskDistance? = null) =
            RacingTaskValidation(true, taskDistance = taskDistance)

        fun invalid(vararg errors: String) =
            RacingTaskValidation(false, errors.toList())

        fun validWithWarnings(taskDistance: RacingTaskDistance, vararg warnings: String) =
            RacingTaskValidation(true, emptyList(), warnings.toList(), taskDistance)
    }
}

/**
 * Racing task distance information
 */
data class RacingTaskDistance(
    val totalDistance: Double,           // meters - optimal FAI racing distance
    val startToFirstTurnpoint: Double,   // meters
    val turnpointDistances: List<Double>, // meters - distances between turnpoints
    val lastTurnpointToFinish: Double,   // meters
    val nominalDistance: Double          // meters - center-to-center distance
) {
    fun getTotalDistanceKm(): Double = totalDistance / 1000.0

    fun getNominalDistanceKm(): Double = nominalDistance / 1000.0

    fun getTotalDistanceFormatted(): String {
        return String.format("%.2f km", getTotalDistanceKm())
    }

    fun getAllSegmentDistances(): List<Double> {
        val segments = mutableListOf<Double>()
        segments.add(startToFirstTurnpoint)
        segments.addAll(turnpointDistances)
        segments.add(lastTurnpointToFinish)
        return segments
    }
}
