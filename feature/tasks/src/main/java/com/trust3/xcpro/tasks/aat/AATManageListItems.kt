package com.trust3.xcpro.tasks.aat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.trust3.xcpro.tasks.SearchableWaypointField
import com.trust3.xcpro.tasks.TaskSheetViewModel
import com.trust3.xcpro.tasks.core.AATWaypointCustomParams
import com.trust3.xcpro.tasks.aat.ui.AATTaskPointTypeSelector
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.domain.model.TaskTargetSnapshot

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AATReorderableWaypointItem(
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
    val blue = Color(0xFF2196F3)
    val targetParam = remember(targetSnapshot?.targetParam) { mutableStateOf(targetSnapshot?.targetParam ?: 0.5) }
    val typedParams = remember(
        taskWaypoint.customParameters,
        taskWaypoint.lat,
        taskWaypoint.lon,
        taskWaypoint.customRadiusMeters
    ) {
        AATWaypointCustomParams.from(
            source = taskWaypoint.customParameters,
            fallbackLat = taskWaypoint.lat,
            fallbackLon = taskWaypoint.lon,
            fallbackRadiusMeters = taskWaypoint.resolvedCustomRadiusMeters() ?: 10_000.0
        )
    }
    val innerRadiusMeters = typedParams.innerRadiusMeters
    val outerRadiusMeters = typedParams.outerRadiusMeters
    val startAngleDegrees = typedParams.startAngleDegrees
    val endAngleDegrees = typedParams.endAngleDegrees
    val radiusMeters = typedParams.radiusMeters

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
        val innerKm = (innerRadiusMeters / 1000.0).takeIf { it > 0 } ?: 0.5
        mutableStateOf(String.format("%.1f", innerKm))
    }
    var aatKeyholeAngle by remember(taskWaypoint.id, startAngleDegrees, endAngleDegrees) {
        val rawAngle = endAngleDegrees - startAngleDegrees
        val displayAngle = if (kotlin.math.abs(rawAngle - 90.0) < 1e-2) 90.0 else rawAngle
        mutableStateOf(String.format("%.1f", displayAngle))
    }
    var aatSectorOuterRadius by remember(taskWaypoint.id, outerRadiusMeters, radiusMeters) {
        mutableStateOf(((outerRadiusMeters ?: radiusMeters) / 1000.0).let { String.format("%.1f", it) })
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
                        taskViewModel.onUpdateAATWaypointPointTypeMeters(
                            index = index,
                            startType = newType,
                            finishType = null,
                            turnType = null,
                            gateWidthMeters = null,
                            keyholeInnerRadiusMeters = null,
                            keyholeAngle = null,
                            sectorOuterRadiusMeters = null
                        )
                    },
                    onFinishTypeChange = { newType ->
                        selectedAATFinishType = newType
                        taskViewModel.onUpdateAATWaypointPointTypeMeters(
                            index = index,
                            startType = null,
                            finishType = newType,
                            turnType = null,
                            gateWidthMeters = null,
                            keyholeInnerRadiusMeters = null,
                            keyholeAngle = null,
                            sectorOuterRadiusMeters = null
                        )
                    },
                    onTurnTypeChange = { newType ->
                        selectedAATTurnType = newType
                        taskViewModel.onUpdateAATWaypointPointTypeMeters(
                            index = index,
                            startType = null,
                            finishType = null,
                            turnType = newType,
                            gateWidthMeters = null,
                            keyholeInnerRadiusMeters = null,
                            keyholeAngle = null,
                            sectorOuterRadiusMeters = null
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
                            taskViewModel.onUpdateAATWaypointPointTypeMeters(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidthMeters = null,
                                keyholeInnerRadiusMeters = radiusKm * 1000.0,
                                keyholeAngle = null,
                                sectorOuterRadiusMeters = null
                            )
                        }
                    },
                    onKeyholeAngleChange = { newAngle ->
                        aatKeyholeAngle = newAngle
                        newAngle.toDoubleOrNull()?.let { angleDeg ->
                            taskViewModel.onUpdateAATWaypointPointTypeMeters(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidthMeters = null,
                                keyholeInnerRadiusMeters = null,
                                keyholeAngle = angleDeg,
                                sectorOuterRadiusMeters = null
                            )
                        }
                    },
                    onSectorOuterRadiusChange = { newRadius ->
                        aatSectorOuterRadius = newRadius
                        newRadius.toDoubleOrNull()?.let { radiusKm ->
                            taskViewModel.onUpdateAATWaypointPointTypeMeters(
                                index = index,
                                startType = null,
                                finishType = null,
                                turnType = null,
                                gateWidthMeters = null,
                                keyholeInnerRadiusMeters = null,
                                keyholeAngle = null,
                                sectorOuterRadiusMeters = radiusKm * 1000.0
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
