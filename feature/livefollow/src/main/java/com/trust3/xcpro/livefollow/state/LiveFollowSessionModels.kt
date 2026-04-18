package com.trust3.xcpro.livefollow.state

import com.trust3.xcpro.livefollow.model.LiveFollowIdentityResolution
import com.trust3.xcpro.livefollow.model.LiveFollowSourceArbitrationDecision
import com.trust3.xcpro.livefollow.model.LiveFollowSourceType

enum class LiveFollowSessionState {
    WAITING,
    LIVE_OGN,
    LIVE_DIRECT,
    AMBIGUOUS,
    STALE,
    OFFLINE,
    STOPPED
}

data class LiveFollowSessionStatePolicy(
    val staleAfterMs: Long = 15_000L,
    val offlineAfterMs: Long = 45_000L
) {
    init {
        require(staleAfterMs > 0L) { "staleAfterMs must be > 0" }
        require(offlineAfterMs > staleAfterMs) {
            "offlineAfterMs must be > staleAfterMs"
        }
    }
}

data class LiveFollowSessionStateInput(
    val arbitrationDecision: LiveFollowSourceArbitrationDecision,
    val ognIdentityResolution: LiveFollowIdentityResolution? = null,
    val stopRequested: Boolean = false
)

data class LiveFollowSessionStateDecision(
    val state: LiveFollowSessionState,
    val activeSource: LiveFollowSourceType?,
    val lastLiveSource: LiveFollowSourceType?,
    val ageMs: Long?
)
