package com.example.xcpro.livefollow.model

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
