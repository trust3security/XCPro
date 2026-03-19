package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.state.LiveFollowReplayPolicy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LiveFollowSessionRepository(
    scope: CoroutineScope,
    private val ownshipSnapshotSource: LiveOwnshipSnapshotSource,
    private val gateway: LiveFollowSessionGateway,
    private val replayPolicy: LiveFollowReplayPolicy = LiveFollowReplayPolicy()
) {
    private val localGatewayState = MutableStateFlow(gateway.sessionState.value)
    private val mutableState = MutableStateFlow(
        sessionSnapshotFor(
            gatewaySnapshot = localGatewayState.value,
            runtimeMode = ownshipSnapshotSource.runtimeMode.value,
            replayPolicy = replayPolicy
        )
    )

    val state: StateFlow<LiveFollowSessionSnapshot> = mutableState.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.sessionState.collect { snapshot ->
                localGatewayState.value = snapshot
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                localGatewayState,
                ownshipSnapshotSource.runtimeMode
            ) { gatewaySnapshot, runtimeMode ->
                sessionSnapshotFor(
                    gatewaySnapshot = gatewaySnapshot,
                    runtimeMode = runtimeMode,
                    replayPolicy = replayPolicy
                )
            }.collect { sessionSnapshot ->
                mutableState.value = sessionSnapshot
            }
        }
    }

    suspend fun startPilotSession(
        request: StartPilotLiveFollowSession
    ): LiveFollowCommandResult {
        if (!state.value.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(state.value.replayBlockReason)
        }
        commandTransportUnavailableResult(state.value)?.let { return it }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            role = LiveFollowSessionRole.PILOT,
            lifecycle = LiveFollowSessionLifecycle.STARTING,
            lastError = null
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.startPilotSession(request)
        )
    }

    suspend fun stopCurrentSession(): LiveFollowCommandResult {
        val current = state.value
        val sessionId = current.sessionId
            ?: return LiveFollowCommandResult.Failure("No active session to stop.")
        if (current.role != LiveFollowSessionRole.PILOT) {
            return LiveFollowCommandResult.Failure("Current session is not a pilot session.")
        }
        if (!current.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(current.replayBlockReason)
        }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            lifecycle = LiveFollowSessionLifecycle.STOPPING,
            lastError = null
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.stopCurrentSession(sessionId)
        )
    }

    suspend fun joinWatchSession(sessionId: String): LiveFollowCommandResult {
        if (!state.value.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(state.value.replayBlockReason)
        }
        commandTransportUnavailableResult(state.value)?.let { return it }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            sessionId = sessionId,
            role = LiveFollowSessionRole.WATCHER,
            lifecycle = LiveFollowSessionLifecycle.JOINING,
            lastError = null
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.joinWatchSession(sessionId)
        )
    }

    suspend fun leaveSession(): LiveFollowCommandResult {
        val current = state.value
        val sessionId = current.sessionId
            ?: return LiveFollowCommandResult.Failure("No active session to leave.")
        if (current.role != LiveFollowSessionRole.WATCHER) {
            return LiveFollowCommandResult.Failure("Current session is not a watch session.")
        }
        if (!current.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(current.replayBlockReason)
        }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            lifecycle = LiveFollowSessionLifecycle.STOPPING,
            lastError = null
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.leaveSession(sessionId)
        )
    }

    private fun applyGatewayResult(
        previous: LiveFollowSessionGatewaySnapshot,
        result: LiveFollowSessionGatewayResult
    ): LiveFollowCommandResult {
        return when (result) {
            is LiveFollowSessionGatewayResult.Success -> {
                localGatewayState.value = result.snapshot
                LiveFollowCommandResult.Success
            }

            is LiveFollowSessionGatewayResult.Failure -> {
                localGatewayState.value = previous.copy(lastError = result.message)
                LiveFollowCommandResult.Failure(result.message)
            }
        }
    }

    private fun commandTransportUnavailableResult(
        sessionSnapshot: LiveFollowSessionSnapshot
    ): LiveFollowCommandResult.Failure? {
        val availability = sessionSnapshot.transportAvailability
        if (availability.isAvailable) return null
        return LiveFollowCommandResult.Failure(
            availability.message ?: "LiveFollow session transport unavailable."
        )
    }
}

private fun sessionSnapshotFor(
    gatewaySnapshot: LiveFollowSessionGatewaySnapshot,
    runtimeMode: com.example.xcpro.livefollow.state.LiveFollowRuntimeMode,
    replayPolicy: LiveFollowReplayPolicy
): LiveFollowSessionSnapshot {
    val replayDecision = replayPolicy.evaluate(runtimeMode)
    return LiveFollowSessionSnapshot(
        sessionId = gatewaySnapshot.sessionId,
        role = gatewaySnapshot.role,
        lifecycle = gatewaySnapshot.lifecycle,
        runtimeMode = runtimeMode,
        watchIdentity = gatewaySnapshot.watchIdentity,
        directWatchAuthorized = gatewaySnapshot.directWatchAuthorized,
        transportAvailability = gatewaySnapshot.transportAvailability,
        sideEffectsAllowed = replayDecision.sideEffectsAllowed,
        replayBlockReason = replayDecision.blockReason,
        lastError = gatewaySnapshot.lastError
    )
}
