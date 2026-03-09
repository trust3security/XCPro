package com.example.xcpro.igc.usecase

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.igc.NoopIgcRecordingActionSink
import com.example.xcpro.igc.data.IgcFinalizeRequest
import com.example.xcpro.igc.data.IgcFinalizeResult
import com.example.xcpro.igc.data.IgcFlightLogRepository
import com.example.xcpro.igc.data.IgcSessionStateSnapshotStore
import com.example.xcpro.igc.domain.IgcRecoveryErrorCode
import com.example.xcpro.igc.domain.IgcRecoveryResult
import com.example.xcpro.igc.domain.IgcSessionStateMachine
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.domain.FlyingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IgcRecordingUseCaseTest {

    @Test
    fun recordingSnapshot_resumesOnStartup_withoutRepositoryRecovery() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also {
            it.saveSnapshot(recordingSnapshot(sessionId = 21L, nextSessionId = 22L))
        }
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Recovered("2025-03-09-XCP-000021-01.IGC")
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertNull(recoveryRepository.lastRecoveredSessionId)
        assertNotNull(store.loadSnapshot())
        assertEquals(IgcSessionStateMachine.Phase.Recording, useCase.state.value.phase)
        assertEquals(21L, useCase.state.value.activeSessionId)
    }

    @Test
    fun finalizingSnapshot_withRecoveryFailure_startsFresh_andClearsSnapshot() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also {
            it.saveSnapshot(finalizingSnapshot(sessionId = 31L, nextSessionId = 32L))
        }
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.STAGING_CORRUPT,
                message = "staging corrupt"
            )
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertEquals(31L, recoveryRepository.lastRecoveredSessionId)
        assertNull(store.loadSnapshot())
        assertEquals(IgcSessionStateMachine.Phase.Idle, useCase.state.value.phase)
    }

    @Test
    fun k1_restartAfterSnapshotPersistBeforeStagedWrite_clearsFinalizingSnapshotAndStartsFresh() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also {
            it.saveSnapshot(finalizingSnapshot(sessionId = 33L, nextSessionId = 34L))
        }
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Failure(
                code = IgcRecoveryErrorCode.STAGING_MISSING,
                message = "staging missing"
            )
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertEquals(33L, recoveryRepository.lastRecoveredSessionId)
        assertNull(store.loadSnapshot())
        assertEquals(IgcSessionStateMachine.Phase.Idle, useCase.state.value.phase)
    }

    @Test
    fun finalizingSnapshot_withUnsupportedRecovery_restoresSnapshot() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also {
            it.saveSnapshot(finalizingSnapshot(sessionId = 35L, nextSessionId = 36L))
        }
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.NoRecoveryWork("repository unsupported")
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertEquals(35L, recoveryRepository.lastRecoveredSessionId)
        assertNotNull(store.loadSnapshot())
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, useCase.state.value.phase)
        assertEquals(35L, useCase.state.value.activeSessionId)
    }

    @Test
    fun onFinalizeSucceeded_deletesRecoveryArtifacts_forRecoveredSessionLifecycle() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also {
            it.saveSnapshot(finalizingSnapshot(sessionId = 41L, nextSessionId = 42L))
        }
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.NoRecoveryWork("test restores snapshot")
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertEquals(IgcSessionStateMachine.Phase.Finalizing, useCase.state.value.phase)

        useCase.onFinalizeSucceeded()
        advanceUntilIdle()

        assertEquals(listOf(41L), recoveryRepository.deletedRecoveryArtifactSessionIds)
        assertNull(store.loadSnapshot())
        assertEquals(IgcSessionStateMachine.Phase.Completed, useCase.state.value.phase)
    }

    @Test
    fun terminalSnapshot_restore_removesTerminalAndReusesNextSessionId() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore()
        store.saveSnapshot(
            IgcSessionStateMachine.Snapshot(
                state = IgcSessionStateMachine.State(phase = IgcSessionStateMachine.Phase.Completed),
                nextSessionId = 11L,
                lastMonoTimeMs = null,
                armingCandidateSinceMs = null,
                takeoffCandidateSinceMs = null,
                landingCandidateSinceMs = null,
                finalizingSinceMs = null,
                preFlightGroundFixMonoTimes = emptyList(),
                postFlightGroundFixMonoTimes = emptyList()
            )
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 1L,
                finalizeTimeoutMs = 1_000L
            )
        )

        assertNull("Terminal snapshots must be cleared on restore", store.loadSnapshot())

        clock.setMonoMs(1_000L)
        source.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        source.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        val state = useCase.state.value
        assertEquals(IgcSessionStateMachine.Phase.Recording, state.phase)
        assertEquals(11L, state.activeSessionId)
        assertNotNull(store.loadSnapshot())
    }

    @Test
    fun inFlightSnapshot_isPersisted_andClearedOnTerminalCompletion() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore()
        val config = IgcSessionStateMachine.Config(
            armingDebounceMs = 0L,
            takeoffDebounceMs = 0L,
            landingDebounceMs = 0L,
            baselineWindowMs = 1L,
            finalizeTimeoutMs = 1_000L
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = config
        )

        clock.setMonoMs(1_000L)
        source.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(2_000L)
        source.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()

        val recordingState = useCase.state.value
        val recordingSnapshot = store.loadSnapshot()
        assertEquals(IgcSessionStateMachine.Phase.Recording, recordingState.phase)
        assertEquals(recordingState.activeSessionId, recordingSnapshot?.state?.activeSessionId)

        clock.setMonoMs(3_000L)
        source.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        clock.setMonoMs(4_000L)
        source.value = FlyingState(isFlying = false, onGround = true)
        advanceUntilIdle()
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, useCase.state.value.phase)

        useCase.onFinalizeSucceeded()
        advanceUntilIdle()
        assertEquals(IgcSessionStateMachine.Phase.Completed, useCase.state.value.phase)
        assertNull("Terminal state should clear persisted snapshot", store.loadSnapshot())
    }

    @Test
    fun restoredFinalizingSnapshot_keepsActiveSessionAndReturnsToRecordingOnTouchAndGo() = runTest {
        val config = IgcSessionStateMachine.Config(
            armingDebounceMs = 0L,
            takeoffDebounceMs = 0L,
            landingDebounceMs = 1_000L,
            baselineWindowMs = 20_000L,
            finalizeTimeoutMs = 30_000L
        )
        val preMachine = IgcSessionStateMachine(config)
        emit(preMachine, 0L, false, true)
        emit(preMachine, 1_000L, false, true)
        emit(preMachine, 2_000L, true, false)
        emit(preMachine, 2_500L, false, true)
        val finalizingSnapshot = run {
            emit(preMachine, 3_500L, false, true)
            preMachine.snapshot()
        }

        val finalizingSessionId = finalizingSnapshot.state.activeSessionId
        assertNotNull(finalizingSessionId)
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, finalizingSnapshot.state.phase)

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 4_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore().also { it.saveSnapshot(finalizingSnapshot) }
        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = config
        )

        val restoredState = useCase.state.value
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, restoredState.phase)
        assertEquals(finalizingSessionId, restoredState.activeSessionId)

        clock.setMonoMs(5_000L)
        source.value = FlyingState(isFlying = true, onGround = false)
        advanceUntilIdle()
        assertEquals(IgcSessionStateMachine.Phase.Recording, useCase.state.value.phase)
        assertEquals(finalizingSessionId, useCase.state.value.activeSessionId)
    }

    @Test
    fun k7_restartAfterSnapshotClear_doesNotInvokeRepositoryRecovery() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 100L, wallMs = 1_741_483_200_000L)
        val source = MutableStateFlow(FlyingState())
        val flightDataRepository = FlightDataRepository()
        val store = InMemorySnapshotStore()
        val recoveryRepository = FakeFlightLogRepository(
            recoveryResult = IgcRecoveryResult.Recovered("2025-03-09-XCP-000071-01.IGC")
        )

        val useCase = IgcRecordingUseCase(
            flightStateSource = flightStateSource(source),
            flightDataRepository = flightDataRepository,
            clock = clock,
            snapshotStore = store,
            defaultDispatcher = dispatcher,
            config = zeroDebounceConfig(),
            recordingActionSink = NoopIgcRecordingActionSink,
            flightLogRepository = recoveryRepository
        )

        assertNull(recoveryRepository.lastRecoveredSessionId)
        assertEquals(IgcSessionStateMachine.Phase.Idle, useCase.state.value.phase)
        assertNull(store.loadSnapshot())
    }

    private fun emit(
        machine: IgcSessionStateMachine,
        monoTimeMs: Long,
        isFlying: Boolean,
        onGround: Boolean
    ) {
        machine.onFlightSignal(
            IgcSessionStateMachine.FlightSignal(
                monoTimeMs = monoTimeMs,
                isFlying = isFlying,
                onGround = onGround,
                hasFix = true
            )
        )
    }

    private fun flightStateSource(
        source: MutableStateFlow<FlyingState>
    ): FlightStateSource = object : FlightStateSource {
        override val flightState = source
    }

    private fun zeroDebounceConfig(): IgcSessionStateMachine.Config {
        return IgcSessionStateMachine.Config(
            armingDebounceMs = 0L,
            takeoffDebounceMs = 0L,
            landingDebounceMs = 0L,
            baselineWindowMs = 1L,
            finalizeTimeoutMs = 1_000L
        )
    }

    private fun recordingSnapshot(sessionId: Long, nextSessionId: Long): IgcSessionStateMachine.Snapshot {
        return IgcSessionStateMachine.Snapshot(
            state = IgcSessionStateMachine.State(
                phase = IgcSessionStateMachine.Phase.Recording,
                activeSessionId = sessionId
            ),
            nextSessionId = nextSessionId,
            lastMonoTimeMs = null,
            armingCandidateSinceMs = null,
            takeoffCandidateSinceMs = null,
            landingCandidateSinceMs = null,
            finalizingSinceMs = null,
            preFlightGroundFixMonoTimes = emptyList(),
            postFlightGroundFixMonoTimes = emptyList()
        )
    }

    private fun finalizingSnapshot(sessionId: Long, nextSessionId: Long): IgcSessionStateMachine.Snapshot {
        return IgcSessionStateMachine.Snapshot(
            state = IgcSessionStateMachine.State(
                phase = IgcSessionStateMachine.Phase.Finalizing,
                activeSessionId = sessionId
            ),
            nextSessionId = nextSessionId,
            lastMonoTimeMs = null,
            armingCandidateSinceMs = null,
            takeoffCandidateSinceMs = null,
            landingCandidateSinceMs = null,
            finalizingSinceMs = 10L,
            preFlightGroundFixMonoTimes = emptyList(),
            postFlightGroundFixMonoTimes = emptyList()
        )
    }

    private class InMemorySnapshotStore : IgcSessionStateSnapshotStore {
        private var snapshot: IgcSessionStateMachine.Snapshot? = null

        override fun saveSnapshot(snapshot: IgcSessionStateMachine.Snapshot) {
            this.snapshot = snapshot
        }

        override fun loadSnapshot(): IgcSessionStateMachine.Snapshot? = snapshot

        override fun clearSnapshot() {
            snapshot = null
        }
    }

    private class FakeFlightLogRepository(
        private val recoveryResult: IgcRecoveryResult
    ) : IgcFlightLogRepository {
        var lastRecoveredSessionId: Long? = null
        val deletedRecoveryArtifactSessionIds = mutableListOf<Long>()

        override fun finalizeSession(request: IgcFinalizeRequest): IgcFinalizeResult {
            error("finalizeSession should not be called in this test")
        }

        override fun recoverSession(sessionId: Long): IgcRecoveryResult {
            lastRecoveredSessionId = sessionId
            return recoveryResult
        }

        override fun deleteRecoveryArtifacts(sessionId: Long) {
            deletedRecoveryArtifactSessionIds += sessionId
        }
    }
}
