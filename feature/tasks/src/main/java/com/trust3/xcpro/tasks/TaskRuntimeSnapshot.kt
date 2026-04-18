package com.trust3.xcpro.tasks

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType

data class TaskRuntimeSnapshot(
    val taskType: TaskType,
    val task: Task,
    val activeLeg: Int
)
