package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveFollowSessionRepositoryTest {

    @Test
    fun startPilotSession_updatesStateFromGatewaySuccess() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource()
        val taskSource = FakeTaskSnapshotSource()
        val gateway = FakeSessionGateway(
            startResult = LiveFollowSessionGatewayResult.Success(
                snapshot = LiveFollowSessionGatewaySnapshot(
                    sessionId = "pilot-1",
                    role = LiveFollowSessionRole.PILOT,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    watchIdentity = null,
                    directWatchAuthorized = false,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
            )
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        val result = repository.startPilotSession(
            StartPilotLiveFollowSession(
                pilotIdentity = identityProfile("AB12CD")
            )
        )
        advanceUntilIdle()
        assertEquals(LiveFollowCommandResult.Success, result)
        assertEquals(1, gateway.startCalls)
        assertEquals("pilot-1", repository.state.value.sessionId)
        assertEquals(LiveFollowSessionRole.PILOT, repository.state.value.role)
        assertEquals(LiveFollowSessionLifecycle.ACTIVE, repository.state.value.lifecycle)
        assertTrue(repository.state.value.sideEffectsAllowed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun joinWatchSession_updatesWatcherStateFromGatewaySuccess() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource()
        val taskSource = FakeTaskSnapshotSource()
        val gateway = FakeSessionGateway(
            joinResult = LiveFollowSessionGatewayResult.Success(
                snapshot = LiveFollowSessionGatewaySnapshot(
                    sessionId = "watch-1",
                    role = LiveFollowSessionRole.WATCHER,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    watchIdentity = identityProfile("F1A2B3"),
                    directWatchAuthorized = true,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
            )
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        val result = repository.joinWatchSession("watch-1")
        advanceUntilIdle()
        assertEquals(LiveFollowCommandResult.Success, result)
        assertEquals(1, gateway.joinCalls)
        assertEquals(LiveFollowSessionRole.WATCHER, repository.state.value.role)
        assertEquals(LiveFollowSessionLifecycle.ACTIVE, repository.state.value.lifecycle)
        assertTrue(repository.state.value.directWatchAuthorized)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun joinWatchSessionByShareCode_updatesWatcherStateFromGatewaySuccess() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource()
        val taskSource = FakeTaskSnapshotSource()
        val gateway = FakeSessionGateway(
            joinByShareCodeResult = LiveFollowSessionGatewayResult.Success(
                snapshot = LiveFollowSessionGatewaySnapshot(
                    sessionId = "watch-2",
                    role = LiveFollowSessionRole.WATCHER,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    watchIdentity = null,
                    directWatchAuthorized = true,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null,
                    shareCode = "WATCH123",
                    watchLookup = liveFollowShareCodeLookup("WATCH123")
                )
            )
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        val result = repository.joinWatchSessionByShareCode("WATCH123")
        advanceUntilIdle()
        assertEquals(LiveFollowCommandResult.Success, result)
        assertEquals(1, gateway.joinByShareCodeCalls)
        assertEquals(LiveFollowSessionRole.WATCHER, repository.state.value.role)
        assertEquals("watch-2", repository.state.value.sessionId)
        assertEquals("WATCH123", repository.state.value.shareCode)
        assertEquals(
            LiveFollowWatchLookupType.SHARE_CODE,
            repository.state.value.watchLookup?.type
        )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun replayMode_blocksGatewaySideEffects() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            runtimeMode = MutableStateFlow(LiveFollowRuntimeMode.REPLAY)
        )
        val taskSource = FakeTaskSnapshotSource()
        val gateway = FakeSessionGateway()
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()
        val result = repository.joinWatchSession("blocked")
        assertEquals(
            LiveFollowCommandResult.Rejected(LiveFollowReplayBlockReason.REPLAY_MODE),
            result
        )
        assertEquals(0, gateway.joinCalls)
        assertEquals(LiveFollowRuntimeMode.REPLAY, repository.state.value.runtimeMode)
        assertEquals(false, repository.state.value.sideEffectsAllowed)
        assertEquals(
            LiveFollowReplayBlockReason.REPLAY_MODE,
            repository.state.value.replayBlockReason
        )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failedCommand_preservesPreviousSnapshotAndRecordsError() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource()
        val taskSource = FakeTaskSnapshotSource()
        val initialSnapshot = LiveFollowSessionGatewaySnapshot(
            sessionId = "pilot-1",
            role = LiveFollowSessionRole.PILOT,
            lifecycle = LiveFollowSessionLifecycle.ACTIVE,
            watchIdentity = null,
            directWatchAuthorized = false,
            transportAvailability = liveFollowAvailableTransport(),
            lastError = null
        )
        val gateway = FakeSessionGateway(
            initialSnapshot = initialSnapshot,
            stopResult = LiveFollowSessionGatewayResult.Failure("backend stop failed")
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()
        val result = repository.stopCurrentSession()
        advanceUntilIdle()
        assertEquals(LiveFollowCommandResult.Failure("backend stop failed"), result)
        assertEquals(1, gateway.stopCalls)
        assertEquals("pilot-1", repository.state.value.sessionId)
        assertEquals(LiveFollowSessionRole.PILOT, repository.state.value.role)
        assertEquals(LiveFollowSessionLifecycle.ACTIVE, repository.state.value.lifecycle)
        assertEquals("backend stop failed", repository.state.value.lastError)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unavailableTransport_blocksStartAndJoinWithoutCallingGateway() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource()
        val taskSource = FakeTaskSnapshotSource()
        val gateway = FakeSessionGateway(
            initialSnapshot = liveFollowGatewayIdleSnapshot(
                transportAvailability = liveFollowUnavailableTransport(
                    LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE
                )
            )
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()
        val startResult = repository.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )
        val joinResult = repository.joinWatchSession("watch-1")
        assertEquals(
            LiveFollowCommandResult.Failure(LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE),
            startResult
        )
        assertEquals(
            LiveFollowCommandResult.Failure(LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE),
            joinResult
        )
        assertEquals(0, gateway.startCalls)
        assertEquals(0, gateway.joinCalls)
        assertEquals(false, repository.state.value.transportAvailability.isAvailable)
        assertEquals(
            LIVEFOLLOW_SESSION_GATEWAY_UNAVAILABLE_MESSAGE,
            repository.state.value.transportAvailability.message
        )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startPilotSession_triggersImmediateEligibleUpload() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            snapshot = MutableStateFlow(sampleOwnshipSnapshot())
        )
        val taskSource = FakeTaskSnapshotSource(
            taskSnapshot = MutableStateFlow(sampleTaskSnapshot())
        )
        val gateway = FakeSessionGateway(
            startResult = LiveFollowSessionGatewayResult.Success(
                snapshot = LiveFollowSessionGatewaySnapshot(
                    sessionId = "pilot-1",
                    role = LiveFollowSessionRole.PILOT,
                    lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                    watchIdentity = null,
                    directWatchAuthorized = false,
                    transportAvailability = liveFollowAvailableTransport(),
                    lastError = null
                )
            )
        )
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        val result = repository.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )
        advanceUntilIdle()
        assertEquals(LiveFollowCommandResult.Success, result)
        assertEquals(1, gateway.uploadCalls)
        assertEquals(1, gateway.taskUploadCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun activePilotTaskChange_triggersTaskUploadWithoutPositionUpload() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            snapshot = MutableStateFlow(null)
        )
        val taskSnapshotFlow = MutableStateFlow<LiveFollowTaskSnapshot?>(null)
        val taskSource = FakeTaskSnapshotSource(taskSnapshot = taskSnapshotFlow)
        val gateway = FakeSessionGateway(
            initialSnapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = "pilot-1",
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null
            )
        )
        LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()
        taskSnapshotFlow.value = sampleTaskSnapshot()
        advanceUntilIdle()
        assertEquals(0, gateway.uploadCalls)
        assertEquals(2, gateway.taskUploadCalls)
        assertEquals(null, gateway.taskUploadSnapshots.first())
        assertEquals(sampleTaskSnapshot(), gateway.taskUploadSnapshots.last())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun activePilotTaskRemoval_triggersExplicitTaskClearUpload() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            snapshot = MutableStateFlow(null)
        )
        val taskSnapshotFlow = MutableStateFlow<LiveFollowTaskSnapshot?>(sampleTaskSnapshot())
        val taskSource = FakeTaskSnapshotSource(taskSnapshot = taskSnapshotFlow)
        val gateway = FakeSessionGateway(
            initialSnapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = "pilot-1",
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null
            )
        )
        LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()

        taskSnapshotFlow.value = null
        advanceUntilIdle()

        assertEquals(0, gateway.uploadCalls)
        assertEquals(2, gateway.taskUploadCalls)
        assertEquals(null, gateway.taskUploadSnapshots.last())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun replayMode_blocksUploadLoopForActivePilotState() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            snapshot = MutableStateFlow(sampleOwnshipSnapshot()),
            runtimeMode = MutableStateFlow(LiveFollowRuntimeMode.REPLAY)
        )
        val taskSource = FakeTaskSnapshotSource(
            taskSnapshot = MutableStateFlow(sampleTaskSnapshot())
        )
        val gateway = FakeSessionGateway(
            initialSnapshot = LiveFollowSessionGatewaySnapshot(
                sessionId = "pilot-1",
                role = LiveFollowSessionRole.PILOT,
                lifecycle = LiveFollowSessionLifecycle.ACTIVE,
                watchIdentity = null,
                directWatchAuthorized = false,
                transportAvailability = liveFollowAvailableTransport(),
                lastError = null
            )
        )
        LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
            taskSnapshotSource = taskSource,
            gateway = gateway
        )
        advanceUntilIdle()

        assertEquals(0, gateway.uploadCalls)
        assertEquals(0, gateway.taskUploadCalls)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope = liveFollowSessionRepositoryScope()
}
