package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.logic.TaskProximityDecision
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskSheetUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val proximityEvaluator: TaskProximityEvaluator
) {
    private val advanceState = TaskAdvanceState()
    private val _state = MutableStateFlow(repository.state.value.copy(advanceSnapshot = advanceState.snapshot()))
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    fun projectSnapshot(snapshot: TaskCoordinatorSnapshot) {
        repository.updateFrom(
            task = snapshot.task,
            taskType = snapshot.taskType,
            activeIndex = snapshot.activeLeg,
            racingValidationProfile = snapshot.racingValidationProfile
        )
        publishState()
    }

    fun shouldAutoAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean =
        advanceState.shouldAdvance(hasEntered, closeToTarget)

    fun evaluateProximity(
        taskType: TaskType,
        waypointRole: WaypointRole,
        aircraftLat: Double,
        aircraftLon: Double,
        targetLat: Double,
        targetLon: Double
    ): TaskProximityDecision =
        proximityEvaluator.evaluate(
            taskType = taskType,
            waypointRole = waypointRole,
            aircraftLat = aircraftLat,
            aircraftLon = aircraftLon,
            targetLat = targetLat,
            targetLon = targetLon
        )

    fun distanceMeters(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double = proximityEvaluator.distanceMeters(fromLat, fromLon, toLat, toLon)

    fun setAdvanceMode(mode: TaskAdvanceState.Mode) {
        advanceState.setMode(mode)
        publishState()
    }

    fun armAdvance(armed: Boolean) {
        advanceState.setArmed(armed)
        publishState()
    }

    fun toggleAdvanceArm() {
        advanceState.toggleArmed()
        publishState()
    }

    private fun publishState() {
        _state.value = repository.state.value.copy(
            advanceSnapshot = advanceState.snapshot()
        )
    }
}
