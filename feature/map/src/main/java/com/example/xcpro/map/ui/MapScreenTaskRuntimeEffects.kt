package com.example.xcpro.map.ui

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.map.MapTaskIntegration
import com.example.xcpro.tasks.core.TaskType

internal data class TaskDrawerRuntimePolicy(
    val shouldBlockDrawerGestures: Boolean,
    val shouldCloseOpenDrawer: Boolean
)

internal fun shouldExitAatEditMode(
    taskType: TaskType,
    isAATEditMode: Boolean
): Boolean = taskType != TaskType.AAT && isAATEditMode

internal fun resolveTaskDrawerRuntimePolicy(
    taskType: TaskType,
    isAATEditMode: Boolean,
    isDrawerOpen: Boolean
): TaskDrawerRuntimePolicy {
    val shouldBlock = MapTaskIntegration.shouldBlockDrawerGestures(
        taskType = taskType,
        isAATEditMode = isAATEditMode
    )
    return TaskDrawerRuntimePolicy(
        shouldBlockDrawerGestures = shouldBlock,
        shouldCloseOpenDrawer = shouldBlock && isDrawerOpen
    )
}

@Composable
internal fun MapScreenTaskRuntimeEffects(
    taskType: TaskType,
    drawerState: DrawerState,
    isAATEditMode: Boolean,
    onExitAATEditMode: () -> Unit
) {
    LaunchedEffect(taskType, isAATEditMode) {
        if (shouldExitAatEditMode(taskType, isAATEditMode)) {
            AppLogger.d(MapScreenRuntimeEffectsTag, "Task type changed to $taskType - resetting AAT edit mode")
            onExitAATEditMode()
        }
    }

    LaunchedEffect(isAATEditMode, taskType) {
        val drawerPolicy = resolveTaskDrawerRuntimePolicy(
            taskType = taskType,
            isAATEditMode = isAATEditMode,
            isDrawerOpen = drawerState.isOpen
        )
        if (drawerPolicy.shouldCloseOpenDrawer) {
            drawerState.close()
        }
        if (drawerPolicy.shouldBlockDrawerGestures) {
            AppLogger.d(MapScreenRuntimeEffectsTag, "Task-specific drawer blocking active ($taskType)")
        } else {
            AppLogger.d(MapScreenRuntimeEffectsTag, "GAA Drawer gestures enabled")
        }
    }
}
