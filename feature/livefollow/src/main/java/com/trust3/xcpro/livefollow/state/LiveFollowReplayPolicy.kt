package com.trust3.xcpro.livefollow.state

import com.trust3.xcpro.livesource.LiveSourceKind

enum class LiveFollowRuntimeMode {
    LIVE,
    REPLAY
}

enum class LiveFollowReplayBlockReason {
    NONE,
    REPLAY_MODE,
    SIMULATOR_SOURCE
}

data class LiveFollowReplayDecision(
    val runtimeMode: LiveFollowRuntimeMode,
    val sideEffectsAllowed: Boolean,
    val blockReason: LiveFollowReplayBlockReason
)

class LiveFollowReplayPolicy {
    fun evaluate(
        runtimeMode: LiveFollowRuntimeMode,
        liveSourceKind: LiveSourceKind
    ): LiveFollowReplayDecision {
        return when {
            runtimeMode == LiveFollowRuntimeMode.REPLAY -> LiveFollowReplayDecision(
                runtimeMode = runtimeMode,
                sideEffectsAllowed = false,
                blockReason = LiveFollowReplayBlockReason.REPLAY_MODE
            )

            liveSourceKind == LiveSourceKind.SIMULATOR_CONDOR2 -> LiveFollowReplayDecision(
                runtimeMode = runtimeMode,
                sideEffectsAllowed = false,
                blockReason = LiveFollowReplayBlockReason.SIMULATOR_SOURCE
            )

            else -> LiveFollowReplayDecision(
                runtimeMode = runtimeMode,
                sideEffectsAllowed = true,
                blockReason = LiveFollowReplayBlockReason.NONE
            )
        }
    }
}
