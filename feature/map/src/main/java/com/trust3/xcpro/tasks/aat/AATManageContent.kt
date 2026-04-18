package com.trust3.xcpro.tasks.aat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.tasks.TaskUiState
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.QRCodeDialog
import com.trust3.xcpro.tasks.TaskStatsSection
import com.trust3.xcpro.tasks.PersistentWaypointSearchBar
import com.trust3.xcpro.tasks.AdvanceControls
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.tasks.core.Task
import com.trust3.xcpro.tasks.core.TaskType
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
    unitsPreferences: UnitsPreferences,
    currentQNH: String?
) {
    var showQRDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        TaskStatsSection(
            task = task,
            taskType = TaskType.AAT,
            distanceMeters = uiState.stats.distanceNominal,
            unitsPreferences = unitsPreferences,
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
