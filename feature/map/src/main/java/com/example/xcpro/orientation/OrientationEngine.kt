package com.example.xcpro.orientation

import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.common.orientation.OrientationSensorData

class OrientationEngine(
    private val config: Config = Config()
) {
    data class Config(
        val userOverrideTimeoutMs: Long = USER_OVERRIDE_TIMEOUT_MS,
        val trackStaleTimeoutMs: Long = TRACK_STALE_TIMEOUT_MS,
        val headingStaleTimeoutMs: Long = HEADING_STALE_TIMEOUT_MS
    )

    data class State(
        val isUserOverrideActive: Boolean = false,
        val lastUserInteractionMonoMs: Long = 0L,
        val lastValidBearing: Double = 0.0,
        val lastValidTrackTimeMs: Long = 0L,
        val lastValidHeadingTimeMs: Long = 0L,
        val lastOrientation: OrientationData = OrientationData()
    )

    data class BearingResult(
        val bearing: Double,
        val isValid: Boolean,
        val source: BearingSource
    )

    data class Output(
        val state: State,
        val orientation: OrientationData,
        val bearingResult: BearingResult,
        val trackIsStale: Boolean,
        val headingIsStale: Boolean,
        val didUpdate: Boolean
    )

    fun onUserInteraction(state: State, nowMonoMs: Long): State {
        return state.copy(
            isUserOverrideActive = true,
            lastUserInteractionMonoMs = nowMonoMs
        )
    }

    fun resetUserOverride(state: State): State = state.copy(isUserOverrideActive = false)

    fun syncLastValidBearing(state: State): State {
        return state.copy(lastValidBearing = normalizeBearing(state.lastOrientation.bearing))
    }

    fun reduce(
        state: State,
        sensorData: OrientationSensorData,
        currentMode: MapOrientationMode,
        minSpeedThresholdMs: Double,
        nowMonoMs: Long,
        nowWallMs: Long
    ): Output {
        if (state.isUserOverrideActive) {
            val timeSinceInteraction = nowMonoMs - state.lastUserInteractionMonoMs
            if (timeSinceInteraction <= config.userOverrideTimeoutMs) {
                val frozenResult = BearingResult(
                    bearing = state.lastOrientation.bearing,
                    isValid = state.lastOrientation.isValid,
                    source = state.lastOrientation.bearingSource
                )
                return Output(
                    state = state,
                    orientation = state.lastOrientation,
                    bearingResult = frozenResult,
                    trackIsStale = false,
                    headingIsStale = false,
                    didUpdate = false
                )
            }
        }

        var nextState = state.copy(isUserOverrideActive = false)
        val bearingResult = calculateBearing(sensorData, currentMode, minSpeedThresholdMs)
        val normalizedBearing = normalizeBearing(bearingResult.bearing)

        if (bearingResult.isValid) {
            nextState = nextState.copy(lastValidBearing = normalizedBearing)
            if (currentMode == MapOrientationMode.TRACK_UP) {
                nextState = nextState.copy(lastValidTrackTimeMs = nowMonoMs)
            }
            if (currentMode == MapOrientationMode.HEADING_UP) {
                nextState = nextState.copy(lastValidHeadingTimeMs = nowMonoMs)
            }
        }

        val trackIsStale = currentMode == MapOrientationMode.TRACK_UP &&
            (nextState.lastValidTrackTimeMs == 0L ||
                nowMonoMs - nextState.lastValidTrackTimeMs > config.trackStaleTimeoutMs)

        val headingIsStale = currentMode == MapOrientationMode.HEADING_UP &&
            !bearingResult.isValid &&
            (nextState.lastValidHeadingTimeMs == 0L ||
                nowMonoMs - nextState.lastValidHeadingTimeMs > config.headingStaleTimeoutMs)

        val finalBearing = when {
            bearingResult.isValid -> normalizedBearing
            headingIsStale -> 0.0
            trackIsStale -> 0.0
            else -> nextState.lastValidBearing
        }
        val finalSource = when {
            bearingResult.isValid -> bearingResult.source
            headingIsStale -> BearingSource.NONE
            trackIsStale -> BearingSource.NONE
            else -> BearingSource.LAST_KNOWN
        }
        val finalValid = bearingResult.isValid ||
            (currentMode == MapOrientationMode.TRACK_UP && !trackIsStale)

        val headingSolution = sensorData.headingSolution
        val orientationData = OrientationData(
            bearing = finalBearing,
            mode = currentMode,
            isValid = finalValid,
            bearingSource = finalSource,
            headingDeg = headingSolution.bearingDeg,
            headingValid = headingSolution.isValid,
            headingSource = headingSolution.source,
            timestamp = nowWallMs
        )

        nextState = nextState.copy(lastOrientation = orientationData)

        return Output(
            state = nextState,
            orientation = orientationData,
            bearingResult = bearingResult,
            trackIsStale = trackIsStale,
            headingIsStale = headingIsStale,
            didUpdate = true
        )
    }

    private fun calculateBearing(
        sensorData: OrientationSensorData,
        currentMode: MapOrientationMode,
        minSpeedThresholdMs: Double
    ): BearingResult {
        return when (currentMode) {
            MapOrientationMode.NORTH_UP -> BearingResult(0.0, true, BearingSource.NONE)

            MapOrientationMode.TRACK_UP -> {
                val hasTrack = sensorData.isGPSValid && sensorData.track.isFinite()
                val valid = hasTrack && sensorData.groundSpeed >= minSpeedThresholdMs
                val bearing = if (hasTrack) sensorData.track else 0.0
                BearingResult(bearing, valid, BearingSource.TRACK)
            }

            MapOrientationMode.HEADING_UP -> {
                val solution = sensorData.headingSolution
                BearingResult(solution.bearingDeg, solution.isValid, solution.source)
            }
        }
    }

    private companion object {
        private const val USER_OVERRIDE_TIMEOUT_MS = 10_000L
        private const val TRACK_STALE_TIMEOUT_MS = 10_000L
        private const val HEADING_STALE_TIMEOUT_MS = 5_000L
    }
}