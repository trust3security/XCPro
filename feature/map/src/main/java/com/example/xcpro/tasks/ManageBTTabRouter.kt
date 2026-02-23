package com.example.xcpro.tasks


import androidx.compose.runtime.Composable
import com.example.xcpro.common.units.UnitsPreferences
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.TaskUiState

// Task type routing imports (separation compliant)
import com.example.xcpro.tasks.racing.RacingManageBTTab
import com.example.xcpro.tasks.aat.AATManageBTTab

/**
 * Router component for task-type-specific ManageBTTab implementations
 *  SEPARATION COMPLIANT: Routes to task-specific components without shared logic
 */
@Composable
fun ManageBTTabRouter(
    uiState: TaskUiState,
    task: Task,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
    unitsPreferences: UnitsPreferences = UnitsPreferences(),
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    currentQNH: String? = null,
    taskType: TaskType
) {
    //  PURE ROUTING: No shared calculation logic, no task-specific imports mixed
    when (taskType) {
        TaskType.RACING -> {
            RacingManageBTTab(
                task = task,
                taskViewModel = taskViewModel,
                uiState = uiState,
                mapLibreMap = mapLibreMap,
                allWaypoints = allWaypoints,
                unitsPreferences = unitsPreferences,
                onClearTask = onClearTask,
                onSaveTask = onSaveTask,
                onDismiss = onDismiss,
                currentQNH = currentQNH
            )
        }
        TaskType.AAT -> {
            AATManageBTTab(
                task = task,
                taskViewModel = taskViewModel,
                uiState = uiState,
                mapLibreMap = mapLibreMap,
                allWaypoints = allWaypoints,
                unitsPreferences = unitsPreferences,
                onClearTask = onClearTask,
                onSaveTask = onSaveTask,
                onDismiss = onDismiss,
                currentQNH = currentQNH
            )
        }
    }
}
