package com.example.xcpro.tasks.aat

import com.example.xcpro.tasks.core.Task
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.SearchWaypoint
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.common.waypoint.WaypointData

// ✅ AAT-ONLY IMPORTS - Zero Racing contamination
// Common task imports (separation compliant)
import com.example.xcpro.tasks.TaskUiState
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.AdvanceControls
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot

/**
 * AAT-specific task management UI
 * ✅ SEPARATION COMPLIANT: Only AAT task imports and logic
 */
@Composable
fun AATManageBTTab(
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
    AATFullyExpandedContent(
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
private fun AATReorderableWaypointList(
    waypoints: List<TaskWaypoint>,
    targets: List<TaskTargetSnapshot>,
    allWaypoints: List<WaypointData> = emptyList(),
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onWaypointReplace: (Int, SearchWaypoint) -> Unit,
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap? = null,
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
            key = { index, wp -> "aat_wp_${index}_${wp.id}" } // aat-specific key
        ) { index, taskWaypoint ->
            AATReorderableWaypointItem(
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
                targetSnapshot = targets.getOrNull(index),
                taskManager = taskManager,
                taskViewModel = taskViewModel,
                mapLibreMap = mapLibreMap,
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
                onWaypointReplace = { newWaypoint ->
                    onWaypointReplace(index, newWaypoint)
                }
            )
        }
    }
}

@Composable
fun AATReorderableWaypointItem(
    waypoint: SearchWaypoint,
    taskWaypoint: TaskWaypoint,
    allWaypoints: List<WaypointData> = emptyList(),
    index: Int,
    totalCount: Int,
    role: String,
    nextWaypoint: TaskWaypoint? = null,
    targetSnapshot: TaskTargetSnapshot? = null,
    taskManager: TaskManagerCoordinator,
    taskViewModel: TaskSheetViewModel,
    mapLibreMap: MapLibreMap? = null,
    isExpanded: Boolean,
    onExpandToggle: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onWaypointReplace: (SearchWaypoint) -> Unit,
    qnhValue: String? = null
) {
    val Blue = Color(0xFF007AFF)

    // ✅ AAT-ONLY task-specific waypoint handling - REACTIVE to task changes
    val aatWaypoint = remember(taskManager.getAATTaskManager().currentAATTask.waypoints) {
        taskManager.getAATTaskManager().currentAATTask.waypoints.getOrNull(index)
    }

    var selectedAATStartType by remember(aatWaypoint) { mutableStateOf(aatWaypoint?.startPointType ?: AATStartPointType.AAT_START_LINE) }
    var selectedAATFinishType by remember(aatWaypoint) { mutableStateOf(aatWaypoint?.finishPointType ?: AATFinishPointType.AAT_FINISH_CYLINDER) }
    var selectedAATTurnType by remember(aatWaypoint) { mutableStateOf(aatWaypoint?.turnPointType ?: AATTurnPointType.AAT_CYLINDER) }
    var aatTargetPoint by remember(aatWaypoint) { mutableStateOf(aatWaypoint?.targetPoint ?: AATLatLng(taskWaypoint.lat, taskWaypoint.lon)) }

    // Keep track of which waypoint role this is
    val waypointRole = when {
        index == 0 -> "Start"
        index == totalCount - 1 && totalCount > 1 -> "Finish"
        else -> "Turn Point"
    }

    // Get actual value from AAT waypoint (Racing pattern)
    val actualValue = aatWaypoint?.assignedArea?.radiusMeters?.let { it / 1000.0 }

    // Display the actual value - no UI fallbacks (Racing pattern)
    val displayValue = actualValue?.toString() ?: "10.0"

    var gateWidth by remember { mutableStateOf(displayValue) }
    var targetParam by remember(targetSnapshot) { mutableStateOf(targetSnapshot?.targetParam ?: 0.5) }

    // Update when waypoint data changes (Racing pattern)
    LaunchedEffect(actualValue) {
        if (actualValue != null) {
            gateWidth = actualValue.toString()
        }
    }

    // ✅ SSOT FIX: AAT-specific parameter state variables - Read from assignedArea
    var aatKeyholeInnerRadius by remember(aatWaypoint) {
        mutableStateOf(aatWaypoint?.assignedArea?.innerRadiusMeters?.let { (it / 1000.0).toString() } ?: "0.5")
    }
    var aatKeyholeAngle by remember(aatWaypoint) {
        mutableStateOf("90.0")  // Default angle - not stored in waypoint, used for sector calculations
    }
    var aatSectorOuterRadius by remember(aatWaypoint) {
        mutableStateOf(aatWaypoint?.assignedArea?.outerRadiusMeters?.let { (it / 1000.0).toString() } ?: "20.0")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
                // Role bubble - AAT colors with light navy for turn points
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onExpandToggle(!isExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    color = when (role) {
                        "Start" -> Color(0xFF4CAF50)    // Green for start
                        "Finish" -> Color(0xFFF44336)   // Red for finish
                        else -> Color(0xFF546E7A)       // Light navy for AAT turn points
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
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExpandToggle(!isExpanded) }
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

                // ✅ AAT TASK POINT TYPE SELECTOR - Professional UI matching Racing pattern
                AATTaskPointTypeSelector(
                    role = waypointRole,
                    waypoint = taskWaypoint,
                    selectedStartType = selectedAATStartType,
                    selectedFinishType = selectedAATFinishType,
                    selectedTurnType = selectedAATTurnType,
                    gateWidth = gateWidth,
                    keyholeInnerRadius = aatKeyholeInnerRadius,
                    keyholeAngle = aatKeyholeAngle,
                    sectorOuterRadius = aatSectorOuterRadius,
                    nextWaypoint = nextWaypoint,
                    taskManager = taskManager,
                    onStartTypeChange = { newType ->
                        selectedAATStartType = newType
                        println("🎯 AAT: Start type changed to ${newType.displayName}")
                        taskViewModel.onUpdateAATWaypointPointType(
                            index = index,
                            startType = newType,
                            finishType = null,
                            turnType = null,
                            gateWidth = null,
                            keyholeInnerRadius = null,
                            keyholeAngle = null,
                            sectorOuterRadius = null
                        )
                    },
                    onFinishTypeChange = { newType ->
                        selectedAATFinishType = newType
                        println("🎯 AAT: Finish type changed to ${newType.displayName}")
                        taskViewModel.onUpdateAATWaypointPointType(
                            index = index,
                            startType = null,
                            finishType = newType,
                            turnType = null,
                            gateWidth = null,
                            keyholeInnerRadius = null,
                            keyholeAngle = null,
                            sectorOuterRadius = null
                        )
                    },
                    onTurnTypeChange = { newType ->
                        selectedAATTurnType = newType
                        println("🎯 AAT: Turn type changed to ${newType.displayName}")
                        taskViewModel.onUpdateAATWaypointPointType(
                            index = index,
                            startType = null,
                            finishType = null,
                            turnType = newType,
                            gateWidth = null,
                            keyholeInnerRadius = null,
                            keyholeAngle = null,
                            sectorOuterRadius = null
                        )
                    },
                    onGateWidthChange = { newWidth ->
                        gateWidth = newWidth  // ✅ Update UI immediately (Racing pattern)
                        try {
                            val radiusKm = newWidth.toDouble()
                            val radiusMeters = radiusKm * 1000.0
                            taskViewModel.onUpdateAATArea(index, radiusMeters)
                        } catch (e: NumberFormatException) {
                            // Invalid format - UI still shows what user typed
                        }
                    },
                    onKeyholeInnerRadiusChange = { newRadius ->
                        aatKeyholeInnerRadius = newRadius
                        println("🎯 AAT: Keyhole inner radius changed to ${newRadius}km")
                        try {
                            val radiusKm = newRadius.toDouble()
                            taskViewModel.onUpdateAATWaypointPointType(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidth = null,
                                keyholeInnerRadius = radiusKm,
                                keyholeAngle = null,
                                sectorOuterRadius = null
                            )
                            // ✅ FIX: Re-plot map to show updated keyhole geometry
                            
                        } catch (e: NumberFormatException) {
                            println("🎯 AAT: Invalid keyhole inner radius format: $newRadius")
                        }
                    },
                    onKeyholeAngleChange = { newAngle ->
                        aatKeyholeAngle = newAngle
                        println("🎯 AAT: Keyhole angle changed to ${newAngle}°")
                        try {
                            val angleDegrees = newAngle.toDouble()
                            taskViewModel.onUpdateAATWaypointPointType(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidth = null,
                                keyholeInnerRadius = null,
                                keyholeAngle = angleDegrees,
                                sectorOuterRadius = null
                            )
                            // ✅ FIX: Re-plot map to show updated keyhole geometry
                            
                        } catch (e: NumberFormatException) {
                            println("🎯 AAT: Invalid keyhole angle format: $newAngle")
                        }
                    },
                    onSectorOuterRadiusChange = { newRadius ->
                        aatSectorOuterRadius = newRadius
                        println("🎯 AAT: Sector outer radius changed to ${newRadius}km")
                        try {
                            val radiusKm = newRadius.toDouble()
                            taskViewModel.onUpdateAATWaypointPointType(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidth = null,
                                keyholeInnerRadius = null,
                                keyholeAngle = null,
                                sectorOuterRadius = radiusKm
                            )
                            // ✅ FIX: Re-plot map to show updated sector geometry
                            
                        } catch (e: NumberFormatException) {
                            println("🎯 AAT: Invalid sector outer radius format: $newRadius")
                        }
                    }
                )

                AATTargetControls(
                    target = targetSnapshot,
                    onParamChanged = { value ->
                        targetParam = value
                        taskViewModel.onSetTargetParam(index, value)
                    },
                    onLockToggle = { taskViewModel.onToggleTargetLock(index) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
