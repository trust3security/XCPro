package com.trust3.xcpro.livefollow.state

enum class LiveFollowRuntimeMode {
    LIVE,
    REPLAY
}

enum class LiveFollowReplayBlockReason {
    NONE,
    REPLAY_MODE
}

data class LiveFollowReplayDecision(
    val runtimeMode: LiveFollowRuntimeMode,
    val sideEffectsAllowed: Boolean,
    val blockReason: LiveFollowReplayBlockReason
)

class LiveFollowReplayPolicy {
    fun evaluate(runtimeMode: LiveFollowRuntimeMode): LiveFollowReplayDecision {
        return when (runtimeMode) {
            LiveFollowRuntimeMode.LIVE -> LiveFollowReplayDecision(
                runtimeMode = runtimeMode,
                sideEffectsAllowed = true,
                blockReason = LiveFollowReplayBlockReason.NONE
            )

            LiveFollowRuntimeMode.REPLAY -> LiveFollowReplayDecision(
                runtimeMode = runtimeMode,
                sideEffectsAllowed = false,
                blockReason = LiveFollowReplayBlockReason.REPLAY_MODE
            )
        }
    }
}
