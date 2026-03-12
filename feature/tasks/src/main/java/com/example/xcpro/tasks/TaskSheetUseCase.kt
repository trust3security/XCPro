package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import com.example.xcpro.tasks.domain.logic.TaskProximityDecision
import com.example.xcpro.tasks.racing.RacingTaskStructureRules
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class TaskSheetUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    val state: StateFlow<TaskUiState> = repository.state

    fun updateFrom(
        task: Task,
        taskType: TaskType,
        activeIndex: Int,
        racingValidationProfile: RacingTaskStructureRules.Profile
    ) {
        repository.updateFrom(
            task = task,
            taskType = taskType,
            activeIndex = activeIndex,
            racingValidationProfile = racingValidationProfile
        )
    }

    fun setTargetParam(index: Int, param: Double) {
        repository.setTargetParam(index, param)
    }

    fun toggleTargetLock(index: Int) {
        repository.toggleTargetLock(index)
    }

    fun setTargetLock(index: Int, locked: Boolean) {
        repository.setTargetLock(index, locked)
    }

    fun shouldAutoAdvance(hasEntered: Boolean, closeToTarget: Boolean): Boolean =
        repository.shouldAutoAdvance(hasEntered, closeToTarget)

    fun evaluateProximity(
        taskType: TaskType,
        waypointRole: WaypointRole,
        aircraftLat: Double,
        aircraftLon: Double,
        targetLat: Double,
        targetLon: Double
    ): TaskProximityDecision =
        repository.evaluateProximity(
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
    ): Double = repository.distanceMeters(fromLat, fromLon, toLat, toLon)

    fun setAdvanceMode(mode: TaskAdvanceState.Mode) {
        repository.setAdvanceMode(mode)
    }

    fun armAdvance(doArm: Boolean) {
        repository.armAdvance(doArm)
    }

    fun toggleAdvanceArm() {
        repository.toggleAdvanceArm()
    }
}
