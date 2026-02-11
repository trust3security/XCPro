package com.example.xcpro.tasks.aat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.launch
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.SearchableWaypointField
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.aat.models.*
import com.example.xcpro.tasks.aat.ui.AATTaskPointTypeSelector
import com.example.xcpro.tasks.domain.model.TaskTargetSnapshot
import com.example.xcpro.tasks.core.TaskWaypoint

@Composable
internal fun AATReorderableWaypointList(
    waypoints: List<TaskWaypoint>,
    targets: List<TaskTargetSnapshot>,
    allWaypoints: List<WaypointData> = emptyList(),
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onWaypointReplace: (Int, SearchWaypoint) -> Unit,
    taskViewModel: TaskSheetViewModel,
    currentQNH: String? = null,
    modifier: Modifier = Modifier
) {
    var expandedWaypointIndex by remember { mutableStateOf<Int?>(null) }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad = navBarBottom + 48.dp

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, start = 0.dp, end = 0.dp, bottom = bottomPad)
    ) {
        itemsIndexed(
            items = waypoints,
            key = { index, wp -> "aat_wp_${index}_${wp.id}" }
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
                taskViewModel = taskViewModel,
                isExpanded = expandedWaypointIndex == index,
                onExpandToggle = { shouldExpand ->
                    if (shouldExpand) {
                        expandedWaypointIndex = index
                        coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                    } else expandedWaypointIndex = null
                },
                onMoveUp = if (index > 0) ({ onReorder(index, index - 1) }) else null,
                onMoveDown = if (index < waypoints.lastIndex) ({ onReorder(index, index + 1) }) else null,
                onRemove = { onRemove(index) },
                onWaypointReplace = { newWp -> onWaypointReplace(index, newWp) },
                currentQNH = currentQNH
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AATReorderableWaypointItem(
    waypoint: SearchWaypoint,
    taskWaypoint: TaskWaypoint,
    allWaypoints: List<WaypointData>,
    index: Int,
    totalCount: Int,
    role: String,
    nextWaypoint: TaskWaypoint?,
    targetSnapshot: TaskTargetSnapshot?,
    taskViewModel: TaskSheetViewModel,
    isExpanded: Boolean,
    onExpandToggle: (Boolean) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onWaypointReplace: (SearchWaypoint) -> Unit,
    currentQNH: String?
) {
    val Blue = Color(0xFF2196F3)
    val targetParam = remember(targetSnapshot?.targetParam) { mutableStateOf(targetSnapshot?.targetParam ?: 0.5) }
    val innerRadiusMeters = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["innerRadiusMeters"].asDoubleOrNull() ?: 0.0
    }
    val outerRadiusMeters = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["outerRadiusMeters"].asDoubleOrNull()
    }
    val startAngleDegrees = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["startAngleDegrees"].asDoubleOrNull() ?: 0.0
    }
    val endAngleDegrees = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["endAngleDegrees"].asDoubleOrNull() ?: 90.0
    }
    val radiusMeters = remember(taskWaypoint.customRadius) {
        (taskWaypoint.customRadius ?: 10.0) * 1000.0
    }

    var selectedAATStartType by remember(taskWaypoint) {
        mutableStateOf(inferAATStartType(taskWaypoint))
    }
    var selectedAATFinishType by remember(taskWaypoint) {
        mutableStateOf(inferAATFinishType(taskWaypoint))
    }
    var selectedAATTurnType by remember(taskWaypoint) {
        mutableStateOf(inferAATTurnType(taskWaypoint, innerRadiusMeters))
    }

    val defaultRadiusKm = radiusMeters / 1000.0
    var gateWidth by remember(taskWaypoint.id, radiusMeters) { mutableStateOf(String.format("%.1f", defaultRadiusKm)) }
    var aatKeyholeInnerRadius by remember(taskWaypoint.id, innerRadiusMeters) {
        // Default to 0.5 km when no inner radius is set
        val innerKm = (innerRadiusMeters / 1000.0)
            .takeIf { it > 0 } ?: 0.5
        mutableStateOf(String.format("%.1f", innerKm))
    }
    var aatKeyholeAngle by remember(taskWaypoint.id, startAngleDegrees, endAngleDegrees) {
        val rawAngle = endAngleDegrees - startAngleDegrees
        val displayAngle = if (kotlin.math.abs(rawAngle - 90.0) < 1e-2) 90.0 else rawAngle
        mutableStateOf(String.format("%.1f", displayAngle))
    }
    var aatSectorOuterRadius by remember(taskWaypoint.id, outerRadiusMeters, radiusMeters) {
        mutableStateOf(
            ((outerRadiusMeters ?: radiusMeters) / 1000.0).let { String.format("%.1f", it) }
        )
    }

    val waypointRole = when (index) {
        0 -> "Start"
        totalCount - 1 -> "Finish"
        else -> "Turn Point"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = { onExpandToggle(!isExpanded) },
                onLongClick = { onExpandToggle(!isExpanded) }
            ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onExpandToggle(!isExpanded) },
                    shape = RoundedCornerShape(16.dp),
                    color = when (role) {
                        "Start" -> Color(0xFF4CAF50)
                        "Finish" -> Color(0xFFF44336)
                        else -> Color(0xFF546E7A)
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
                    onStartTypeChange = { newType ->
                        selectedAATStartType = newType
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
                        gateWidth = newWidth
                        newWidth.toDoubleOrNull()?.let { radiusKm ->
                            taskViewModel.onUpdateAATArea(index, radiusKm * 1000.0)
                        }
                    },
                    onKeyholeInnerRadiusChange = { newRadius ->
                        aatKeyholeInnerRadius = newRadius
                        newRadius.toDoubleOrNull()?.let { radiusKm ->
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
                        }
                    },
                    onKeyholeAngleChange = { newAngle ->
                        aatKeyholeAngle = newAngle
                        newAngle.toDoubleOrNull()?.let { angleDeg ->
                            taskViewModel.onUpdateAATWaypointPointType(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidth = null,
                                keyholeInnerRadius = null,
                                keyholeAngle = angleDeg,
                                sectorOuterRadius = null
                            )
                        }
                    },
                    onSectorOuterRadiusChange = { newRadius ->
                        aatSectorOuterRadius = newRadius
                        newRadius.toDoubleOrNull()?.let { radiusKm ->
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
                        }
                    }
                )

                AATTargetControls(
                    target = targetSnapshot,
                    onParamChanged = { value ->
                        targetParam.value = value
                        taskViewModel.onSetTargetParam(index, value)
                    },
                    onLockToggle = { taskViewModel.onToggleTargetLock(index) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun inferAATStartType(waypoint: TaskWaypoint): AATStartPointType =
    when (waypoint.customPointType) {
        AATAreaShape.CIRCLE.name -> AATStartPointType.AAT_START_CYLINDER
        AATAreaShape.SECTOR.name -> AATStartPointType.AAT_START_SECTOR
        else -> AATStartPointType.AAT_START_LINE
    }

private fun inferAATFinishType(waypoint: TaskWaypoint): AATFinishPointType =
    when (waypoint.customPointType) {
        AATAreaShape.LINE.name -> AATFinishPointType.AAT_FINISH_LINE
        else -> AATFinishPointType.AAT_FINISH_CYLINDER
    }

private fun inferAATTurnType(waypoint: TaskWaypoint, innerRadiusMeters: Double): AATTurnPointType =
    when {
        innerRadiusMeters > 0.0 -> AATTurnPointType.AAT_KEYHOLE
        waypoint.customPointType == AATAreaShape.SECTOR.name -> AATTurnPointType.AAT_SECTOR
        else -> AATTurnPointType.AAT_CYLINDER
    }

private fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    else -> null
}
