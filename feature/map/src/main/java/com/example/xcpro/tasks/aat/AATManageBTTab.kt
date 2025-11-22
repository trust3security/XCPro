package com.example.xcpro.tasks.aat

import androidx.compose.runtime.Composable
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.TaskUiState
import com.example.xcpro.tasks.core.Task
import org.maplibre.android.maps.MapLibreMap

/**
 * Thin entry point that wires the AAT bottom tab into the shared content that now lives
 * in `AATManageContent.kt`. Keeping this file minimal avoids duplication and keeps the
 * public API stable for callers.
 */
@Composable
fun AATManageBTTab(
    uiState: TaskUiState,
    task: Task,
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    currentQNH: String? = null
) {
    AATFullyExpandedContent(
        uiState = uiState,
        task = task,
        onClearTask = onClearTask,
        onSaveTask = onSaveTask,
        onDismiss = onDismiss,
        taskManager = taskManager,
        taskViewModel = taskViewModel,
        mapLibreMap = mapLibreMap,
        allWaypoints = allWaypoints,
        currentQNH = currentQNH
    )
}
