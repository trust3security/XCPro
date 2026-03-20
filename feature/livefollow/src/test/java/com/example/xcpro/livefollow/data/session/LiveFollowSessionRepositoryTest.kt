package com.example.xcpro.livefollow.data.session

import com.example.xcpro.livefollow.data.ownship.LiveOwnshipSnapshotSource
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentity
import com.example.xcpro.livefollow.model.LiveFollowAircraftIdentityType
import com.example.xcpro.livefollow.model.LiveFollowConfidence
import com.example.xcpro.livefollow.model.LiveFollowIdentityProfile
import com.example.xcpro.livefollow.model.LiveOwnshipSnapshot
import com.example.xcpro.livefollow.model.LiveOwnshipSourceLabel
import com.example.xcpro.livefollow.model.LiveFollowValueQuality
import com.example.xcpro.livefollow.model.LiveFollowValueState
import com.example.xcpro.livefollow.model.liveFollowAvailableTransport
import com.example.xcpro.livefollow.model.liveFollowUnavailableTransport
import com.example.xcpro.livefollow.state.LiveFollowReplayBlockReason
import com.example.xcpro.livefollow.state.LiveFollowRuntimeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun replayMode_blocksGatewaySideEffects() = runTest {
        val scope = repoScope()
        try {
        val ownshipSource = FakeOwnshipSnapshotSource(
            runtimeMode = MutableStateFlow(LiveFollowRuntimeMode.REPLAY)
        )
        val gateway = FakeSessionGateway()
        val repository = LiveFollowSessionRepository(
            scope = scope,
            ownshipSnapshotSource = ownshipSource,
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
            gateway = gateway
        )

        val result = repository.startPilotSession(
            StartPilotLiveFollowSession(pilotIdentity = identityProfile("AB12CD"))
        )
        advanceUntilIdle()

        assertEquals(LiveFollowCommandResult.Success, result)
        assertEquals(1, gateway.uploadCalls)
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
            gateway = gateway
        )
        advanceUntilIdle()

        assertEquals(0, gateway.uploadCalls)
        } finally {
            scope.cancel()
        }
    }

    private fun TestScope.repoScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(testScheduler))

    private fun identityProfile(hex: String): LiveFollowIdentityProfile =
        LiveFollowIdentityProfile(
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = hex,
                verified = true
            )
        )

    private class FakeOwnshipSnapshotSource(
        override val snapshot: StateFlow<LiveOwnshipSnapshot?> = MutableStateFlow(null),
        override val runtimeMode: MutableStateFlow<LiveFollowRuntimeMode> =
            MutableStateFlow(LiveFollowRuntimeMode.LIVE)
    ) : LiveOwnshipSnapshotSource

    private class FakeSessionGateway(
        initialSnapshot: LiveFollowSessionGatewaySnapshot = liveFollowGatewayIdleSnapshot(),
        var startResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
            liveFollowGatewayIdleSnapshot()
        ),
        var stopResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
            liveFollowGatewayIdleSnapshot()
        ),
        var joinResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
            liveFollowGatewayIdleSnapshot()
        ),
        var leaveResult: LiveFollowSessionGatewayResult = LiveFollowSessionGatewayResult.Success(
            liveFollowGatewayIdleSnapshot()
        )
    ) : LiveFollowSessionGateway {
        override val sessionState = MutableStateFlow(initialSnapshot)
        var startCalls: Int = 0
        var stopCalls: Int = 0
        var joinCalls: Int = 0
        var leaveCalls: Int = 0
        var uploadCalls: Int = 0

        override suspend fun startPilotSession(
            request: StartPilotLiveFollowSession
        ): LiveFollowSessionGatewayResult {
            startCalls += 1
            return startResult.also(::applyResult)
        }

        override suspend fun stopCurrentSession(sessionId: String): LiveFollowSessionGatewayResult {
            stopCalls += 1
            return stopResult.also(::applyResult)
        }

        override suspend fun joinWatchSession(sessionId: String): LiveFollowSessionGatewayResult {
            joinCalls += 1
            return joinResult.also(::applyResult)
        }

        override suspend fun leaveSession(sessionId: String): LiveFollowSessionGatewayResult {
            leaveCalls += 1
            return leaveResult.also(::applyResult)
        }

        override suspend fun uploadPilotPosition(
            snapshot: LiveOwnshipSnapshot
        ): LiveFollowPilotPositionUploadResult {
            uploadCalls += 1
            return LiveFollowPilotPositionUploadResult.Uploaded
        }

        private fun applyResult(result: LiveFollowSessionGatewayResult) {
            if (result is LiveFollowSessionGatewayResult.Success) {
                sessionState.value = result.snapshot
            }
        }
    }

    private fun sampleOwnshipSnapshot(): LiveOwnshipSnapshot {
        return LiveOwnshipSnapshot(
            latitudeDeg = -33.9,
            longitudeDeg = 151.2,
            gpsAltitudeMslMeters = 500.0,
            pressureAltitudeMslMeters = 495.0,
            groundSpeedMs = 12.0,
            trackDeg = 180.0,
            verticalSpeedMs = 1.2,
            fixMonoMs = 10_000L,
            fixWallMs = 20_000L,
            positionQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            verticalQuality = LiveFollowValueQuality(
                state = LiveFollowValueState.VALID,
                confidence = LiveFollowConfidence.HIGH
            ),
            canonicalIdentity = LiveFollowAircraftIdentity.create(
                type = LiveFollowAircraftIdentityType.FLARM,
                rawValue = "AB12CD",
                verified = true
            ),
            sourceLabel = LiveOwnshipSourceLabel.LIVE_FLIGHT_RUNTIME
        )
    }
}
