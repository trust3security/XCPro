package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.logic.TaskProximityDecision
import com.example.xcpro.tasks.domain.logic.TaskProximityEvaluator
import com.example.xcpro.tasks.racing.navigation.RacingAdvanceState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskSheetUseCase @Inject constructor(
    private val repository: TaskRepository,
    private val proximityEvaluator: TaskProximityEvaluator
) {
    private val aatAdvanceState = TaskAdvanceState()
    private var latestTaskType: TaskType = TaskType.RACING
    private var latestRacingAdvanceSnapshot: RacingAdvanceState.Snapshot = RacingAdvanceState().snapshot()
    private val _state = MutableStateFlow(repository.state.value.copy(advanceSnapshot = currentAdvanceSnapshot()))
    val state: StateFlow<TaskUiState> = _state.asStateFlow()

    fun projectSnapshot(snapshot: TaskCoordinatorSnapshot) {
        latestTaskType = snapshot.taskType
        latestRacingAdvanceSnapshot = snapshot.racingAdvanceSnapshot
        repository.updateFrom(
            task = snapshot.task,
            taskType = snapshot.taskType,
            activeIndex = snapshot.activeLeg,
            racingValidationProfile = snapshot.racingValidationProfile
        )
        publishState()
    }

    fun shouldAutoAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean =
        aatAdvanceState.shouldAdvance(hasEntered, closeToTarget)

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
        aatAdvanceState.setMode(mode)
        publishState()
    }

    fun armAdvance(armed: Boolean) {
        aatAdvanceState.setArmed(armed)
        publishState()
    }

    fun toggleAdvanceArm() {
        aatAdvanceState.toggleArmed()
        publishState()
    }

    private fun publishState() {
        _state.value = repository.state.value.copy(
            advanceSnapshot = currentAdvanceSnapshot()
        )
    }

    private fun currentAdvanceSnapshot(): TaskAdvanceUiSnapshot {
        return when (latestTaskType) {
            TaskType.RACING -> latestRacingAdvanceSnapshot.toUiSnapshot()
            TaskType.AAT -> aatAdvanceState.snapshot().toUiSnapshot()
        }
    }
}
