package com.example.xcpro.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcpro.map.MapTaskScreenManager
import com.example.xcpro.map.model.MapLocationUiModel

@Composable
fun MapActionButtons(
    taskScreenManager: MapTaskScreenManager,
    currentLocation: MapLocationUiModel?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    showOgnThermals: Boolean,
    showAdsbTraffic: Boolean,
    onRecenter: () -> Unit,
    onToggleDistanceCircles: () -> Unit,
    onToggleOgnThermals: () -> Unit,
    onToggleAdsbTraffic: () -> Unit,
    onReturn: () -> Unit,
    onShowQnhDialog: () -> Unit,
    showQnhFab: Boolean,
    onDismissQnhFab: () -> Unit,
    showVarioDemoFab: Boolean,
    showAatEditFab: Boolean,
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
    val minTopStartPadding = 72.dp
    val preferredTopStartPadding = 130.dp
    val minTopStep = fabSize + 8.dp
    val demoFabSize = fabSize
    val demoSpacing = 12.6.dp
    val demoSim3BottomPadding = 16.dp
    val demoSimBottomPadding = demoSim3BottomPadding + demoFabSize + demoSpacing
    val demoSim2BottomPadding = demoSimBottomPadding + demoFabSize + demoSpacing
    val demoRefBottomPadding = demoSim2BottomPadding + demoFabSize + demoSpacing
    val demoTaskBottomPadding = if (showVarioDemoFab) {
        demoRefBottomPadding + demoFabSize + demoSpacing
    } else {
        demoSim3BottomPadding
    }
    val isTaskPanelExpanded by taskScreenManager.showTaskBottomSheet.collectAsStateWithLifecycle(initialValue = false)

    val adsbIndex = 0
    val qnhIndex = if (showQnhFab) 1 else -1
    val distanceIndex = if (showQnhFab) 2 else 1
    val thermalIndex = distanceIndex + 1
    val topControlsCount = thermalIndex + 1
    val showRecenterControl = showRecenterButton && currentLocation != null && !showReturnButton
    val centerControlsCount = (if (showRecenterControl) 1 else 0) + if (showReturnButton) 1 else 0

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
    ) {
        val availableHeight = maxHeight
        val centerGroupHeight = when (centerControlsCount) {
            0 -> 0.dp
            else -> fabSize + fabStep * (centerControlsCount - 1)
        }
        val desiredCenterTop = when (centerControlsCount) {
            0 -> 0.dp
            else -> ((availableHeight - centerGroupHeight) / 2f).coerceAtLeast(0.dp)
        }
        val maxTopBottomForCenter = (desiredCenterTop - fabSpacing).coerceAtLeast(0.dp)
        val topStep = when {
            centerControlsCount == 0 || topControlsCount <= 1 -> fabStep
            else -> {
                val provisionalTopHeight = fabSize + fabStep * (topControlsCount - 1)
                val provisionalTopStart = (maxTopBottomForCenter - provisionalTopHeight)
                    .coerceIn(minTopStartPadding, preferredTopStartPadding)
                val availableForSteps = (maxTopBottomForCenter - provisionalTopStart - fabSize)
                    .coerceAtLeast(0.dp)
                (availableForSteps / (topControlsCount - 1))
                    .coerceIn(minTopStep, fabStep)
            }
        }
        val topGroupHeight = when (topControlsCount) {
            0 -> 0.dp
            else -> fabSize + topStep * (topControlsCount - 1)
        }
        val topStackStart = when {
            centerControlsCount == 0 -> preferredTopStartPadding
            else -> (maxTopBottomForCenter - topGroupHeight)
                .coerceIn(minTopStartPadding, preferredTopStartPadding)
        }
        val adsbTopPadding = topStackStart + topStep * adsbIndex
        val qnhTopPadding = if (qnhIndex >= 0) topStackStart + topStep * qnhIndex else 0.dp
        val distanceTopPadding = topStackStart + topStep * distanceIndex
        val thermalTopPadding = topStackStart + topStep * thermalIndex
        val topStackBottom = thermalTopPadding + fabSize
        val minCenterTop = (topStackBottom + fabSpacing).coerceAtLeast(0.dp)
        val maxCenterTop = (availableHeight - centerGroupHeight - fabSpacing).coerceAtLeast(0.dp)
        val resolvedCenterMin = minCenterTop.coerceAtMost(maxCenterTop)
        val centerGroupTop = when (centerControlsCount) {
            0 -> 0.dp
            else -> desiredCenterTop.coerceIn(resolvedCenterMin, maxCenterTop)
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

        AdsbTrafficButton(
            isEnabled = showAdsbTraffic,
            onToggle = onToggleAdsbTraffic,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = adsbTopPadding, end = 16.dp)
        )

        if (showQnhFab) {
            QnhButton(
                onClick = onShowQnhDialog,
                onDismiss = onDismissQnhFab,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = qnhTopPadding, end = 16.dp)
            )
        }

        DistanceCirclesButton(
            isEnabled = showDistanceCircles,
            onToggle = onToggleDistanceCircles,
            isBottomSheetVisible = isTaskPanelExpanded,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = distanceTopPadding, end = 16.dp)
        )

        OgnThermalsButton(
            isEnabled = showOgnThermals,
            onToggle = onToggleOgnThermals,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = thermalTopPadding, end = 16.dp)
        )

        if (showVarioDemoFab) {
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
