package com.trust3.xcpro.orientation

import com.example.dfcards.FlightModeSelection
import com.trust3.xcpro.MapOrientationSettings
import com.trust3.xcpro.common.orientation.BearingSource
import com.trust3.xcpro.common.orientation.MapOrientationMode
import com.trust3.xcpro.common.orientation.OrientationData
import com.trust3.xcpro.common.orientation.OrientationSensorData

class OrientationEngine(
    private val config: Config = Config()
) {
    data class Config(
        val userOverrideTimeoutMs: Long = USER_OVERRIDE_TIMEOUT_MS,
        val trackStaleTimeoutMs: Long = TRACK_STALE_TIMEOUT_MS,
        val headingStaleTimeoutMs: Long = HEADING_STALE_TIMEOUT_MS
    )

    enum class OrientationProfile {
        CRUISE,
        CIRCLING
    }

    data class State(
        val activeProfile: OrientationProfile = OrientationProfile.CRUISE,
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

    fun updateProfile(state: State, selection: FlightModeSelection): State {
        val newProfile = if (selection == FlightModeSelection.THERMAL) {
            OrientationProfile.CIRCLING
        } else {
            OrientationProfile.CRUISE
        }
        if (newProfile == state.activeProfile) {
            return state
        }
        return state.copy(
            activeProfile = newProfile,
            lastValidBearing = normalizeBearing(state.lastOrientation.bearing)
        )
    }

    fun reduce(
        state: State,
        sensorData: OrientationSensorData,
        settings: MapOrientationSettings,
        nowMonoMs: Long,
        nowWallMs: Long
    ): Output {
        val currentMode = resolveMode(state.activeProfile, settings)
        var nextState = state

        if (currentMode != state.lastOrientation.mode) {
            nextState = nextState.copy(lastValidBearing = normalizeBearing(state.lastOrientation.bearing))
        }

        if (nextState.isUserOverrideActive) {
            val timeSinceInteraction = nowMonoMs - nextState.lastUserInteractionMonoMs
            if (timeSinceInteraction <= config.userOverrideTimeoutMs) {
                val frozenResult = BearingResult(
                    bearing = nextState.lastOrientation.bearing,
                    isValid = nextState.lastOrientation.isValid,
                    source = nextState.lastOrientation.bearingSource
                )
                return Output(
                    state = nextState,
                    orientation = nextState.lastOrientation,
                    bearingResult = frozenResult,
                    trackIsStale = false,
                    headingIsStale = false,
                    didUpdate = false
                )
            }
        }

        nextState = nextState.copy(isUserOverrideActive = false)
        val bearingResult = calculateBearing(
            sensorData = sensorData,
            currentMode = currentMode,
            minSpeedThresholdMs = settings.minSpeedThresholdMs
        )
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

    private fun resolveMode(
        profile: OrientationProfile,
        settings: MapOrientationSettings
    ): MapOrientationMode {
        return if (profile == OrientationProfile.CRUISE) {
            settings.cruiseMode
        } else {
            settings.circlingMode
        }
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