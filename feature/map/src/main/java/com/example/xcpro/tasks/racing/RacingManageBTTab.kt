package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.common.waypoint.SearchWaypoint
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.example.xcpro.common.waypoint.WaypointData

// âœ… RACING-ONLY IMPORTS - Zero AAT contamination
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType

// Common task imports (separation compliant)
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.racing.ui.RacingTaskPointTypeSelector
import com.example.xcpro.tasks.SearchableWaypointField
import com.example.xcpro.tasks.QRCodeDialog
import com.example.xcpro.tasks.TaskStatsSection
import com.example.xcpro.tasks.PersistentWaypointSearchBar

/**
 * Racing-specific task management UI
 * âœ… SEPARATION COMPLIANT: Only Racing task imports and logic
 */
@Composable
fun RacingManageBTTab(
    task: Task,
    taskManager: TaskManagerCoordinator,
    mapLibreMap: MapLibreMap?,
    allWaypoints: List<WaypointData> = emptyList(),
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    currentQNH: String? = null
) {
    RacingFullyExpandedContent(
        task = task,
        onClearTask = onClearTask,
        onSaveTask = onSaveTask,
        onDismiss = onDismiss,
        taskManager = taskManager,
        mapLibreMap = mapLibreMap,
        allWaypoints = allWaypoints,
        currentQNH = currentQNH
    )
}

@Composable
private fun RacingFullyExpandedContent(
    task: Task,
    onClearTask: () -> Unit,
    onSaveTask: () -> Unit,
    onDismiss: () -> Unit,
    taskManager: TaskManagerCoordinator,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Persistent search bar right under header - always visible
        PersistentWaypointSearchBar(
            allWaypoints = allWaypoints,
            onWaypointSelected = { newWaypoint ->
                // Add new waypoint to the end of the task
                taskManager.addWaypoint(newWaypoint)
                taskManager.plotOnMap(mapLibreMap)
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
                onReorder = { fromIndex, toIndex ->
                    taskManager.reorderWaypoints(fromIndex, toIndex)
                    taskManager.plotOnMap(mapLibreMap)
                },
                onRemove = { index ->
                    taskManager.removeWaypoint(index)
                    taskManager.plotOnMap(mapLibreMap)
                },
                onTaskPointTypeUpdate = { index, startType, finishType, turnType, gateWidth, keyholeInnerRadius, keyholeAngle, faiQuadrantOuterRadius ->
                    taskManager.updateWaypointPointType(index, startType, finishType, turnType, gateWidth, keyholeInnerRadius, keyholeAngle, faiQuadrantOuterRadius)
                    taskManager.plotOnMap(mapLibreMap)
                },
                onWaypointReplace = { index, newWaypoint ->
                    taskManager.replaceWaypoint(index, newWaypoint)
                    taskManager.plotOnMap(mapLibreMap)
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
            onDismiss = { showQRDialog = false }
        )
    }
}

@Composable
private fun RacingReorderableWaypointList(
    waypoints: List<TaskWaypoint>,
    allWaypoints: List<WaypointData> = emptyList(),
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onTaskPointTypeUpdate: (Int, RacingStartPointType?, RacingFinishPointType?, RacingTurnPointType?, Double?, Double?, Double?, Double?) -> Unit,
    onWaypointReplace: (Int, SearchWaypoint) -> Unit,
    taskManager: TaskManagerCoordinator,
    currentQNH: String? = null,
    modifier: Modifier = Modifier
) {
    // Track which waypoint is expanded (only one at a time)
    var expandedWaypointIndex by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = navBarBottom + 48.dp // keep last item fully clear

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            top = 8.dp,
            start = 0.dp,
            end = 0.dp,
            bottom = bottomPad
        )
    ) {
        itemsIndexed(
            items = waypoints,
            key = { index, wp -> "racing_wp_${index}_${wp.id}" } // racing-specific key
        ) { index, taskWaypoint ->
            RacingReorderableWaypointItem(
                waypoint = SearchWaypoint(
                    id = taskWaypoint.id,
                    title = taskWaypoint.title,
                    subtitle = taskWaypoint.subtitle,
                    lat = taskWaypoint.lat,
                    lon = taskWaypoint.lon
                ),
                taskWaypoint = taskWaypoint,
                allWaypoints = allWaypoints,
                index = index,
                totalCount = waypoints.size,
                role = when {
                    index == 0 -> "Start"
                    index == waypoints.lastIndex && waypoints.size > 1 -> "Finish"
                    else -> "Turn $index"
                },
                nextWaypoint = if (index < waypoints.lastIndex) waypoints[index + 1] else null,
                taskManager = taskManager,
                isExpanded = expandedWaypointIndex == index,
                onExpandToggle = { shouldExpand ->
                    if (shouldExpand) {
                        expandedWaypointIndex = index
                        // Auto-scroll to show the expanded item
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(index)
                        }
                    } else {
                        expandedWaypointIndex = null
                    }
                },
                onMoveUp = if (index > 0) ({ onReorder(index, index - 1) }) else null,
                onMoveDown = if (index < waypoints.lastIndex) ({ onReorder(index, index + 1) }) else null,
                onRemove = { onRemove(index) },
                onTaskPointTypeUpdate = { startType, finishType, turnType, gateWidth, keyholeInnerRadius, keyholeAngle, faiQuadrantOuterRadius ->
                    onTaskPointTypeUpdate(index, startType, finishType, turnType, gateWidth, keyholeInnerRadius, keyholeAngle, faiQuadrantOuterRadius)
                },
                onWaypointReplace = { newWaypoint ->
                    onWaypointReplace(index, newWaypoint)
                }
            )
        }
    }
}

@Composable
fun RacingReorderableWaypointItem(
    waypoint: SearchWaypoint,
    taskWaypoint: TaskWaypoint,
    allWaypoints: List<WaypointData> = emptyList(),
    index: Int,
    totalCount: Int,
    role: String,
    nextWaypoint: TaskWaypoint? = null,
    taskManager: TaskManagerCoordinator,
    isExpanded: Boolean,
    onExpandToggle: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onTaskPointTypeUpdate: (RacingStartPointType?, RacingFinishPointType?, RacingTurnPointType?, Double?, Double?, Double?, Double?) -> Unit,
    onWaypointReplace: (SearchWaypoint) -> Unit,
    qnhValue: String? = null
) {
    val Blue = Color(0xFF007AFF)

    // âœ… RACING-ONLY task-specific waypoint handling
    val specificWaypoint = taskManager.getTaskSpecificWaypoint(index)
    val racingWaypoint = specificWaypoint as? RacingWaypoint

    // âœ… DIRECT MODEL READ: No cached UI state - always read from model to prevent override bug
    val selectedStartType = racingWaypoint?.startPointType ?: RacingStartPointType.START_CYLINDER
    val selectedFinishType = racingWaypoint?.finishPointType ?: RacingFinishPointType.FINISH_CYLINDER
    val selectedTurnType = racingWaypoint?.turnPointType ?: RacingTurnPointType.TURN_POINT_CYLINDER

    // Keep track of which waypoint role this is
    val waypointRole = when {
        index == 0 -> "Start"
        index == totalCount - 1 && totalCount > 1 -> "Finish"
        else -> "Turn Point"
    }

    // Get actual value from Racing waypoint - let the model handle defaults
    val actualValue = racingWaypoint?.gateWidth

    // Display the model's actual value - no UI fallbacks
    val displayValue = actualValue?.toString() ?: "0.0"

    var gateWidth by remember { mutableStateOf(displayValue) }

    // âœ… UI REFRESH: Update when underlying waypoint data changes
    LaunchedEffect(actualValue) {
        if (actualValue != null) {
            gateWidth = actualValue.toString()
        }
    }

    var keyholeInnerRadius by remember { mutableStateOf(racingWaypoint?.keyholeInnerRadius?.toString() ?: "0.5") }
    var keyholeAngle by remember { mutableStateOf(racingWaypoint?.keyholeAngle?.toString() ?: "45") }
    var faiQuadrantOuterRadius by remember { mutableStateOf(racingWaypoint?.faiQuadrantOuterRadius?.toString() ?: "20") }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onExpandToggle(!isExpanded) }
            .animateContentSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isExpanded) 2.dp else 1.dp,
            color = if (isExpanded)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Role bubble
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = when (role) {
                        "Start" -> Color(0xFF4CAF50)
                        "Finish" -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (isExpanded) {
                    SearchableWaypointField(
                        currentWaypoint = waypoint,
                        allWaypoints = allWaypoints,
                        onWaypointSelected = onWaypointReplace,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = waypoint.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Controls only when collapsed
                if (!isExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(36.dp),
                                tint = if (onMoveUp != null) Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(36.dp),
                                tint = if (onMoveDown != null) Blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove",
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // âœ… RACING TASK POINT TYPE SELECTOR - Only Racing types
                RacingTaskPointTypeSelector(
                    role = waypointRole,
                    waypoint = taskWaypoint,
                    selectedStartType = selectedStartType ?: RacingStartPointType.START_CYLINDER,
                    selectedFinishType = selectedFinishType ?: RacingFinishPointType.FINISH_CYLINDER,
                    selectedTurnType = selectedTurnType ?: RacingTurnPointType.TURN_POINT_CYLINDER,
                    gateWidth = gateWidth,
                    keyholeInnerRadius = keyholeInnerRadius,
                    keyholeAngle = keyholeAngle,
                    faiQuadrantOuterRadius = faiQuadrantOuterRadius,
                    nextWaypoint = nextWaypoint,
                    taskManager = taskManager,
                    onStartTypeChange = { newType ->
                        onTaskPointTypeUpdate(newType, selectedFinishType, selectedTurnType,
                            gateWidth.toDoubleOrNull(), keyholeInnerRadius.toDoubleOrNull(),
                            keyholeAngle.toDoubleOrNull(), faiQuadrantOuterRadius.toDoubleOrNull())
                    },
                    onFinishTypeChange = { newType ->
                        onTaskPointTypeUpdate(selectedStartType, newType, selectedTurnType,
                            gateWidth.toDoubleOrNull(), keyholeInnerRadius.toDoubleOrNull(),
                            keyholeAngle.toDoubleOrNull(), faiQuadrantOuterRadius.toDoubleOrNull())
                    },
                    onTurnTypeChange = { newType ->
                        // ðŸ”‘ BUG FIX: Pass correct default based on new type, not null
                        val typeSpecificDefault = when (newType) {
                            RacingTurnPointType.KEYHOLE -> 10.0  // 10km keyhole outer radius
                            RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 0.5
                        }
                        // ðŸ”‘ UI SYNC FIX: Update local UI state to match model
                        gateWidth = typeSpecificDefault.toString()
                        onTaskPointTypeUpdate(selectedStartType, selectedFinishType, newType,
                            typeSpecificDefault, null, null, null)
                    },
                    onGateWidthChange = { newWidth ->
                        gateWidth = newWidth
                        try {
                            val width = newWidth.toDouble()
                            onTaskPointTypeUpdate(null, null, null, width, null, null, null)
                        } catch (e: NumberFormatException) {
                            // Handle invalid format
                        }
                    },
                    onKeyholeInnerRadiusChange = { newRadius ->
                        keyholeInnerRadius = newRadius
                        try {
                            val radius = newRadius.toDouble()
                            onTaskPointTypeUpdate(null, null, null, null, radius, null, null)
                        } catch (e: NumberFormatException) {
                            // Handle invalid format
                        }
                    },
                    onKeyholeAngleChange = { newAngle ->
                        keyholeAngle = newAngle
                        try {
                            val angle = newAngle.toDouble()
                            onTaskPointTypeUpdate(null, null, null, null, null, angle, null)
                        } catch (e: NumberFormatException) {
                            // Handle invalid format
                        }
                    },
                    onFAIQuadrantOuterRadiusChange = { newRadius ->
                        faiQuadrantOuterRadius = newRadius
                        try {
                            val radius = newRadius.toDouble()
                            onTaskPointTypeUpdate(null, null, null, null, null, null, radius)
                        } catch (e: NumberFormatException) {
                            // Handle invalid format
                        }
                    }
                )
            }
        }
    }
}


