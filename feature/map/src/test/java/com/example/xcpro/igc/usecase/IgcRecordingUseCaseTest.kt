package com.example.xcpro.igc.usecase

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.igc.data.IgcSessionStateSnapshotStore
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
}
