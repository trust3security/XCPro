package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveFollowTransportAvailability
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode

enum class LiveFollowSessionRole {
    NONE,
    PILOT,
    WATCHER
}

enum class LiveFollowSessionLifecycle {
    IDLE,
    STARTING,
    JOINING,
    ACTIVE,
    STOPPING,
    ERROR
}

data class StartPilotLiveFollowSession(
    val pilotIdentity: LiveFollowIdentityProfile,
    val taskId: String? = null
)

data class LiveFollowSessionSnapshot(
    val sessionId: String?,
    val role: LiveFollowSessionRole,
    val lifecycle: LiveFollowSessionLifecycle,
    val runtimeMode: LiveFollowRuntimeMode,
    val watchIdentity: LiveFollowIdentityProfile?,
    val directWatchAuthorized: Boolean,
    val transportAvailability: LiveFollowTransportAvailability,
    val sideEffectsAllowed: Boolean,
    val replayBlockReason: LiveFollowReplayBlockReason,
    val lastError: String?,
    val shareCode: String? = null,
    val watchLookup: LiveFollowWatchLookup? = null
)

sealed interface LiveFollowCommandResult {
    data object Success : LiveFollowCommandResult

    data class Rejected(
        val reason: LiveFollowReplayBlockReason
    ) : LiveFollowCommandResult

    data class Failure(
        val message: String
    ) : LiveFollowCommandResult
}

internal fun idleSessionSnapshot(
    runtimeMode: LiveFollowRuntimeMode = LiveFollowRuntimeMode.LIVE,
    transportAvailability: LiveFollowTransportAvailability = liveFollowAvailableTransport()
): LiveFollowSessionSnapshot = LiveFollowSessionSnapshot(
    sessionId = null,
    role = LiveFollowSessionRole.NONE,
    lifecycle = LiveFollowSessionLifecycle.IDLE,
    runtimeMode = runtimeMode,
    watchIdentity = null,
    directWatchAuthorized = false,
    transportAvailability = transportAvailability,
    sideEffectsAllowed = runtimeMode == LiveFollowRuntimeMode.LIVE,
    replayBlockReason = if (runtimeMode == LiveFollowRuntimeMode.LIVE) {
        LiveFollowReplayBlockReason.NONE
    } else {
        LiveFollowReplayBlockReason.REPLAY_MODE
    },
    lastError = null,
    shareCode = null,
    watchLookup = null
)
