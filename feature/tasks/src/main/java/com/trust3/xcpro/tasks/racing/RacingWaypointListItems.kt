package com.trust3.xcpro.tasks.racing

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.common.waypoint.WaypointData
import com.trust3.xcpro.common.units.UnitsPreferences
import com.trust3.xcpro.tasks.SearchableWaypointField
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.core.RacingWaypointCustomParams
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType
import com.trust3.xcpro.tasks.racing.ui.RacingTaskPointTypeSelector

private const val METERS_PER_KILOMETER = 1000.0

@Composable
internal fun RacingReorderableWaypointItem(
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
    onTaskPointTypeUpdate: (
        RacingStartPointType?,
        RacingFinishPointType?,
        RacingTurnPointType?,
        Double?,
        Double?,
        Double?,
        Double?
    ) -> Unit,
    onWaypointReplace: (SearchWaypoint) -> Unit,
    unitsPreferences: UnitsPreferences,
    qnhValue: String? = null
) {
    val blue = Color(0xFF007AFF)

    val selectedStartType = remember(taskWaypoint) { inferRacingStartType(taskWaypoint) }
    val selectedFinishType = remember(taskWaypoint) { inferRacingFinishType(taskWaypoint) }
    val selectedTurnType = remember(taskWaypoint) { inferRacingTurnType(taskWaypoint) }

    val waypointRole = when {
        index == 0 -> "Start"
        index == totalCount - 1 && totalCount > 1 -> "Finish"
        else -> "Turn Point"
    }

    val gateWidthValue = remember(taskWaypoint.customRadiusMeters) {
        taskWaypoint.resolvedCustomRadiusMeters()?.div(METERS_PER_KILOMETER) ?: 0.0
    }
    val racingParams = remember(taskWaypoint.customParameters) {
        RacingWaypointCustomParams.from(taskWaypoint.customParameters)
    }
    val keyholeInnerRadiusValue = racingParams.keyholeInnerRadiusMeters / METERS_PER_KILOMETER
    val keyholeAngleValue = racingParams.keyholeAngle
    val faiQuadrantOuterRadiusValue = racingParams.faiQuadrantOuterRadiusMeters / METERS_PER_KILOMETER

    val turnDistanceToNextMeters = remember(taskWaypoint, nextWaypoint) {
        nextWaypoint?.let { taskViewModel.calculateDistanceToNextWaypointMeters(taskWaypoint, it) }
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
            color = if (isExpanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
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

                if (!isExpanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        IconButton(onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier = Modifier.size(36.dp),
                                tint = if (onMoveUp != null) blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier = Modifier.size(36.dp),
                                tint = if (onMoveDown != null) blue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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
                    turnDistanceToNextMeters = turnDistanceToNextMeters,
                    unitsPreferences = unitsPreferences,
                    onStartTypeChange = { newType ->
                        onTaskPointTypeUpdate(
                            newType,
                            selectedFinishType,
                            selectedTurnType,
                            gateWidth.toDoubleOrNull()?.times(METERS_PER_KILOMETER),
                            keyholeInnerRadius.toDoubleOrNull()?.times(METERS_PER_KILOMETER),
                            keyholeAngle.toDoubleOrNull(),
                            faiQuadrantOuterRadius.toDoubleOrNull()?.times(METERS_PER_KILOMETER)
                        )
                    },
                    onFinishTypeChange = { newType ->
                        onTaskPointTypeUpdate(
                            selectedStartType,
                            newType,
                            selectedTurnType,
                            gateWidth.toDoubleOrNull()?.times(METERS_PER_KILOMETER),
                            keyholeInnerRadius.toDoubleOrNull()?.times(METERS_PER_KILOMETER),
                            keyholeAngle.toDoubleOrNull(),
                            faiQuadrantOuterRadius.toDoubleOrNull()?.times(METERS_PER_KILOMETER)
                        )
                    },
                    onTurnTypeChange = { newType ->
                        val typeSpecificDefault = when (newType) {
                            RacingTurnPointType.KEYHOLE -> 10.0
                            RacingTurnPointType.TURN_POINT_CYLINDER,
                            RacingTurnPointType.FAI_QUADRANT -> 0.5
                        }
                        val faiQuadrantDefault = if (newType == RacingTurnPointType.FAI_QUADRANT) 10.0 else null
                        gateWidth = typeSpecificDefault.toString()
                        if (faiQuadrantDefault != null) {
                            faiQuadrantOuterRadius = faiQuadrantDefault.toString()
                        }
                        onTaskPointTypeUpdate(
                            selectedStartType,
                            selectedFinishType,
                            newType,
                            typeSpecificDefault * METERS_PER_KILOMETER,
                            null,
                            null,
                            faiQuadrantDefault?.times(METERS_PER_KILOMETER)
                        )
                    },
                    onGateWidthChange = { newWidth ->
                        gateWidth = newWidth
                        try {
                            val widthMeters = newWidth.toDouble() * METERS_PER_KILOMETER
                            onTaskPointTypeUpdate(null, null, null, widthMeters, null, null, null)
                        } catch (_: NumberFormatException) {
                        }
                    },
                    onKeyholeInnerRadiusChange = { newRadius ->
                        keyholeInnerRadius = newRadius
                        try {
                            val radiusMeters = newRadius.toDouble() * METERS_PER_KILOMETER
                            onTaskPointTypeUpdate(null, null, null, null, radiusMeters, null, null)
                        } catch (_: NumberFormatException) {
                        }
                    },
                    onKeyholeAngleChange = { newAngle ->
                        keyholeAngle = newAngle
                        try {
                            val angle = newAngle.toDouble()
                            onTaskPointTypeUpdate(null, null, null, null, null, angle, null)
                        } catch (_: NumberFormatException) {
                        }
                    },
                    onFAIQuadrantOuterRadiusChange = { newRadius ->
                        faiQuadrantOuterRadius = newRadius
                        try {
                            val radiusMeters = newRadius.toDouble() * METERS_PER_KILOMETER
                            onTaskPointTypeUpdate(null, null, null, null, null, null, radiusMeters)
                        } catch (_: NumberFormatException) {
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
