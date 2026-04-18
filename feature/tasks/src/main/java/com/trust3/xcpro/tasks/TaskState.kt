package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.domain.logic.TaskValidator
import com.trust3.xcpro.tasks.domain.model.TaskStats
import com.trust3.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.trust3.xcpro.tasks.racing.RacingTaskStructureRules

/**
 * UI-facing state emitted by TaskSheetViewModel.
 */
data class TaskUiState(
    val task: Task = Task(id = "new-task"),
    val taskType: TaskType = TaskType.RACING,
    val stats: TaskStats = TaskStats(),
    val validationErrors: List<TaskValidator.ValidationError> = emptyList(),
    val targets: List<TaskTargetSnapshot> = emptyList(),
    val racingValidationProfile: RacingTaskStructureRules.Profile = RacingTaskStructureRules.Profile.FAI_STRICT,
    val advanceSnapshot: TaskAdvanceUiSnapshot = TaskAdvanceUiSnapshot()
)
