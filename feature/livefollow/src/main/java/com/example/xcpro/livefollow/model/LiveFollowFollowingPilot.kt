package com.example.xcpro.livefollow.model

import com.example.xcpro.livefollow.data.session.LiveFollowSessionVisibility

data class LiveFollowFollowingPilot(
    val sessionId: String,
    val userId: String,
    val visibility: LiveFollowSessionVisibility?,
    val shareCode: String?,
    val status: String,
    val displayLabel: String,
    val lastPositionWallMs: Long?,
    val latest: LiveFollowActivePilotPoint?
)
