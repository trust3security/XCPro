package com.trust3.xcpro.livefollow.data.watch

import com.trust3.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.trust3.xcpro.livefollow.model.LiveFollowConfidence
import com.trust3.xcpro.livefollow.model.LiveFollowSourceState
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.livefollow.model.LiveFollowTransportAvailability
import kotlinx.coroutines.flow.StateFlow

interface DirectWatchTrafficSource {
    val aircraft: StateFlow<DirectWatchAircraftSample?>
    val task: StateFlow<LiveFollowTaskSnapshot?>
    val transportAvailability: StateFlow<LiveFollowTransportAvailability>
}

data class DirectWatchAircraftSample(
    val state: LiveFollowSourceState,
    val confidence: LiveFollowConfidence,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMslMeters: Double?,
    val aglMeters: Double? = null,
    val groundSpeedMs: Double?,
    val trackDeg: Double?,
    val verticalSpeedMs: Double?,
    val fixMonoMs: Long,
    val fixWallMs: Long?,
    val canonicalIdentity: LiveFollowAircraftIdentity?,
    val displayLabel: String?
)
