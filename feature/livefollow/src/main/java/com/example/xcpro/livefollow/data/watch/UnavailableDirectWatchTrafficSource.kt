package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowSourceState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DIRECT_WATCH_SOURCE_UNAVAILABLE_MESSAGE =
    "Direct watch telemetry is unavailable in this Phase 3 build."

@Singleton
class UnavailableDirectWatchTrafficSource @Inject constructor() : DirectWatchTrafficSource {
    private val mutableAircraft = MutableStateFlow(
        DirectWatchAircraftSample(
            state = LiveFollowSourceState.UNAVAILABLE,
            confidence = LiveFollowConfidence.UNKNOWN,
            latitudeDeg = 0.0,
            longitudeDeg = 0.0,
            altitudeMslMeters = null,
            groundSpeedMs = null,
            trackDeg = null,
            verticalSpeedMs = null,
            fixMonoMs = 0L,
            fixWallMs = null,
            canonicalIdentity = null,
            displayLabel = DIRECT_WATCH_SOURCE_UNAVAILABLE_MESSAGE
        )
    )

    override val aircraft: StateFlow<DirectWatchAircraftSample?> = mutableAircraft.asStateFlow()
}
