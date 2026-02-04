package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskAdvanceState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class TaskSheetUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    val state: StateFlow<TaskUiState> = repository.state

    fun updateFrom(task: Task, taskType: TaskType, activeIndex: Int) {
        repository.updateFrom(task, taskType, activeIndex)
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
