package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowSourceState
import kotlinx.coroutines.flow.StateFlow

interface DirectWatchTrafficSource {
    val aircraft: StateFlow<DirectWatchAircraftSample?>
}

data class DirectWatchAircraftSample(
    val state: LiveFollowSourceState,
    val confidence: LiveFollowConfidence,
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMslMeters: Double?,
    val groundSpeedMs: Double?,
    val trackDeg: Double?,
    val verticalSpeedMs: Double?,
    val fixMonoMs: Long,
    val fixWallMs: Long?,
    val canonicalIdentity: LiveFollowAircraftIdentity?,
    val displayLabel: String?
)
