package com.trust3.xcpro.simulator

import kotlinx.coroutines.flow.StateFlow

data class CondorBridgeRef(
    val stableId: String,
    val displayName: String?
)

enum class CondorTransportKind {
    BLUETOOTH,
    TCP_LISTENER
}

enum class CondorConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class CondorFramingState {
    IDLE,
    WAITING_FOR_FIRST_LINE,
    FLOWING
}

enum class CondorStreamFreshness {
    NO_DATA,
    HEALTHY,
    STALE
}

data class CondorSessionState(
    val connection: CondorConnectionState = CondorConnectionState.DISCONNECTED,
    val framing: CondorFramingState = CondorFramingState.IDLE,
    val freshness: CondorStreamFreshness = CondorStreamFreshness.NO_DATA,
    val hasFixPayload: Boolean = false,
    val lastReceiveElapsedRealtimeMs: Long? = null
)

enum class CondorReconnectState {
    IDLE,
    WAITING,
    ATTEMPTING,
    BLOCKED,
    EXHAUSTED
}

enum class CondorLiveDegradedReason {
    DISCONNECTED,
    STALE_STREAM,
    TRANSPORT_ERROR
}

data class CondorLiveState(
    val selectedTransport: CondorTransportKind = CondorTransportKind.BLUETOOTH,
    val selectedBridge: CondorBridgeRef? = null,
    val activeBridge: CondorBridgeRef? = null,
    val session: CondorSessionState = CondorSessionState(),
    val reconnect: CondorReconnectState = CondorReconnectState.IDLE,
    val lastFailure: CondorLiveDegradedReason? = null
)

interface CondorLiveStatePort {
    val state: StateFlow<CondorLiveState>
}
