package com.example.xcpro.tasks.aat.ui

import com.example.xcpro.tasks.core.TaskWaypoint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.xcpro.tasks.aat.models.AATStartPointType
import com.example.xcpro.tasks.aat.models.AATFinishPointType
import com.example.xcpro.tasks.aat.models.AATTurnPointType

private const val TAG = "AATTaskPointTypeSelector"

/**
 * AAT-specific task point type selector (Main Router)
 *
 * Routes to appropriate selector based on waypoint role:
 * - Start  AATStartPointSelector
 * - Finish  AATFinishPointSelector
 * - Turn Point  AATTurnPointSelector
 *
 * Extracted from shared TaskPointTypeSelector to achieve 100% task separation compliance.
 * Uses only AAT types and models - zero dependencies on Racing/shared components.
 *
 * Refactored: Individual selectors extracted to separate files to maintain 500-line limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AATTaskPointTypeSelector(
    role: String,
    waypoint: TaskWaypoint,
    selectedStartType: AATStartPointType,
    selectedFinishType: AATFinishPointType,
    selectedTurnType: AATTurnPointType,
    gateWidth: String,
    keyholeInnerRadius: String,
    keyholeAngle: String,
    sectorOuterRadius: String,
    nextWaypoint: TaskWaypoint? = null,
    onStartTypeChange: (AATStartPointType) -> Unit,
    onFinishTypeChange: (AATFinishPointType) -> Unit,
    onTurnTypeChange: (AATTurnPointType) -> Unit,
    onGateWidthChange: (String) -> Unit,
    onKeyholeInnerRadiusChange: (String) -> Unit,
    onKeyholeAngleChange: (String) -> Unit,
    onSectorOuterRadiusChange: (String) -> Unit,
) {
    Log.d(TAG, " AATTaskPointTypeSelector rendering - Role: $role, Waypoint: ${waypoint.title}")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (role) {
            "Start" -> {
                AATStartPointSelector(
                    selectedStartType = selectedStartType,
                    gateWidth = gateWidth,
                    waypoint = waypoint,
                    nextWaypoint = nextWaypoint,
                    onStartTypeChange = onStartTypeChange,
                    onGateWidthChange = onGateWidthChange
                )
            }

            "Finish" -> {
                AATFinishPointSelector(
                    selectedFinishType = selectedFinishType,
                    gateWidth = gateWidth,
                    onFinishTypeChange = onFinishTypeChange,
                    onGateWidthChange = onGateWidthChange
                )
            }

            "Turn Point" -> {
                AATTurnPointSelector(
                    selectedTurnType = selectedTurnType,
                    gateWidth = gateWidth,
                    keyholeInnerRadius = keyholeInnerRadius,
                    keyholeAngle = keyholeAngle,
                    sectorOuterRadius = sectorOuterRadius,
                    waypoint = waypoint,
                    nextWaypoint = nextWaypoint,
                    onTurnTypeChange = onTurnTypeChange,
                    onGateWidthChange = onGateWidthChange,
                    onKeyholeInnerRadiusChange = onKeyholeInnerRadiusChange,
                    onKeyholeAngleChange = onKeyholeAngleChange,
                    onSectorOuterRadiusChange = onSectorOuterRadiusChange
                )
            }

            else -> {
                Text(
                    text = "Unknown waypoint role: $role",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}


