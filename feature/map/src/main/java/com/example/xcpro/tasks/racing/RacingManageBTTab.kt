package com.example.xcpro.tasks.racing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.AdvanceControls
import com.example.xcpro.tasks.PersistentWaypointSearchBar
import com.example.xcpro.tasks.QRCodeDialog
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.TaskStatsSection
import com.example.xcpro.tasks.TaskUiState
import com.example.xcpro.tasks.core.Task
import org.maplibre.android.maps.MapLibreMap

/**
 * Racing-specific task management UI
 * ✅ SEPARATION COMPLIANT: Only Racing task imports and logic
 */
@Composable
fun RacingManageBTTab(
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
    RacingFullyExpandedContent(
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

@Composable
private fun RacingFullyExpandedContent(
    uiState: TaskUiState,
    task: Task,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
    currentQNH: String? = null
) {
    var showQRDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Task header with QR sharing - always show
        Spacer(modifier = Modifier.height(8.dp))

        // Task statistics with 3 icons (Distance, QR, Task Type)
        TaskStatsSection(
            task = task,
            taskType = com.example.xcpro.tasks.core.TaskType.RACING,
            taskManager = taskManager,
            onQRCodeClick = { showQRDialog = true }
        )

        AdvanceControls(
            snapshot = uiState.advanceSnapshot,
            onModeChange = { mode -> taskViewModel.onAdvanceMode(mode) },
            onToggleArm = { taskViewModel.onAdvanceArmToggle() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Persistent search bar right under header - always visible
        PersistentWaypointSearchBar(
            allWaypoints = allWaypoints,
            onWaypointSelected = { newWaypoint ->
                taskViewModel.onAddWaypoint(newWaypoint)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp) // 3mm (~9dp) margin on each side
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Waypoint list that grows downward below search bar
        if (task.waypoints.isNotEmpty()) {
            RacingReorderableWaypointList(
                waypoints = task.waypoints,
                allWaypoints = allWaypoints,
                currentQNH = currentQNH,
                taskManager = taskManager,
                taskViewModel = taskViewModel,
                onReorder = { fromIndex, toIndex ->
                    taskViewModel.onReorderWaypoint(fromIndex, toIndex)
                },
                onRemove = { index ->
                    taskViewModel.onRemoveWaypoint(index)
                },
                onTaskPointTypeUpdate = { index, startType, finishType, turnType, gateWidth, keyholeInnerRadius, keyholeAngle, faiQuadrantOuterRadius ->
                    taskViewModel.onUpdateWaypointPointType(
                        index,
                        startType,
                        finishType,
                        turnType,
                        gateWidth,
                        keyholeInnerRadius,
                        keyholeAngle,
                        faiQuadrantOuterRadius
                    )
                },
                onWaypointReplace = { index, newWaypoint ->
                    taskViewModel.onReplaceWaypoint(index, newWaypoint)
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            // When no waypoints, show helpful text below search bar
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search above to add waypoints to your Racing task",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // QR Code Dialog
    if (showQRDialog) {
        QRCodeDialog(
            taskManager = taskManager,
            uiState = uiState,
            onDismiss = { showQRDialog = false },
            onImportJson = { json -> taskViewModel.importPersistedTask(json) }
        )
    }
}
