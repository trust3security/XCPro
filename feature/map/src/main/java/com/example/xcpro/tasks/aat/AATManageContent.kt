package com.example.xcpro.tasks.aat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.TaskUiState
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.QRCodeDialog
import com.example.xcpro.tasks.TaskStatsSection
import com.example.xcpro.tasks.PersistentWaypointSearchBar
import com.example.xcpro.tasks.AdvanceControls
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import org.maplibre.android.maps.MapLibreMap

@Composable
internal fun AATFullyExpandedContent(
    uiState: TaskUiState,
    task: Task,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData>,
    currentQNH: String?
) {
    var showQRDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        TaskStatsSection(
            task = task,
            taskType = TaskType.AAT,
            distanceKm = uiState.stats.distanceNominal / 1000.0,
            onQRCodeClick = { showQRDialog = true }
        )

        AdvanceControls(
            snapshot = uiState.advanceSnapshot,
            onModeChange = { mode -> taskViewModel.onAdvanceMode(mode) },
            onToggleArm = { taskViewModel.onAdvanceArmToggle() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        PersistentWaypointSearchBar(
            allWaypoints = allWaypoints,
            onWaypointSelected = { newWaypoint -> taskViewModel.onAddWaypoint(newWaypoint) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (task.waypoints.isNotEmpty()) {
            AATReorderableWaypointList(
                waypoints = task.waypoints,
                targets = uiState.targets,
                allWaypoints = allWaypoints,
                currentQNH = currentQNH,
                taskViewModel = taskViewModel,
                onReorder = { fromIndex, toIndex -> taskViewModel.onReorderWaypoint(fromIndex, toIndex) },
                onRemove = { index -> taskViewModel.onRemoveWaypoint(index) },
                onWaypointReplace = { index, newWaypoint -> taskViewModel.onReplaceWaypoint(index, newWaypoint) },
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search above to add waypoints to your AAT task",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showQRDialog) {
        QRCodeDialog(
            uiState = uiState,
            onDismiss = { showQRDialog = false },
            onImportJson = { json -> taskViewModel.importPersistedTask(json) }
        )
    }
}
