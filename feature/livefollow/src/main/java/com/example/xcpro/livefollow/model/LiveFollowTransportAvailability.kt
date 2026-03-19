package com.example.xcpro.livefollow.model

enum class LiveFollowTransportState {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE
}

data class LiveFollowTransportAvailability(
    val state: LiveFollowTransportState,
    val message: String? = null
) {
    val isAvailable: Boolean
        get() = state == LiveFollowTransportState.AVAILABLE
}

fun liveFollowAvailableTransport(): LiveFollowTransportAvailability =
    LiveFollowTransportAvailability(state = LiveFollowTransportState.AVAILABLE)

fun liveFollowUnavailableTransport(
    message: String
): LiveFollowTransportAvailability = LiveFollowTransportAvailability(
    state = LiveFollowTransportState.UNAVAILABLE,
    message = message
)
