package com.example.xcpro.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.map.MapTaskScreenManager
import kotlin.math.abs
import org.maplibre.android.maps.MapLibreMap

@Composable
fun TaskTopDropdownPanel(
    panelState: MapTaskScreenManager.TaskPanelState,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    onStateChange: (MapTaskScreenManager.TaskPanelState) -> Unit,
    mapLibreMap: MapLibreMap?,
    taskViewModel: TaskSheetViewModel? = null,
    allWaypoints: List<WaypointData> = emptyList(),
    unitsPreferences: UnitsPreferences = UnitsPreferences(),
    currentQNH: String? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val focusManager = LocalFocusManager.current

    // Locked visual baseline copied from SwipeableTaskBottomSheet.
    val minimizedHeight = 120.dp
    val halfExpandedHeight = 400.dp
    val fullyExpandedHeight = screenHeight * 0.95f

    val minimizedPx = with(density) { minimizedHeight.toPx() }
    val halfExpandedPx = with(density) { halfExpandedHeight.toPx() }
    val fullyExpandedPx = with(density) { fullyExpandedHeight.toPx() }

    val resolvedTaskViewModel = taskViewModel ?: hiltViewModel()
    val uiState by resolvedTaskViewModel.uiState.collectAsStateWithLifecycle()
    val task by remember { androidx.compose.runtime.derivedStateOf { uiState.task } }

    var currentHeightPx by remember { mutableStateOf(halfExpandedPx) }
    var swipeDownDistance by remember { mutableStateOf(0f) }
    var swipeUpDistance by remember { mutableStateOf(0f) }

    LaunchedEffect(panelState, minimizedPx, halfExpandedPx, fullyExpandedPx) {
        currentHeightPx = when (panelState) {
            MapTaskScreenManager.TaskPanelState.HIDDEN -> minimizedPx
            MapTaskScreenManager.TaskPanelState.COLLAPSED -> minimizedPx
            MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL -> halfExpandedPx
            MapTaskScreenManager.TaskPanelState.EXPANDED_FULL -> fullyExpandedPx
        }
    }

    val draggableState = rememberDraggableState { delta ->
        // Top anchored: drag down expands, drag up collapses.
        val newHeight = currentHeightPx + delta
        if (delta > 0f) {
            swipeDownDistance += delta
            swipeUpDistance = 0f
        } else {
            swipeUpDistance += abs(delta)
            swipeDownDistance = 0f
        }
        currentHeightPx = newHeight.coerceIn(minimizedPx - 200f, fullyExpandedPx)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { currentHeightPx.toDp() })
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    val swipeThreshold = 50f
                    val dismissThreshold = 150f

                    val targetState = when (panelState) {
                        MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL -> {
                            when {
                                swipeUpDistance > dismissThreshold || currentHeightPx < minimizedPx ->
                                    MapTaskScreenManager.TaskPanelState.HIDDEN

                                swipeDownDistance > swipeThreshold ->
                                    MapTaskScreenManager.TaskPanelState.EXPANDED_FULL

                                else -> MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL
                            }
                        }

                        MapTaskScreenManager.TaskPanelState.EXPANDED_FULL -> {
                            when {
                                swipeUpDistance > swipeThreshold -> {
                                    if (task.waypoints.isNotEmpty()) {
                                        MapTaskScreenManager.TaskPanelState.COLLAPSED
                                    } else {
                                        MapTaskScreenManager.TaskPanelState.HIDDEN
                                    }
                                }

                                else -> MapTaskScreenManager.TaskPanelState.EXPANDED_FULL
                            }
                        }

                        else -> panelState
                    }

                    when (targetState) {
                        MapTaskScreenManager.TaskPanelState.HIDDEN -> onDismiss()
                        else -> onStateChange(targetState)
                    }

                    currentHeightPx = when (targetState) {
                        MapTaskScreenManager.TaskPanelState.HIDDEN -> minimizedPx
                        MapTaskScreenManager.TaskPanelState.COLLAPSED -> minimizedPx
                        MapTaskScreenManager.TaskPanelState.EXPANDED_PARTIAL -> halfExpandedPx
                        MapTaskScreenManager.TaskPanelState.EXPANDED_FULL -> fullyExpandedPx
                    }
                    swipeDownDistance = 0f
                    swipeUpDistance = 0f
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            when {
                currentHeightPx <= minimizedPx + 50f -> {
                    MinimizedContent(task = task, taskType = uiState.taskType)
                }

                else -> {
                    TaskTopExpandedContent(
                        uiState = uiState,
                        task = task,
                        taskViewModel = resolvedTaskViewModel,
                        currentQNH = currentQNH,
                        allWaypoints = allWaypoints,
                        unitsPreferences = unitsPreferences,
                        onClearTask = onClearTask,
                        onSaveTask = onSaveTask,
                        onDismiss = onDismiss,
                        mapLibreMap = mapLibreMap
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskTopExpandedContent(
    uiState: TaskUiState,
    task: Task,
    taskViewModel: TaskSheetViewModel,
    currentQNH: String?,
    allWaypoints: List<WaypointData>,
    unitsPreferences: UnitsPreferences,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    mapLibreMap: MapLibreMap?
) {
    TaskPanelCategoryHost(
        uiState = uiState,
        taskViewModel = taskViewModel,
        manageContent = {
                ManageBTTabRouter(
                    uiState = uiState,
                    task = task,
                    taskViewModel = taskViewModel,
                    mapLibreMap = mapLibreMap,
                    allWaypoints = allWaypoints,
                    unitsPreferences = unitsPreferences,
                    onClearTask = onClearTask,
                    onSaveTask = onSaveTask,
                    onDismiss = onDismiss,
                    currentQNH = currentQNH,
                    taskType = uiState.taskType
                )
        }
    )
}
