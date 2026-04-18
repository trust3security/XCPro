package com.trust3.xcpro.map

import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType

data class TaskRenderSnapshot(
    val task: Task,
    val taskType: TaskType,
    val aatEditWaypointIndex: Int?
)
