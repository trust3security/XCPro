package com.example.xcpro.replay

import android.net.Uri

internal const val DEFAULT_SPEED = 1.0
internal const val DEFAULT_QNH_HPA = 1013.3

data class SessionState(
    val selection: Selection? = null,
    val status: SessionStatus = SessionStatus.IDLE,
    val speedMultiplier: Double = DEFAULT_SPEED,
    val startTimestampMillis: Long = 0L,
    val currentTimestampMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val qnhHpa: Double = DEFAULT_QNH_HPA
) {
    val hasSelection: Boolean get() = selection != null
    val elapsedMillis: Long get() = (currentTimestampMillis - startTimestampMillis).coerceAtLeast(0L)
    val progressFraction: Float
        get() = if (durationMillis <= 0L) 0f else (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
}

data class Selection(val uri: Uri, val displayName: String?)

enum class SessionStatus { IDLE, PAUSED, PLAYING }

enum class ReplayMode { REFERENCE, REALTIME_SIM }

enum class ReplayInterpolation { LINEAR, CATMULL_ROM, CATMULL_ROM_RUNTIME }

data class ReplaySimConfig(
    val mode: ReplayMode = ReplayMode.REALTIME_SIM,
    val baroStepMs: Long = 20L,    // 50 Hz baro cadence (closer to live sensors)
    val gpsStepMs: Long = 1_000L,  // 1 Hz GPS cadence
    val referenceStepMs: Long = 1_000L, // REFERENCE densify cadence (matches B-record seconds by default)
    val jitterMs: Long = 8L,       // +/- 8 ms timing jitter
    val pressureNoiseSigmaHpa: Double = 0.04,
    val gpsAltitudeNoiseSigmaM: Double = 1.5,
    val gpsAccuracyMeters: Float = 5f,
    val warmupMillis: Long = 8_000L,
    val seed: Long = 1_337L,
    val interpolation: ReplayInterpolation = ReplayInterpolation.LINEAR
)

data class ReplayNoiseProfile(
    val pressureNoiseSigmaHpa: Double,
    val gpsAltitudeNoiseSigmaM: Double,
    val jitterMs: Long
)

data class ReplayCadenceProfile(
    val referenceStepMs: Long = 1_000L,
    val gpsStepMs: Long = 1_000L
) {
    companion object {
        val DEFAULT = ReplayCadenceProfile()
        val LIVE_100MS = ReplayCadenceProfile(referenceStepMs = 100L, gpsStepMs = 100L)
    }
}

sealed interface ReplayEvent {
    data class Completed(val samples: Int) : ReplayEvent
    data class Failed(val throwable: Throwable) : ReplayEvent
    object Cancelled : ReplayEvent
}
