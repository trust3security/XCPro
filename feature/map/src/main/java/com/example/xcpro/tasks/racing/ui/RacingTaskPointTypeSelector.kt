package com.example.xcpro.tasks.racing.ui

import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.RacingStartDistanceUi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType

private const val TAG = "RacingTaskPointTypeSelector"

/**
 * Racing-specific task point type selector (Main Router)
 *
 * Routes to appropriate selector based on waypoint role:
 * - Start  RacingStartPointSelector
 * - Finish  RacingFinishPointSelector
 * - Turn Point  RacingTurnPointSelector
 *
 * Extracted from shared TaskPointTypeSelector to achieve 100% task separation compliance.
 * Uses only Racing types and models - zero dependencies on AAT/shared components.
 *
 * Refactored: Individual selectors extracted to separate files to maintain 500-line limit.
 */
@Composable
fun RacingTaskPointTypeSelector(
    role: String,
    waypoint: TaskWaypoint,
    selectedStartType: RacingStartPointType,
    selectedFinishType: RacingFinishPointType,
    selectedTurnType: RacingTurnPointType,
    gateWidth: String,
    keyholeInnerRadius: String,
    keyholeAngle: String,
    faiQuadrantOuterRadius: String,
    startDistanceUi: RacingStartDistanceUi?,
    turnDistanceToNextMeters: Double?,
    unitsPreferences: UnitsPreferences,
    onStartTypeChange: (RacingStartPointType) -> Unit,
    onFinishTypeChange: (RacingFinishPointType) -> Unit,
    onTurnTypeChange: (RacingTurnPointType) -> Unit,
    onGateWidthChange: (String) -> Unit,
    onKeyholeInnerRadiusChange: (String) -> Unit,
    onKeyholeAngleChange: (String) -> Unit,
    onFAIQuadrantOuterRadiusChange: (String) -> Unit,
) {
    Log.d(TAG, " RacingTaskPointTypeSelector rendering - Role: $role, Waypoint: ${waypoint.title}")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (role) {
            "Start" -> {
                RacingStartPointSelector(
                    selectedStartType = selectedStartType,
                    gateWidth = gateWidth,
                    distanceToNext = startDistanceUi,
                    unitsPreferences = unitsPreferences,
                    onStartTypeChange = onStartTypeChange,
                    onGateWidthChange = onGateWidthChange
                )
            }

            "Finish" -> {
                RacingFinishPointSelector(
                    selectedFinishType = selectedFinishType,
                    gateWidth = gateWidth,
                    onFinishTypeChange = onFinishTypeChange,
                    onGateWidthChange = onGateWidthChange
                )
            }

            "Turn Point" -> {
                RacingTurnPointSelector(
                    selectedTurnType = selectedTurnType,
                    gateWidth = gateWidth,
                    keyholeInnerRadius = keyholeInnerRadius,
                    keyholeAngle = keyholeAngle,
                    faiQuadrantOuterRadius = faiQuadrantOuterRadius,
                    distanceToNextMeters = turnDistanceToNextMeters,
                    unitsPreferences = unitsPreferences,
                    onTurnTypeChange = onTurnTypeChange,
                    onGateWidthChange = onGateWidthChange,
                    onKeyholeInnerRadiusChange = onKeyholeInnerRadiusChange,
                    onKeyholeAngleChange = onKeyholeAngleChange,
                    onFAIQuadrantOuterRadiusChange = onFAIQuadrantOuterRadiusChange
                )
            }
        }
    }
}


