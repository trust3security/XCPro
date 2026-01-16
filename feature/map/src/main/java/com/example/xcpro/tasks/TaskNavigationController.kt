package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationDecision
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEngine
import com.example.xcpro.tasks.racing.navigation.RacingNavigationEventType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStateStore
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
internal class TaskNavigationController(
    private val taskManager: TaskManagerCoordinator,
    private val stateStore: RacingNavigationStateStore = RacingNavigationStateStore(),
    private val advanceState: RacingAdvanceState = RacingAdvanceState(),
    private val engine: RacingNavigationEngine = RacingNavigationEngine(),
    private val featureFlags: TaskFeatureFlags = TaskFeatureFlags
) {

    val racingState = stateStore.state
    val racingEvents = stateStore.events

    private var suppressManualDisarm = false
    private var legChangeListenerAdded = false

    fun bind(
        fixes: Flow<RacingNavigationFix>,
        scope: CoroutineScope
    ): Job {
        if (!legChangeListenerAdded) {
            taskManager.addLegChangeListener { newLegIndex ->
                if (suppressManualDisarm) {
                    return@addLegChangeListener
                }
                advanceState.setArmed(false)
                syncManualLegChange(newLegIndex)
            }
            legChangeListenerAdded = true
        }

        return fixes.onEach { fix ->
            if (!featureFlags.enableRacingNavigation) return@onEach
            handleFix(fix)
        }.launchIn(scope)
    }

    private fun handleFix(fix: RacingNavigationFix) {
        if (taskManager.taskType != TaskType.RACING) {
            stateStore.reset()
            return
        }

        val racingTask = taskManager.getRacingTaskManager().currentRacingTask
        val decision = engine.step(racingTask, stateStore.state.value, fix)
        applyDecision(decision)
    }

    private fun applyDecision(decision: RacingNavigationDecision) {
        val event = decision.event
        val allowAdvance = event != null &&
            featureFlags.enableRacingAutoAdvance &&
            advanceState.shouldAdvance(event.type)

        if (event != null) {
            if (event.type == RacingNavigationEventType.START) {
                advanceState.onStartAdvanced()
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
        val racingTask = taskManager.getRacingTaskManager().currentRacingTask
        val maxIndex = racingTask.waypoints.lastIndex
        val clampedIndex = if (maxIndex >= 0) newLegIndex.coerceIn(0, maxIndex) else 0
        val status = if (clampedIndex <= 0) {
            advanceState.resetToStartPhase()
            RacingNavigationStatus.PENDING_START
        } else {
            advanceState.onStartAdvanced()
            RacingNavigationStatus.IN_PROGRESS
        }

        val currentState = stateStore.state.value
        val updatedState = currentState.copy(
            status = status,
            currentLegIndex = clampedIndex,
            lastFix = null,
            lastTransitionTimeMillis = 0L
        )
        stateStore.update(updatedState, null)
    }

    fun setAdvanceMode(mode: RacingAdvanceState.Mode) {
        advanceState.setMode(mode)
    }

    fun setAdvanceArmed(armed: Boolean) {
        advanceState.setArmed(armed)
    }

    fun toggleAdvanceArmed(): Boolean = advanceState.toggleArmed()

    fun resetNavigationState(taskSignature: String = "") {
        advanceState.resetToStartPhase()
        stateStore.reset(taskSignature)
    }

    fun snapshot(): RacingAdvanceState.Snapshot = advanceState.snapshot()
}
