package com.example.xcpro.map.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
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
    showOgnTraffic: Boolean,
    showAdsbTraffic: Boolean,
    showForecastOverlay: Boolean,
    onRecenter: () -> Unit,
    onToggleDistanceCircles: () -> Unit,
    onToggleOgnTraffic: () -> Unit,
    onToggleAdsbTraffic: () -> Unit,
    onShowForecastSheet: () -> Unit,
    onReturn: () -> Unit,
    onShowQnhDialog: () -> Unit,
    showQnhFab: Boolean,
    onDismissQnhFab: () -> Unit,
    showVarioDemoFab: Boolean,
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
    val centerOffset = (bottomInset - topInset) * 0.5f
    val qnhTopPadding = 130.dp
    val fabSpacing = 64.dp
    val distanceTopPadding = if (showQnhFab) qnhTopPadding + fabSpacing else qnhTopPadding
    val ognTopPadding = distanceTopPadding + fabSpacing
    val adsbTopPadding = ognTopPadding + fabSpacing
    val forecastTopPadding = adsbTopPadding + fabSpacing
    val demoFabSize = 48.dp
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
    ) {
        if (showRecenterButton && currentLocation != null) {
            RecenterButton(
                onRecenter = onRecenter,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        if (showReturnButton) {
            ReturnButton(
                onReturn = onReturn,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .offset(y = centerOffset)
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

        OgnTrafficButton(
            isEnabled = showOgnTraffic,
            onToggle = onToggleOgnTraffic,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = ognTopPadding, end = 16.dp)
        )

        AdsbTrafficButton(
            isEnabled = showAdsbTraffic,
            onToggle = onToggleAdsbTraffic,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = adsbTopPadding, end = 16.dp)
        )

        ForecastOverlayButton(
            isEnabled = showForecastOverlay,
            onClick = onShowForecastSheet,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = forecastTopPadding, end = 16.dp)
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

        if (showVarioDemoFab) {
            VarioDemoButton(
                onClick = onVarioDemoReferenceClick,
                badgeText = "REF",
                badgeColor = MaterialTheme.colorScheme.primary,
                contentDescription = "Run vario demo replay (reference)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = demoRefBottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSim2Click,
                badgeText = "SIM2",
                badgeColor = MaterialTheme.colorScheme.error,
                contentDescription = "Run vario demo replay (sim2)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = demoSim2BottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSimClick,
                badgeText = "SIM",
                badgeColor = MaterialTheme.colorScheme.tertiary,
                contentDescription = "Run vario demo replay (sim)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = demoSimBottomPadding)
            )
            VarioDemoButton(
                onClick = onVarioDemoSim3Click,
                badgeText = "SIM3",
                badgeColor = MaterialTheme.colorScheme.secondary,
                contentDescription = "Run vario demo replay (sim3)",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = demoSim3BottomPadding)
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
                    .padding(end = 16.dp, bottom = demoTaskBottomPadding)
            )
        }
    }
}
