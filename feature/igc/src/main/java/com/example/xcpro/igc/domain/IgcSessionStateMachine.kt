package com.example.xcpro.igc.domain

import java.util.ArrayDeque

/**
 * Pure domain state machine for IGC recording session lifecycle and gating.
 *
 * Lifecycle:
 * Idle -> Armed -> Recording -> Finalizing -> Completed/Failed
 *
 * Invariants:
 * - Monotonic timestamps only.
 * - Exactly one session id per recording flight.
 * - Finalize action is emitted at most once per session.
 * - Pre/post landing ground-fix windows are tracked with sliding-window semantics.
 */
class IgcSessionStateMachine(
    private val config: Config = Config(),
    private val initialSessionId: Long = 1L
) {

    init {
        require(initialSessionId >= 1L) { "initialSessionId must be >= 1" }
    }

    data class Config(
        val armingDebounceMs: Long = 2_000L,
        val takeoffDebounceMs: Long = 2_000L,
        val landingDebounceMs: Long = 3_000L,
        val baselineWindowMs: Long = 20_000L,
        val finalizeTimeoutMs: Long = 30_000L
    ) {
        init {
            require(armingDebounceMs >= 0L) { "armingDebounceMs must be >= 0" }
            require(takeoffDebounceMs >= 0L) { "takeoffDebounceMs must be >= 0" }
            require(landingDebounceMs >= 0L) { "landingDebounceMs must be >= 0" }
            require(baselineWindowMs > 0L) { "baselineWindowMs must be > 0" }
            require(finalizeTimeoutMs > 0L) { "finalizeTimeoutMs must be > 0" }
        }
    }

    enum class Phase {
        Idle,
        Armed,
        Recording,
        Finalizing,
        Completed,
        Failed
    }

    data class FlightSignal(
        val monoTimeMs: Long,
        val isFlying: Boolean,
        val onGround: Boolean,
        val hasFix: Boolean
    ) {
        init {
            require(monoTimeMs >= 0L) { "monoTimeMs must be >= 0" }
        }
    }

    data class State(
        val phase: Phase = Phase.Idle,
        val activeSessionId: Long? = null,
        val preFlightGroundWindowMs: Long = 0L,
        val postFlightGroundWindowMs: Long = 0L,
        val finalizeDispatched: Boolean = false,
        val lastCompletedSessionId: Long? = null,
        val lastFailedSessionId: Long? = null,
        val failureReason: String? = null
    )

    sealed interface Action {
        data class EnterArmed(val monoTimeMs: Long) : Action
        data class StartRecording(
            val sessionId: Long,
            val preFlightGroundWindowMs: Long
        ) : Action

        data class FinalizeRecording(
            val sessionId: Long,
            val postFlightGroundWindowMs: Long
        ) : Action

        data class MarkCompleted(val sessionId: Long) : Action
        data class MarkFailed(val sessionId: Long, val reason: String) : Action
    }

    data class Transition(
        val state: State,
        val actions: List<Action> = emptyList()
    )

    data class Snapshot(
        val state: State,
        val nextSessionId: Long,
        val lastMonoTimeMs: Long?,
        val armingCandidateSinceMs: Long?,
        val takeoffCandidateSinceMs: Long?,
        val landingCandidateSinceMs: Long?,
        val finalizingSinceMs: Long?,
        val preFlightGroundFixMonoTimes: List<Long>,
        val postFlightGroundFixMonoTimes: List<Long>
    )

    private var state = State()
    private var nextSessionId = initialSessionId
    private var lastMonoTimeMs: Long? = null
    private var armingCandidateSinceMs: Long? = null
    private var takeoffCandidateSinceMs: Long? = null
    private var landingCandidateSinceMs: Long? = null
    private var finalizingSinceMs: Long? = null
    private val preFlightGroundFixMonoTimes = ArrayDeque<Long>()
    private val postFlightGroundFixMonoTimes = ArrayDeque<Long>()

    fun currentState(): State = state

    fun onFlightSignal(signal: FlightSignal): Transition {
        val previousMono = lastMonoTimeMs
        if (previousMono != null && signal.monoTimeMs < previousMono) {
            return Transition(state = state)
        }
        lastMonoTimeMs = signal.monoTimeMs

        captureGroundFixWindow(signal)

        val actions = mutableListOf<Action>()
        when (state.phase) {
            Phase.Idle -> handleIdle(signal, actions)
            Phase.Armed -> handleArmed(signal, actions)
            Phase.Recording -> handleRecording(signal, actions)
            Phase.Finalizing -> handleFinalizing(signal, actions)
            Phase.Completed -> handleTerminalAsIdle(signal, actions)
            Phase.Failed -> handleTerminalAsIdle(signal, actions)
        }
        return Transition(state = state, actions = actions)
    }

    fun onFinalizeSucceeded(): Transition {
        if (state.phase != Phase.Finalizing) {
            return Transition(state = state)
        }
        val sessionId = state.activeSessionId ?: return Transition(state = state)
        state = state.copy(
            phase = Phase.Completed,
            activeSessionId = null,
            finalizeDispatched = false,
            lastCompletedSessionId = sessionId,
            failureReason = null
        )
        landingCandidateSinceMs = null
        finalizingSinceMs = null
        return Transition(
            state = state,
            actions = listOf(Action.MarkCompleted(sessionId))
        )
    }

    fun onFinalizeFailed(reason: String): Transition {
        if (state.phase != Phase.Finalizing) {
            return Transition(state = state)
        }
        val sessionId = state.activeSessionId ?: return Transition(state = state)
        state = state.copy(
            phase = Phase.Failed,
            activeSessionId = null,
            finalizeDispatched = false,
            lastFailedSessionId = sessionId,
            failureReason = reason
        )
        landingCandidateSinceMs = null
        finalizingSinceMs = null
        return Transition(
            state = state,
            actions = listOf(Action.MarkFailed(sessionId, reason))
        )
    }

    fun snapshot(): Snapshot =
        Snapshot(
            state = state,
            nextSessionId = nextSessionId,
            lastMonoTimeMs = lastMonoTimeMs,
            armingCandidateSinceMs = armingCandidateSinceMs,
            takeoffCandidateSinceMs = takeoffCandidateSinceMs,
            landingCandidateSinceMs = landingCandidateSinceMs,
            finalizingSinceMs = finalizingSinceMs,
            preFlightGroundFixMonoTimes = preFlightGroundFixMonoTimes.toList(),
            postFlightGroundFixMonoTimes = postFlightGroundFixMonoTimes.toList()
        )

    private fun captureGroundFixWindow(signal: FlightSignal) {
        if (!signal.hasFix || !signal.onGround) return
        val queue = when (state.phase) {
            Phase.Recording, Phase.Finalizing -> postFlightGroundFixMonoTimes
            else -> preFlightGroundFixMonoTimes
        }
        queue.addLast(signal.monoTimeMs)
        trimWindow(queue, signal.monoTimeMs)
        val postWindow = windowMs(postFlightGroundFixMonoTimes)
        state = state.copy(postFlightGroundWindowMs = postWindow)
    }

    private fun handleIdle(signal: FlightSignal, actions: MutableList<Action>) {
        if (!canArm(signal)) {
            armingCandidateSinceMs = null
            return
        }
        val candidateStart = armingCandidateSinceMs ?: signal.monoTimeMs.also {
            armingCandidateSinceMs = it
        }
        if (signal.monoTimeMs - candidateStart < config.armingDebounceMs) {
            return
        }
        state = state.copy(
            phase = Phase.Armed,
            preFlightGroundWindowMs = windowMs(preFlightGroundFixMonoTimes),
            postFlightGroundWindowMs = 0L,
            failureReason = null
        )
        takeoffCandidateSinceMs = null
        actions += Action.EnterArmed(signal.monoTimeMs)
    }

    private fun handleTerminalAsIdle(signal: FlightSignal, actions: MutableList<Action>) {
        handleIdle(signal, actions)
    }

    private fun handleArmed(signal: FlightSignal, actions: MutableList<Action>) {
        if (!signal.isFlying) {
            takeoffCandidateSinceMs = null
            return
        }
        val candidateStart = takeoffCandidateSinceMs ?: signal.monoTimeMs.also {
            takeoffCandidateSinceMs = it
        }
        if (signal.monoTimeMs - candidateStart < config.takeoffDebounceMs) {
            return
        }
        val sessionId = nextSessionId
        nextSessionId += 1L
        val preWindow = windowMs(preFlightGroundFixMonoTimes)
        postFlightGroundFixMonoTimes.clear()
        state = state.copy(
            phase = Phase.Recording,
            activeSessionId = sessionId,
            preFlightGroundWindowMs = preWindow,
            postFlightGroundWindowMs = 0L,
            finalizeDispatched = false,
            failureReason = null
        )
        landingCandidateSinceMs = null
        finalizingSinceMs = null
        actions += Action.StartRecording(
            sessionId = sessionId,
            preFlightGroundWindowMs = preWindow
        )
    }

    private fun handleRecording(signal: FlightSignal, actions: MutableList<Action>) {
        if (signal.isFlying || !signal.onGround) {
            landingCandidateSinceMs = null
            postFlightGroundFixMonoTimes.clear()
            state = state.copy(postFlightGroundWindowMs = 0L)
            return
        }
        val candidateStart = landingCandidateSinceMs ?: signal.monoTimeMs.also {
            landingCandidateSinceMs = it
        }
        if (signal.monoTimeMs - candidateStart < config.landingDebounceMs) {
            return
        }
        state = state.copy(
            phase = Phase.Finalizing,
            postFlightGroundWindowMs = windowMs(postFlightGroundFixMonoTimes),
            finalizeDispatched = false
        )
        finalizingSinceMs = signal.monoTimeMs
        tryDispatchFinalize(signal, actions)
    }

    private fun handleFinalizing(signal: FlightSignal, actions: MutableList<Action>) {
        if (signal.isFlying) {
            // AI-NOTE: Abort finalizing on resumed flight so a touch-and-go remains one active session.
            state = state.copy(
                phase = Phase.Recording,
                finalizeDispatched = false,
                postFlightGroundWindowMs = 0L
            )
            landingCandidateSinceMs = null
            finalizingSinceMs = null
            postFlightGroundFixMonoTimes.clear()
            return
        }
        tryDispatchFinalize(signal, actions)
    }

    private fun tryDispatchFinalize(signal: FlightSignal, actions: MutableList<Action>) {
        if (state.finalizeDispatched) return
        val sessionId = state.activeSessionId ?: return
        val startedAt = finalizingSinceMs ?: signal.monoTimeMs.also { finalizingSinceMs = it }
        val waitedMs = (signal.monoTimeMs - startedAt).coerceAtLeast(0L)
        val postWindowMs = windowMs(postFlightGroundFixMonoTimes)
        val hasEnoughPostWindow = postWindowMs >= config.baselineWindowMs
        val timedOut = waitedMs >= config.finalizeTimeoutMs
        if (!hasEnoughPostWindow && !timedOut) return
        state = state.copy(
            finalizeDispatched = true,
            postFlightGroundWindowMs = postWindowMs
        )
        actions += Action.FinalizeRecording(
            sessionId = sessionId,
            postFlightGroundWindowMs = postWindowMs
        )
    }

    private fun canArm(signal: FlightSignal): Boolean =
        signal.hasFix && signal.onGround && !signal.isFlying

    private fun trimWindow(queue: ArrayDeque<Long>, latestMonoMs: Long) {
        val minKeepMonoMs = latestMonoMs - config.baselineWindowMs
        while (queue.isNotEmpty() && queue.first < minKeepMonoMs) {
            queue.removeFirst()
        }
    }

    private fun windowMs(queue: ArrayDeque<Long>): Long {
        val first = queue.firstOrNull() ?: return 0L
        val last = queue.lastOrNull() ?: return 0L
        return (last - first).coerceAtLeast(0L)
    }

    private fun ArrayDeque<Long>.firstOrNull(): Long? =
        if (isEmpty()) null else first

    private fun ArrayDeque<Long>.lastOrNull(): Long? =
        if (isEmpty()) null else last

    companion object {
        fun fromSnapshot(
            snapshot: Snapshot,
            config: Config = Config()
        ): IgcSessionStateMachine {
            val nextSessionId = snapshot.nextSessionId.coerceAtLeast(1L)
            val machine = IgcSessionStateMachine(
                config = config,
                initialSessionId = nextSessionId
            )
            machine.state = snapshot.state
            machine.nextSessionId = nextSessionId
            machine.lastMonoTimeMs = snapshot.lastMonoTimeMs
            machine.armingCandidateSinceMs = snapshot.armingCandidateSinceMs
            machine.takeoffCandidateSinceMs = snapshot.takeoffCandidateSinceMs
            machine.landingCandidateSinceMs = snapshot.landingCandidateSinceMs
            machine.finalizingSinceMs = snapshot.finalizingSinceMs
            machine.preFlightGroundFixMonoTimes.addAll(snapshot.preFlightGroundFixMonoTimes)
            machine.postFlightGroundFixMonoTimes.addAll(snapshot.postFlightGroundFixMonoTimes)
            return machine
        }
    }
}
