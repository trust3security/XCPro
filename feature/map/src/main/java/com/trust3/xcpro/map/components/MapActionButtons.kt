package com.trust3.xcpro.map.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.map.model.MapLocationUiModel

@Composable
fun MapActionButtons(
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    onRecenter: () -> Unit,
    onReturn: () -> Unit,
    showVarioDemoFab: Boolean,
    showAatEditFab: Boolean,
    onSyntheticThermalReplayClick: () -> Unit,
    onSyntheticThermalReplayWindNoisyClick: () -> Unit,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
    onVarioDemoSim3Click: () -> Unit,
    showRacingReplayFab: Boolean,
    onRacingReplayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topInset = 24.dp
    val bottomInset = 80.dp
    val fabSize = 48.dp
    val fabSpacing = 16.dp
    val fabStep = fabSize + fabSpacing
    val demoFabSize = fabSize
    val demoSpacing = 12.6.dp
    val demoSim3BottomPadding = 16.dp
    val demoSimBottomPadding = demoSim3BottomPadding + demoFabSize + demoSpacing
    val demoSim2BottomPadding = demoSimBottomPadding + demoFabSize + demoSpacing
    val demoRefBottomPadding = demoSim2BottomPadding + demoFabSize + demoSpacing
    val demoThermalCleanBottomPadding = demoRefBottomPadding + demoFabSize + demoSpacing
    val demoThermalWindNoisyBottomPadding = demoThermalCleanBottomPadding + demoFabSize + demoSpacing
    val demoTaskBottomPadding = if (showVarioDemoFab) {
        demoThermalWindNoisyBottomPadding + demoFabSize + demoSpacing
    } else {
        demoSim3BottomPadding
    }
    val showRecenterControl = showRecenterButton && currentLocation != null && !showReturnButton
    val centerControlsCount = (if (showRecenterControl) 1 else 0) + if (showReturnButton) 1 else 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
    ) {
        val availableHeight = maxHeight
        val centerGroupHeight = if (centerControlsCount == 0) 0.dp else fabSize + fabStep * (centerControlsCount - 1)
        val centerGroupTop = if (centerControlsCount == 0) {
            0.dp
        } else {
            ((availableHeight - centerGroupHeight) / 2f).coerceAtLeast(0.dp)
        }
        val recenterTopPadding = centerGroupTop
        val returnTopPadding = if (showRecenterControl) {
            centerGroupTop + fabStep
        } else {
            centerGroupTop
        }
        // Keep demo replay controls in a stable lane across tracking/recenter state changes.
        // Only reserve extra end space when the AAT edit FAB occupies the same bottom-end lane.
        val demoLaneEndPadding = if (showAatEditFab) 80.dp else 16.dp

        if (showRecenterControl) {
            RecenterButton(
                onRecenter = onRecenter,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = recenterTopPadding)
            )
        }

        if (showReturnButton) {
            ReturnButton(
                onReturn = onReturn,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = returnTopPadding, end = 16.dp)
                )
        }

        if (showVarioDemoFab) {
            VarioDemoButton(
                onClick = onSyntheticThermalReplayWindNoisyClick,
                badgeText = "THN",
                badgeColor = MaterialTheme.colorScheme.primary,
                contentDescription = "Run synthetic thermal replay (wind-noisy)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoThermalWindNoisyBottomPadding)
            )
            VarioDemoButton(
                onClick = onSyntheticThermalReplayClick,
                badgeText = "THR",
                badgeColor = MaterialTheme.colorScheme.primaryContainer,
                contentDescription = "Run synthetic thermal replay (clean)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoThermalCleanBottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoReferenceClick,
                badgeText = "REF",
                badgeColor = MaterialTheme.colorScheme.primary,
                contentDescription = "Run vario demo replay (reference)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoRefBottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSim2Click,
                badgeText = "SIM2",
                badgeColor = MaterialTheme.colorScheme.error,
                contentDescription = "Run vario demo replay (sim2)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoSim2BottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSimClick,
                badgeText = "SIM",
                badgeColor = MaterialTheme.colorScheme.tertiary,
                contentDescription = "Run vario demo replay (sim)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoSimBottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSim3Click,
                badgeText = "SIM3",
                badgeColor = MaterialTheme.colorScheme.secondary,
                contentDescription = "Run vario demo replay (sim3)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoSim3BottomPadding)
            )
        }

        if (showRacingReplayFab) {
            VarioDemoButton(
                onClick = onRacingReplayClick,
                badgeText = "TASK",
                badgeColor = MaterialTheme.colorScheme.secondary,
                contentDescription = "Run racing task replay",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = demoLaneEndPadding, bottom = demoTaskBottomPadding)
            )
        }
    }
}
