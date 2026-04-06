package com.example.xcpro.map

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType

data class TaskRenderSnapshot(
    val task: Task,
    val taskType: TaskType,
    val aatEditWaypointIndex: Int?
)
