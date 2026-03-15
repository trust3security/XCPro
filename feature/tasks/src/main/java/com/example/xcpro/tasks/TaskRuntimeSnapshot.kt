package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType

data class TaskRuntimeSnapshot(
    val taskType: TaskType,
    val task: Task,
    val activeLeg: Int
)
