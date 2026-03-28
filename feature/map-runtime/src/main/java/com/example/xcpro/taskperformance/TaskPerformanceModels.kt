package com.example.xcpro.taskperformance

enum class TaskPerformanceInvalidReason {
    NONE,
    NO_TASK,
    PRESTART,
    NO_POSITION,
    NO_START,
    NO_ALTITUDE,
    INVALID_ROUTE,
    INVALID,
    STATIC
}

enum class TaskRemainingTimeBasis {
    ACHIEVED_TASK_SPEED
}

data class TaskPerformanceSnapshot(
    val taskSpeedMs: Double = Double.NaN,
    val taskSpeedValid: Boolean = false,
    val taskSpeedInvalidReason: TaskPerformanceInvalidReason = TaskPerformanceInvalidReason.NO_TASK,
    val taskDistanceMeters: Double = Double.NaN,
    val taskDistanceValid: Boolean = false,
    val taskDistanceInvalidReason: TaskPerformanceInvalidReason = TaskPerformanceInvalidReason.NO_TASK,
    val taskRemainingDistanceMeters: Double = Double.NaN,
    val taskRemainingDistanceValid: Boolean = false,
    val taskRemainingDistanceInvalidReason: TaskPerformanceInvalidReason = TaskPerformanceInvalidReason.NO_TASK,
    val taskRemainingTimeMillis: Long = 0L,
    val taskRemainingTimeValid: Boolean = false,
    val taskRemainingTimeBasis: TaskRemainingTimeBasis? = null,
    val taskRemainingTimeInvalidReason: TaskPerformanceInvalidReason = TaskPerformanceInvalidReason.NO_TASK,
    val startAltitudeMeters: Double = Double.NaN,
    val startAltitudeValid: Boolean = false,
    val startAltitudeInvalidReason: TaskPerformanceInvalidReason = TaskPerformanceInvalidReason.NO_START
)
