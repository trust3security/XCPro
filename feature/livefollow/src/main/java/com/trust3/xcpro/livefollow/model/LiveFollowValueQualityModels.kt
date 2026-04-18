package com.trust3.xcpro.livefollow.model

enum class LiveFollowValueState {
    VALID,
    DEGRADED,
    INVALID,
    UNAVAILABLE
}

data class LiveFollowValueQuality(
    val state: LiveFollowValueState,
    val confidence: LiveFollowConfidence
)
