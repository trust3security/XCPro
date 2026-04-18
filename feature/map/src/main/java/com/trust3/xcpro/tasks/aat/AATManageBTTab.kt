package com.trust3.xcpro.tasks.aat

import androidx.compose.runtime.Composable
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.TaskUiState
import com.trust3.xcpro.tasks.core.Task
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
    unitsPreferences: UnitsPreferences = UnitsPreferences(),
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
        unitsPreferences = unitsPreferences,
        currentQNH = currentQNH
    )
}
