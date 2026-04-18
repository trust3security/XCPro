package com.trust3.xcpro.tasks


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.android.maps.MapLibreMap
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.tasks.core.TaskType

// Calculate waypoint item height (approximate)
private val WAYPOINT_ITEM_HEIGHT = 72.dp // Estimated height of ReorderableWaypointItem
private val CLEARANCE_MULTIPLIER = 1.5f

// Function to calculate optimal bottom sheet height
private fun calculateOptimalHeight(
    density: Density,
    waypointCount: Int,
    screenHeight: Dp
): Float {
    return with(density) {
        val clearance = WAYPOINT_ITEM_HEIGHT * CLEARANCE_MULTIPLIER
        val maxAllowedHeight = screenHeight - clearance
        maxAllowedHeight.toPx()
    }
}

// ---------- MAIN BOTTOM SHEET ----------
@Composable
fun SwipeableTaskBottomSheet(
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    mapLibreMap: MapLibreMap?,
    taskViewModel: TaskSheetViewModel? = null,
    allWaypoints: List<WaypointData> = emptyList(),
    isSearchActive: Boolean = false,
    currentQNH: String? = null,
    initialHeight: BottomSheetState = BottomSheetState.HALF_EXPANDED,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val focusManager = LocalFocusManager.current

    val minimizedHeight = 120.dp
    val halfExpandedHeight = 400.dp
    val fullyExpandedHeight = screenHeight * 0.95f

    val minimizedPx = with(density) { minimizedHeight.toPx() }
    val halfExpandedPx = with(density) { halfExpandedHeight.toPx() }
    val fullyExpandedPx = with(density) { fullyExpandedHeight.toPx() }

    // Search offset to shift sheet down when search is active
    val searchOffset = if (isSearchActive) WAYPOINT_ITEM_HEIGHT else 0.dp

    // ViewModel wiring (placed near data consumers to avoid scope confusion)
    val resolvedTaskViewModel = taskViewModel ?: hiltViewModel()
    val uiState by resolvedTaskViewModel.uiState.collectAsStateWithLifecycle()

    //  SSOT FIX: Make task reactive so distance/UI updates when radius changes
    // CRITICAL: Without derivedStateOf, task captures once and never updates!
    val task by remember { derivedStateOf { uiState.task } }

    var currentHeightPx by remember(initialHeight) {
        val height = when (initialHeight) {
            BottomSheetState.MINIMIZED -> minimizedPx
            BottomSheetState.HALF_EXPANDED -> halfExpandedPx
            BottomSheetState.FULLY_EXPANDED -> fullyExpandedPx
        }
        mutableStateOf(height)
    }
    var isDragging by remember { mutableStateOf(false) }

    var swipeDownDistance by remember { mutableStateOf(0f) }
    var swipeUpDistance by remember { mutableStateOf(0f) }
    var initialHeightPx by remember { mutableStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        isDragging = true
        val newHeight = currentHeightPx - delta

        // Track swipe direction and distance
        if (delta > 0) { // Swiping down
            swipeDownDistance += delta
            swipeUpDistance = 0f
        } else { // Swiping up
            swipeUpDistance += kotlin.math.abs(delta)
            swipeDownDistance = 0f
        }

        currentHeightPx = newHeight.coerceIn(minimizedPx - 200f, fullyExpandedPx)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = searchOffset)
            .height(with(density) { currentHeightPx.toDp() })
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { focusManager.clearFocus() }
                )
            }
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    isDragging = true
                    initialHeightPx = currentHeightPx
                },
                onDragStopped = {
                    isDragging = false

                    val swipeThreshold = 50f

                    val newHeight = when {
                        swipeDownDistance > 150f || currentHeightPx < minimizedPx -> {
                            if (isSearchActive) {
                                minimizedPx
                            } else {
                                onDismiss()
                                currentHeightPx
                            }
                        }
                        swipeUpDistance > swipeThreshold && initialHeightPx < halfExpandedPx -> {
                            halfExpandedPx
                        }
                        swipeUpDistance > swipeThreshold && initialHeightPx >= halfExpandedPx -> {
                            fullyExpandedPx
                        }
                        swipeDownDistance > swipeThreshold && initialHeightPx > halfExpandedPx -> {
                            halfExpandedPx
                        }
                        swipeDownDistance > swipeThreshold && initialHeightPx <= halfExpandedPx -> {
                            minimizedPx
                        }
                        else -> {
                            // Snap to nearest state based on current position
                            when {
                                currentHeightPx < (minimizedPx + halfExpandedPx) / 2 -> minimizedPx
                                currentHeightPx < (halfExpandedPx + fullyExpandedPx) / 2 -> halfExpandedPx
                                else -> fullyExpandedPx
                            }
                        }
                    }

                    currentHeightPx = newHeight
                    swipeDownDistance = 0f
                    swipeUpDistance = 0f
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle
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
                    // Minimized content
                    MinimizedContent(task = task, taskType = uiState.taskType)
                }
                currentHeightPx >= fullyExpandedPx - 50f -> {
                    // Fully expanded content with categories
                    ExpandedContent(
                        uiState = uiState,
                        task = task,
                        taskViewModel = resolvedTaskViewModel,
                        currentQNH = currentQNH,
                        allWaypoints = allWaypoints,
                        onClearTask = onClearTask,
                        onSaveTask = onSaveTask,
                        onDismiss = onDismiss,
                        mapLibreMap = mapLibreMap
                    )
                }
                else -> {
                    // Half expanded - show same content as fully expanded
                    ExpandedContent(
                        uiState = uiState,
                        task = task,
                        taskViewModel = resolvedTaskViewModel,
                        currentQNH = currentQNH,
                        allWaypoints = allWaypoints,
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
private fun ExpandedContent(
    uiState: TaskUiState,
    task: Task,
    taskViewModel: TaskSheetViewModel,
    currentQNH: String?,
    allWaypoints: List<WaypointData>,
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
                    onClearTask = onClearTask,
                    onSaveTask = onSaveTask,
                    onDismiss = onDismiss,
                    currentQNH = currentQNH,
                    taskType = uiState.taskType
                )
        }
    )
}


