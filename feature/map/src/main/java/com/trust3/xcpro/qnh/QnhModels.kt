package com.trust3.xcpro.qnh

/**
 * QNH value with metadata for UI and diagnostics.
 */
data class QnhValue(
    val hpa: Double,
    val source: QnhSource,
    val calibratedAtMillis: Long,
    val confidence: QnhConfidence
)

enum class QnhSource {
    EXTERNAL,
    MANUAL,
    AUTO_TERRAIN,
    AUTO_GPS,
    STANDARD
}

enum class QnhConfidence {
    LOW,
    MEDIUM,
    HIGH
}

sealed interface QnhCalibrationState {
    data object Idle : QnhCalibrationState
    data class Collecting(val samplesCollected: Int, val samplesRequired: Int) : QnhCalibrationState
    data class Succeeded(val value: QnhValue) : QnhCalibrationState
    data class Failed(val reason: QnhCalibrationFailureReason) : QnhCalibrationState
    data object TimedOut : QnhCalibrationState
}

enum class QnhCalibrationFailureReason {
    REPLAY_MODE,
    ALREADY_RUNNING,
    TIMEOUT,
    INVALID_QNH,
    MISSING_SENSORS,
    UNKNOWN
}

data class QnhCalibrationConfig(
    val samplesRequired: Int = 15,
    val maxGpsSpeedMs: Double = 3.0,
    val maxGpsAccuracyMeters: Double = 10.0,
    val maxSampleAgeMs: Long = 1_500L,
    val timeoutMs: Long = 90_000L,
    val minQnhHpa: Double = 950.0,
    val maxQnhHpa: Double = 1050.0,
    val estimatedAglMeters: Double = 2.0,
    val trimFraction: Double = 0.2
)
