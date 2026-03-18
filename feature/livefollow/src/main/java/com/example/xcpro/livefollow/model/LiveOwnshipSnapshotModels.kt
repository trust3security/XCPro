package com.example.xcpro.livefollow.model

enum class LiveOwnshipSourceLabel {
    LIVE_FLIGHT_RUNTIME,
    REPLAY_RUNTIME,
    UNKNOWN
}

data class LiveOwnshipSnapshot(
    val latitudeDeg: Double,
    val longitudeDeg: Double,
    val gpsAltitudeMslMeters: Double?,
    val pressureAltitudeMslMeters: Double?,
    val groundSpeedMs: Double?,
    val trackDeg: Double?,
    val verticalSpeedMs: Double?,
    val fixMonoMs: Long,
    val fixWallMs: Long?,
    val positionQuality: LiveFollowValueQuality,
    val verticalQuality: LiveFollowValueQuality,
    val canonicalIdentity: LiveFollowAircraftIdentity?,
    val sourceLabel: LiveOwnshipSourceLabel
)
