package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.data.task.LiveFollowTaskSnapshotSource
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
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
    private val taskSnapshotSource: LiveFollowTaskSnapshotSource,
    private val gateway: LiveFollowSessionGateway,
    private val replayPolicy: LiveFollowReplayPolicy = LiveFollowReplayPolicy()
) {
    private val localGatewayState = MutableStateFlow(gateway.sessionState.value)
    private val latestTaskSnapshot = MutableStateFlow<LiveFollowTaskSnapshot?>(null)
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

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            ownshipSnapshotSource.snapshot.collect { ownshipSnapshot ->
                uploadActivePilotSnapshot(ownshipSnapshot)
            }
        }

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            taskSnapshotSource.taskSnapshot.collect { taskSnapshot ->
                latestTaskSnapshot.value = taskSnapshot
                uploadActivePilotTask(taskSnapshot)
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
        val commandResult = applyGatewayResult(
            previous = previous,
            result = gateway.startPilotSession(request)
        )
        if (commandResult == LiveFollowCommandResult.Success) {
            uploadActivePilotSnapshot(ownshipSnapshotSource.snapshot.value)
            uploadActivePilotTask(latestTaskSnapshot.value)
        }
        return commandResult
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

    suspend fun updatePilotVisibility(
        visibility: LiveFollowSessionVisibility
    ): LiveFollowCommandResult {
        val current = state.value
        val sessionId = current.sessionId
            ?: return LiveFollowCommandResult.Failure("No active pilot session to update.")
        if (current.role != LiveFollowSessionRole.PILOT) {
            return LiveFollowCommandResult.Failure("Current session is not a pilot session.")
        }
        if (!current.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(current.replayBlockReason)
        }
        commandTransportUnavailableResult(current)?.let { return it }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            lifecycle = LiveFollowSessionLifecycle.STARTING,
            lastError = null
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.updatePilotVisibility(sessionId, visibility)
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
            lastError = null,
            shareCode = null,
            watchLookup = liveFollowSessionIdLookup(sessionId)
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.joinWatchSession(sessionId)
        )
    }

    suspend fun joinAuthenticatedWatchSession(sessionId: String): LiveFollowCommandResult {
        if (!state.value.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(state.value.replayBlockReason)
        }
        commandTransportUnavailableResult(state.value)?.let { return it }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            sessionId = sessionId,
            ownerUserId = null,
            role = LiveFollowSessionRole.WATCHER,
            lifecycle = LiveFollowSessionLifecycle.JOINING,
            visibility = null,
            lastError = null,
            shareCode = null,
            watchLookup = liveFollowAuthenticatedSessionIdLookup(sessionId)
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.joinAuthenticatedWatchSession(sessionId)
        )
    }

    suspend fun joinWatchSessionByShareCode(shareCode: String): LiveFollowCommandResult {
        if (!state.value.sideEffectsAllowed) {
            return LiveFollowCommandResult.Rejected(state.value.replayBlockReason)
        }
        commandTransportUnavailableResult(state.value)?.let { return it }

        val previous = localGatewayState.value
        localGatewayState.value = previous.copy(
            sessionId = null,
            role = LiveFollowSessionRole.WATCHER,
            lifecycle = LiveFollowSessionLifecycle.JOINING,
            lastError = null,
            shareCode = shareCode,
            watchLookup = liveFollowShareCodeLookup(shareCode)
        )
        return applyGatewayResult(
            previous = previous,
            result = gateway.joinWatchSessionByShareCode(shareCode)
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
                val latestGatewaySnapshot = gateway.sessionState.value
                localGatewayState.value = latestGatewaySnapshot.copy(lastError = result.message)
                LiveFollowCommandResult.Failure(result.message)
            }
        }
    }

    private suspend fun uploadActivePilotSnapshot(
        ownshipSnapshot: LiveOwnshipSnapshot?
    ) {
        if (ownshipSnapshot == null) return
        val currentSession = state.value
        if (!currentSession.sideEffectsAllowed) return
        if (currentSession.role != LiveFollowSessionRole.PILOT) return
        if (currentSession.lifecycle != LiveFollowSessionLifecycle.ACTIVE) return
        gateway.uploadPilotPosition(ownshipSnapshot)
    }

    private suspend fun uploadActivePilotTask(
        taskSnapshot: LiveFollowTaskSnapshot?
    ) {
        val currentSession = state.value
        if (!currentSession.sideEffectsAllowed) return
        if (currentSession.role != LiveFollowSessionRole.PILOT) return
        if (currentSession.lifecycle != LiveFollowSessionLifecycle.ACTIVE) return
        gateway.uploadPilotTask(taskSnapshot)
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
        ownerUserId = gatewaySnapshot.ownerUserId,
        role = gatewaySnapshot.role,
        lifecycle = gatewaySnapshot.lifecycle,
        visibility = gatewaySnapshot.visibility,
        runtimeMode = runtimeMode,
        watchIdentity = gatewaySnapshot.watchIdentity,
        directWatchAuthorized = gatewaySnapshot.directWatchAuthorized,
        transportAvailability = gatewaySnapshot.transportAvailability,
        sideEffectsAllowed = replayDecision.sideEffectsAllowed,
        replayBlockReason = replayDecision.blockReason,
        lastError = gatewaySnapshot.lastError,
        shareCode = gatewaySnapshot.shareCode,
        watchLookup = deriveLiveFollowWatchLookup(
            explicitLookup = gatewaySnapshot.watchLookup,
            shareCode = gatewaySnapshot.shareCode,
            sessionId = gatewaySnapshot.sessionId
        )
    )
}
