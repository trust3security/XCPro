package com.trust3.xcpro.livefollow.model

data class LiveFollowActivePilot(
    val sessionId: String?,
    val shareCode: String,
    val status: String,
    val displayLabel: String,
    val lastPositionWallMs: Long?,
    val latest: LiveFollowActivePilotPoint?
)

data class LiveFollowActivePilotPoint(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val altitudeMslMeters: Double?,
    val groundSpeedMs: Double?,
    val headingDeg: Double?,
    val fixWallMs: Long?
)
