package com.example.xcpro.tasks.racing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.tasks.SearchableWaypointField
import com.example.xcpro.tasks.TaskSheetViewModel
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.ui.RacingTaskPointTypeSelector
import kotlinx.coroutines.launch

/**
 * Racing task waypoint list + item UI.
 * Keeps per-row UI state local to the list and avoids side effects outside of callbacks.
 */
@Composable
internal fun RacingReorderableWaypointList(
    waypoints: List<TaskWaypoint>,
    allWaypoints: List<WaypointData> = emptyList(),
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onTaskPointTypeUpdate: (Int, RacingStartPointType?, RacingFinishPointType?, RacingTurnPointType?, Double?, Double?, Double?, Double?) -> Unit,
    onWaypointReplace: (Int, SearchWaypoint) -> Unit,
    taskViewModel: TaskSheetViewModel,
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
                taskViewModel = taskViewModel,
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
private fun RacingReorderableWaypointItem(
    waypoint: SearchWaypoint,
    taskWaypoint: TaskWaypoint,
    allWaypoints: List<WaypointData> = emptyList(),
    index: Int,
    totalCount: Int,
    role: String,
    nextWaypoint: TaskWaypoint? = null,
    taskViewModel: TaskSheetViewModel,
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

    val selectedStartType = remember(taskWaypoint) { inferRacingStartType(taskWaypoint) }
    val selectedFinishType = remember(taskWaypoint) { inferRacingFinishType(taskWaypoint) }
    val selectedTurnType = remember(taskWaypoint) { inferRacingTurnType(taskWaypoint) }

    // Keep track of which waypoint role this is
    val waypointRole = when {
        index == 0 -> "Start"
        index == totalCount - 1 && totalCount > 1 -> "Finish"
        else -> "Turn Point"
    }

    val gateWidthValue = remember(taskWaypoint.customRadius) {
        taskWaypoint.customRadius ?: 0.0
    }
    val keyholeInnerRadiusValue = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["keyholeInnerRadius"].asDoubleOrNull() ?: 0.5
    }
    val keyholeAngleValue = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["keyholeAngle"].asDoubleOrNull() ?: 45.0
    }
    val faiQuadrantOuterRadiusValue = remember(taskWaypoint.customParameters) {
        taskWaypoint.customParameters["faiQuadrantOuterRadius"].asDoubleOrNull() ?: 10.0
    }

    val turnDistanceToNextKm = remember(taskWaypoint, nextWaypoint) {
        nextWaypoint?.let { taskViewModel.calculateDistanceToNextWaypointKm(taskWaypoint, it) }
    }
    val startDistanceUi = remember(taskWaypoint, nextWaypoint, selectedStartType) {
        nextWaypoint?.let {
            taskViewModel.resolveRacingStartDistanceUi(
                selectedStartType = selectedStartType,
                startWaypoint = taskWaypoint,
                nextWaypoint = it
            )
        }
    }

    val displayValue = gateWidthValue.toString()

    var gateWidth by remember(taskWaypoint.id, gateWidthValue) { mutableStateOf(displayValue) }

    LaunchedEffect(gateWidthValue) {
        gateWidth = gateWidthValue.toString()
    }

    var keyholeInnerRadius by remember(taskWaypoint.id, keyholeInnerRadiusValue) {
        mutableStateOf(keyholeInnerRadiusValue.toString())
    }
    var keyholeAngle by remember(taskWaypoint.id, keyholeAngleValue) {
        mutableStateOf(keyholeAngleValue.toString())
    }
    var faiQuadrantOuterRadius by remember(taskWaypoint.id, faiQuadrantOuterRadiusValue) {
        mutableStateOf(faiQuadrantOuterRadiusValue.toString())
    }

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

                // RACING TASK POINT TYPE SELECTOR - Only Racing types
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
                    startDistanceUi = startDistanceUi,
                    turnDistanceToNextKm = turnDistanceToNextKm,
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
                        // BUG FIX: Pass correct default based on new type, not null
                        val typeSpecificDefault = when (newType) {
                            RacingTurnPointType.KEYHOLE -> 10.0  // 10km keyhole outer radius
                            RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 0.5
                        }
                        val faiQuadrantDefault = if (newType == RacingTurnPointType.FAI_QUADRANT) 10.0 else null
                        // UI SYNC FIX: Update local UI state to match model
                        gateWidth = typeSpecificDefault.toString()
                        if (faiQuadrantDefault != null) {
                            faiQuadrantOuterRadius = faiQuadrantDefault.toString()
                        }
                        onTaskPointTypeUpdate(selectedStartType, selectedFinishType, newType,
                            typeSpecificDefault, null, null, faiQuadrantDefault)
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

private fun inferRacingStartType(waypoint: TaskWaypoint): RacingStartPointType =
    waypoint.customPointType
        ?.let { runCatching { RacingStartPointType.valueOf(it) }.getOrNull() }
        ?: RacingStartPointType.START_CYLINDER

private fun inferRacingFinishType(waypoint: TaskWaypoint): RacingFinishPointType =
    waypoint.customPointType
        ?.let { runCatching { RacingFinishPointType.valueOf(it) }.getOrNull() }
        ?: RacingFinishPointType.FINISH_CYLINDER

private fun inferRacingTurnType(waypoint: TaskWaypoint): RacingTurnPointType =
    waypoint.customPointType
        ?.let { runCatching { RacingTurnPointType.valueOf(it) }.getOrNull() }
        ?: RacingTurnPointType.TURN_POINT_CYLINDER

private fun Any?.asDoubleOrNull(): Double? = when (this) {
    is Number -> this.toDouble()
    else -> null
}
