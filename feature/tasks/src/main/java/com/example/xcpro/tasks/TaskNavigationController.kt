package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.RacingStartCustomParams
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationDecision
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEvent
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
import com.example.xcpro.tasks.racing.toRacingWaypoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Task navigation controller (router only).
 * Consumes RacingNavigationFix samples and applies the Racing navigation engine
 * when task type is RACING.
 */
class TaskNavigationController(
    private val taskManager: TaskManagerCoordinator,
    private val stateStore: RacingNavigationStateStore,
    private val engine: RacingNavigationEngine,
    private val featureFlags: TaskFeatureFlags
) {

    val racingState = stateStore.state
    val racingEvents = stateStore.events

    private var suppressManualDisarm = false
    private var activeBindingCount = 0
    private var legChangeListener: ((Int) -> Unit)? = null

    fun bind(
        fixes: Flow<RacingNavigationFix>,
        scope: CoroutineScope
    ): Job {
        ensureLegChangeListener()
        activeBindingCount += 1

        return fixes.onEach { fix ->
            if (!featureFlags.enableRacingNavigation) return@onEach
            handleFix(fix)
        }.launchIn(scope).also { job ->
            job.invokeOnCompletion {
                releaseLegChangeListener()
            }
        }
    }

    private fun handleFix(fix: RacingNavigationFix) {
        if (taskManager.taskType != TaskType.RACING) {
            stateStore.reset()
            return
        }

        val task = taskManager.currentTask
        val startRules = task.waypoints.firstOrNull()?.let { waypoint ->
            RacingStartCustomParams.from(waypoint.customParameters)
        } ?: RacingStartCustomParams()
        val finishRules = task.waypoints.lastOrNull()?.let { waypoint ->
            RacingFinishCustomParams.from(waypoint.customParameters)
        } ?: RacingFinishCustomParams()
        val decision = engine.step(
            taskWaypoints = task.toRacingWaypoints(),
            previousState = stateStore.state.value,
            fix = fix,
            startRules = startRules,
            finishRules = finishRules,
            startArmed = taskManager.racingAdvanceSnapshot().armState == RacingAdvanceState.ArmState.START_ARMED
        )
        applyDecision(decision)
    }

    private fun applyDecision(decision: RacingNavigationDecision) {
        val event = decision.event
        val allowAdvance = event != null &&
            featureFlags.enableRacingAutoAdvance &&
            taskManager.shouldRacingAdvance(event.type) &&
            eventSupportsAutoAdvance(event)

        if (event != null) {
            if (event.type == RacingNavigationEventType.START) {
                taskManager.onRacingStartAccepted()
            }
            stateStore.update(decision.state, event)
        } else {
            stateStore.update(decision.state, null)
        }

        if (allowAdvance) {
            suppressManualDisarm = true
            try {
                taskManager.setActiveLeg(decision.state.currentLegIndex)
            } finally {
                suppressManualDisarm = false
            }
        }
    }

    private fun syncManualLegChange(newLegIndex: Int) {
        if (taskManager.taskType != TaskType.RACING) {
            return
        }
        val maxIndex = taskManager.currentTask.waypoints.lastIndex
        val clampedIndex = if (maxIndex >= 0) newLegIndex.coerceIn(0, maxIndex) else 0
        val status = if (clampedIndex <= 0) {
            taskManager.resetRacingAdvanceToStartPhase()
            RacingNavigationStatus.PENDING_START
        } else {
            taskManager.onRacingStartAccepted()
            RacingNavigationStatus.IN_PROGRESS
        }

        val currentState = stateStore.state.value
        val updatedState = if (clampedIndex <= 0) {
            RacingNavigationState(taskSignature = currentState.taskSignature)
        } else {
            currentState.copy(
                status = status,
                currentLegIndex = clampedIndex,
                lastFix = null,
                lastTransitionTimeMillis = 0L,
                creditedTurnpointsByLeg = currentState.creditedTurnpointsByLeg
                    .filterKeys { legIndex -> legIndex < clampedIndex },
                creditedFinish = null,
                hasObservedRequiredApproachSideForActiveLeg = false,
                reportedNearMissTurnpointLegIndices = emptySet(),
                finishOutcome = null,
                finishUsedStraightInException = false,
                finishCrossingTimeMillis = null,
                finishLandingDeadlineMillis = null,
                finishLandingStopStartTimeMillis = null,
                finishBoundaryStopStartTimeMillis = null,
                lastFixBeforeFinishClose = null
            )
        }
        stateStore.update(updatedState, null)
    }

    fun setAdvanceMode(mode: RacingAdvanceState.Mode) {
        taskManager.setRacingAdvanceMode(mode)
    }

    fun setAdvanceArmed(armed: Boolean) {
        taskManager.setRacingAdvanceArmed(armed)
    }

    fun toggleAdvanceArmed(): Boolean = taskManager.toggleRacingAdvanceArmed()

    fun resetNavigationState(taskSignature: String = "") {
        taskManager.resetRacingAdvanceToStartPhase()
        stateStore.reset(taskSignature)
    }

    fun snapshot(): RacingAdvanceState.Snapshot = taskManager.racingAdvanceSnapshot()

    fun restoreReplaySnapshot(
        selectedLeg: Int,
        navigationState: RacingNavigationState,
        advanceSnapshot: RacingAdvanceState.Snapshot
    ) {
        if (taskManager.taskType != TaskType.RACING) {
            return
        }
        val maxIndex = taskManager.currentTask.waypoints.lastIndex
        val clampedSelectedLeg = if (maxIndex >= 0) {
            selectedLeg.coerceIn(0, maxIndex)
        } else {
            0
        }

        suppressManualDisarm = true
        try {
            taskManager.setActiveLeg(clampedSelectedLeg)
            taskManager.restoreRacingAdvanceSnapshot(advanceSnapshot)
            stateStore.restore(navigationState)
        } finally {
            suppressManualDisarm = false
        }
    }

    private fun ensureLegChangeListener() {
        if (legChangeListener != null) {
            return
        }
        val listener: (Int) -> Unit = { newLegIndex ->
            if (!suppressManualDisarm) {
                taskManager.setRacingAdvanceArmed(false)
                syncManualLegChange(newLegIndex)
            }
        }
        taskManager.addLegChangeListener(listener)
        legChangeListener = listener
    }

    private fun releaseLegChangeListener() {
        if (activeBindingCount > 0) {
            activeBindingCount -= 1
        }
        if (activeBindingCount != 0) {
            return
        }
        legChangeListener?.let(taskManager::removeLegChangeListener)
        legChangeListener = null
    }

    private fun eventSupportsAutoAdvance(event: RacingNavigationEvent): Boolean {
        return when (event.type) {
            RacingNavigationEventType.START,
            RacingNavigationEventType.TURNPOINT -> event.crossingEvidence != null
            RacingNavigationEventType.FINISH,
            RacingNavigationEventType.START_REJECTED,
            RacingNavigationEventType.TURNPOINT_NEAR_MISS -> true
        }
    }
}
