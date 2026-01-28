package com.example.xcpro.tasks


import androidx.compose.runtime.Composable
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
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
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
                taskManager = taskManager,
                taskViewModel = taskViewModel,
                uiState = uiState,
                mapLibreMap = mapLibreMap,
                allWaypoints = allWaypoints,
                onClearTask = onClearTask,
                onSaveTask = onSaveTask,
                onDismiss = onDismiss,
                currentQNH = currentQNH
            )
        }
        TaskType.AAT -> {
            AATManageBTTab(
                task = task,
                taskManager = taskManager,
                taskViewModel = taskViewModel,
                uiState = uiState,
                mapLibreMap = mapLibreMap,
                allWaypoints = allWaypoints,
                onClearTask = onClearTask,
                onSaveTask = onSaveTask,
                onDismiss = onDismiss,
                currentQNH = currentQNH
            )
        }
    }
}
