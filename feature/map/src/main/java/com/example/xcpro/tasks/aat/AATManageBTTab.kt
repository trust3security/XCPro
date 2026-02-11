package com.example.xcpro.tasks.aat

import androidx.compose.runtime.Composable
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.TaskUiState
import com.example.xcpro.tasks.core.Task
import org.maplibre.android.maps.MapLibreMap

/**
 * AAT-specific task management UI entry point for the bottom tab.
 * Delegates to the shared expanded content implementation.
 */
@Composable
fun AATManageBTTab(
    uiState: TaskUiState,
    task: Task,
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
        taskViewModel = taskViewModel,
        mapLibreMap = mapLibreMap,
        allWaypoints = allWaypoints,
        currentQNH = currentQNH
    )
}
