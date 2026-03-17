package com.example.xcpro.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.map.TaskRenderSyncCoordinator

/**
 * Generic Task Map Overlay - No task-specific imports to prevent contamination
 * Delegates to specific task overlays based on task type
 */
@Composable
fun TaskMapOverlay(
    onTaskStateChanged: (TaskRenderSyncCoordinator.TaskStateSignature) -> Unit,
    taskViewModel: TaskSheetViewModel? = null,
    modifier: Modifier = Modifier
) {
    val resolvedViewModel = taskViewModel ?: hiltViewModel()
    val uiState by resolvedViewModel.uiState.collectAsStateWithLifecycle()
    val currentTask = uiState.task
    val currentTaskType = uiState.taskType
    val taskStateSignature = remember(currentTask, currentTaskType) {
        TaskRenderSyncCoordinator.TaskStateSignature(
            taskId = currentTask.id,
            taskHash = currentTask.hashCode(),
            taskType = currentTaskType
        )
    }

    // Emit task state changes to the single runtime sync owner.
    LaunchedEffect(taskStateSignature) {
        onTaskStateChanged(taskStateSignature)
    }

    Box(modifier = modifier) {
        // Generic task visualization UI could go here
        // (distance display, task info, etc.)
        // Specific task validation should be handled by dedicated overlays
    }

    // NOTE: MapLibre 10+ location listeners differ; wiring remains in the host runtime layer.
}
