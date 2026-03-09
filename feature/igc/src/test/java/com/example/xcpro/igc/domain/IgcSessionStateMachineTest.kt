package com.example.xcpro.igc.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgcSessionStateMachineTest {

    @Test
    fun lifecycle_happyPath_reachesCompleted() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 1_000L,
                takeoffDebounceMs = 1_000L,
                landingDebounceMs = 1_000L,
                baselineWindowMs = 20_000L,
                finalizeTimeoutMs = 30_000L
            )
        )

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val arm = step(machine, monoMs = 1_000L, isFlying = false, onGround = true)
        assertTrue(arm.actions.any { it is IgcSessionStateMachine.Action.EnterArmed })
        assertEquals(IgcSessionStateMachine.Phase.Armed, arm.state.phase)

        step(machine, monoMs = 2_000L, isFlying = true, onGround = false)
        val start = step(machine, monoMs = 3_000L, isFlying = true, onGround = false)
        val startAction = start.actions.filterIsInstance<IgcSessionStateMachine.Action.StartRecording>().single()
        assertEquals(1L, startAction.sessionId)
        assertEquals(IgcSessionStateMachine.Phase.Recording, start.state.phase)

        step(machine, monoMs = 4_000L, isFlying = false, onGround = true)
        val finalizing = step(machine, monoMs = 5_000L, isFlying = false, onGround = true)
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, finalizing.state.phase)
        assertTrue(finalizing.actions.isEmpty())

        step(machine, monoMs = 15_000L, isFlying = false, onGround = true)
        val finalizeSignal = step(machine, monoMs = 25_000L, isFlying = false, onGround = true)
        val finalize = finalizeSignal.actions.filterIsInstance<IgcSessionStateMachine.Action.FinalizeRecording>().single()
        assertEquals(startAction.sessionId, finalize.sessionId)
        assertTrue(finalize.postFlightGroundWindowMs >= 20_000L)

        val completed = machine.onFinalizeSucceeded()
        val completedAction = completed.actions.filterIsInstance<IgcSessionStateMachine.Action.MarkCompleted>().single()
        assertEquals(startAction.sessionId, completedAction.sessionId)
        assertEquals(IgcSessionStateMachine.Phase.Completed, completed.state.phase)
        assertEquals(startAction.sessionId, completed.state.lastCompletedSessionId)
    }

    @Test
    fun debounce_shortFlyingSpike_doesNotStartRecording() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 3_000L,
                landingDebounceMs = 1_000L,
                baselineWindowMs = 20_000L,
                finalizeTimeoutMs = 20_000L
            )
        )

        val armed = step(machine, monoMs = 0L, isFlying = false, onGround = true)
        assertEquals(IgcSessionStateMachine.Phase.Armed, armed.state.phase)

        step(machine, monoMs = 1_000L, isFlying = true, onGround = false)
        val stillArmed = step(machine, monoMs = 2_500L, isFlying = false, onGround = true)

        assertEquals(IgcSessionStateMachine.Phase.Armed, stillArmed.state.phase)
        assertFalse(stillArmed.actions.any { it is IgcSessionStateMachine.Action.StartRecording })
    }

    @Test
    fun finalize_action_isIdempotent() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 2_000L,
                finalizeTimeoutMs = 5_000L
            )
        )

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val start = step(machine, monoMs = 1_000L, isFlying = true, onGround = false)
        val sessionId = start.actions
            .filterIsInstance<IgcSessionStateMachine.Action.StartRecording>()
            .single()
            .sessionId

        step(machine, monoMs = 2_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 3_000L, isFlying = false, onGround = true)
        val finalizeOnce = step(machine, monoMs = 4_000L, isFlying = false, onGround = true)
        assertEquals(1, finalizeOnce.actions.filterIsInstance<IgcSessionStateMachine.Action.FinalizeRecording>().size)

        val noDuplicateFinalize = step(machine, monoMs = 6_000L, isFlying = false, onGround = true)
        assertTrue(noDuplicateFinalize.actions.none { it is IgcSessionStateMachine.Action.FinalizeRecording })

        val complete = machine.onFinalizeSucceeded()
        assertEquals(1, complete.actions.filterIsInstance<IgcSessionStateMachine.Action.MarkCompleted>().size)
        assertEquals(sessionId, complete.state.lastCompletedSessionId)

        val noDuplicateComplete = machine.onFinalizeSucceeded()
        assertTrue(noDuplicateComplete.actions.isEmpty())
    }

    @Test
    fun nonMonotonicSignalsAreIgnored() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 1_000L,
                baselineWindowMs = 20_000L,
                finalizeTimeoutMs = 20_000L
            )
        )

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val start = step(machine, monoMs = 1L, isFlying = true, onGround = false)
        assertEquals(IgcSessionStateMachine.Phase.Recording, start.state.phase)
        val sessionId = start.actions
            .filterIsInstance<IgcSessionStateMachine.Action.StartRecording>()
            .single()
            .sessionId

        val after = step(machine, monoMs = 2L, isFlying = false, onGround = false)
        val ignored = step(machine, monoMs = 1L, isFlying = false, onGround = false)
        assertEquals(IgcSessionStateMachine.Phase.Recording, after.state.phase)
        assertEquals(IgcSessionStateMachine.Phase.Recording, ignored.state.phase)
        assertEquals(sessionId, ignored.state.activeSessionId)
        assertTrue(ignored.actions.isEmpty())
    }

    @Test
    fun touchAndGo_keepsSessionId_whenReturningFromFinalizingToRecording() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 0L,
                takeoffDebounceMs = 0L,
                landingDebounceMs = 0L,
                baselineWindowMs = 20_000L,
                finalizeTimeoutMs = 20_000L
            )
        )

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val startTransition = step(machine, monoMs = 1L, isFlying = true, onGround = false)
        val sessionId = startTransition.actions
            .filterIsInstance<IgcSessionStateMachine.Action.StartRecording>()
            .single()
            .sessionId

        step(machine, monoMs = 2L, isFlying = false, onGround = true)
        val finalizeTransition = step(machine, monoMs = 3L, isFlying = false, onGround = true)
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, finalizeTransition.state.phase)

        val touchAndGoTransition = step(machine, monoMs = 4L, isFlying = true, onGround = false)
        assertEquals(IgcSessionStateMachine.Phase.Recording, touchAndGoTransition.state.phase)
        assertEquals(sessionId, touchAndGoTransition.state.activeSessionId)
        assertTrue(
            touchAndGoTransition.actions.none {
                it is IgcSessionStateMachine.Action.FinalizeRecording
            }
        )
    }

    @Test
    fun preAndPostWindows_meetBaseline_whenDataExists() {
        val machine = IgcSessionStateMachine(
            config = IgcSessionStateMachine.Config(
                armingDebounceMs = 1_000L,
                takeoffDebounceMs = 1_000L,
                landingDebounceMs = 1_000L,
                baselineWindowMs = 20_000L,
                finalizeTimeoutMs = 40_000L
            )
        )

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        step(machine, monoMs = 1_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 7_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 14_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 21_000L, isFlying = false, onGround = true)

        step(machine, monoMs = 22_000L, isFlying = true, onGround = false)
        val start = step(machine, monoMs = 23_000L, isFlying = true, onGround = false)
        val startAction = start.actions.filterIsInstance<IgcSessionStateMachine.Action.StartRecording>().single()
        assertTrue(startAction.preFlightGroundWindowMs >= 20_000L)

        step(machine, monoMs = 24_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 25_000L, isFlying = false, onGround = true)
        step(machine, monoMs = 35_000L, isFlying = false, onGround = true)
        val finalize = step(machine, monoMs = 45_000L, isFlying = false, onGround = true)
            .actions
            .filterIsInstance<IgcSessionStateMachine.Action.FinalizeRecording>()
            .single()

        assertEquals(startAction.sessionId, finalize.sessionId)
        assertTrue(finalize.postFlightGroundWindowMs >= 20_000L)
    }

    @Test
    fun snapshotRestore_preservesFinalizingSession_andCompletesSameSession() {
        val config = IgcSessionStateMachine.Config(
            armingDebounceMs = 0L,
            takeoffDebounceMs = 0L,
            landingDebounceMs = 0L,
            baselineWindowMs = 5_000L,
            finalizeTimeoutMs = 10_000L
        )
        val machine = IgcSessionStateMachine(config)

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val start = step(machine, monoMs = 1_000L, isFlying = true, onGround = false)
        val sessionId = start.actions.filterIsInstance<IgcSessionStateMachine.Action.StartRecording>().single().sessionId

        step(machine, monoMs = 2_000L, isFlying = false, onGround = true)
        val inFinalizing = step(machine, monoMs = 3_000L, isFlying = false, onGround = true)
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, inFinalizing.state.phase)
        assertTrue(inFinalizing.actions.isEmpty())

        val snapshot = machine.snapshot()
        assertEquals(IgcSessionStateMachine.Phase.Finalizing, snapshot.state.phase)
        assertNotNull(snapshot.state.activeSessionId)

        val restored = IgcSessionStateMachine.fromSnapshot(snapshot, config)
        val finalize = step(restored, monoMs = 8_000L, isFlying = false, onGround = true)
            .actions
            .filterIsInstance<IgcSessionStateMachine.Action.FinalizeRecording>()
            .single()

        assertEquals(sessionId, finalize.sessionId)
        assertTrue(finalize.postFlightGroundWindowMs >= 5_000L)
    }

    @Test
    fun fromSnapshot_sanitizesInvalidNextSessionIdAndKeepsRecoveryMonotonic() {
        val config = IgcSessionStateMachine.Config(
            armingDebounceMs = 0L,
            takeoffDebounceMs = 0L,
            landingDebounceMs = 0L,
            baselineWindowMs = 20_000L,
            finalizeTimeoutMs = 20_000L
        )

        val snapshot = IgcSessionStateMachine.Snapshot(
            state = IgcSessionStateMachine.State(
                phase = IgcSessionStateMachine.Phase.Idle
            ),
            nextSessionId = 0L,
            lastMonoTimeMs = null,
            armingCandidateSinceMs = null,
            takeoffCandidateSinceMs = null,
            landingCandidateSinceMs = null,
            finalizingSinceMs = null,
            preFlightGroundFixMonoTimes = emptyList(),
            postFlightGroundFixMonoTimes = emptyList()
        )
        val machine = IgcSessionStateMachine.fromSnapshot(snapshot, config)

        step(machine, monoMs = 0L, isFlying = false, onGround = true)
        val start = step(machine, monoMs = 1L, isFlying = true, onGround = false)
        val startAction = start.actions.filterIsInstance<IgcSessionStateMachine.Action.StartRecording>().single()

        assertEquals(1L, startAction.sessionId)
        assertEquals(IgcSessionStateMachine.Phase.Recording, start.state.phase)
    }

    private fun step(
        machine: IgcSessionStateMachine,
        monoMs: Long,
        isFlying: Boolean,
        onGround: Boolean,
        hasFix: Boolean = true
    ): IgcSessionStateMachine.Transition =
        machine.onFlightSignal(
            IgcSessionStateMachine.FlightSignal(
                monoTimeMs = monoMs,
                isFlying = isFlying,
                onGround = onGround,
                hasFix = hasFix
            )
        )
}
