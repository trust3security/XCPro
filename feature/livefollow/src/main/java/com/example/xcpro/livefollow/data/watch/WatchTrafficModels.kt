package com.example.xcpro.livefollow.data.watch

import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.example.xcpro.livefollow.model.LiveFollowSourceEligibility
import com.example.xcpro.livefollow.model.LiveFollowSourceType
import com.example.xcpro.livefollow.state.LiveFollowSessionState

data class WatchTrafficSnapshot(
    val sourceState: LiveFollowSessionState,
    val activeSource: LiveFollowSourceType?,
    val aircraft: WatchAircraftSnapshot?,
    val ageMs: Long?,
    val ognEligibility: LiveFollowSourceEligibility,
    val directEligibility: LiveFollowSourceEligibility,
    val identityResolution: LiveFollowIdentityResolution?
)

data class WatchAircraftSnapshot(
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

internal fun stoppedWatchTrafficSnapshot(): WatchTrafficSnapshot = WatchTrafficSnapshot(
    sourceState = LiveFollowSessionState.STOPPED,
    activeSource = null,
    aircraft = null,
    ageMs = null,
    ognEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
    directEligibility = LiveFollowSourceEligibility.UNAVAILABLE,
    identityResolution = null
)
