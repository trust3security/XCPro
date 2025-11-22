package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.domain.logic.TaskValidator
import com.example.xcpro.tasks.domain.model.TaskStats
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot

/**
 * UI-facing state emitted by TaskSheetViewModel.
 */
data class TaskUiState(
    val task: Task = Task(id = "new-task"),
    val taskType: TaskType = TaskType.RACING,
    val stats: TaskStats = TaskStats(),
    val validationErrors: List<TaskValidator.ValidationError> = emptyList(),
    val targets: List<TaskTargetSnapshot> = emptyList(),
    val advanceSnapshot: com.example.xcpro.tasks.domain.logic.TaskAdvanceState.Snapshot =
        com.example.xcpro.tasks.domain.logic.TaskAdvanceState().snapshot()
)
