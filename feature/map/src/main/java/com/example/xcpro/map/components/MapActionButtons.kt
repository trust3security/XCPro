package com.example.xcpro.map.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.map.MapTaskScreenManager
import com.example.ui1.icons.LocationSailplane

@Composable
fun MapActionButtons(
    taskScreenManager: MapTaskScreenManager,
    currentLocation: GPSData?,
    showRecenterButton: Boolean,
    showReturnButton: Boolean,
    showDistanceCircles: Boolean,
    onRecenter: () -> Unit,
    onToggleDistanceCircles: () -> Unit,
    onReturn: () -> Unit,
    onShowQnhDialog: () -> Unit,
    showQnhFab: Boolean,
    onDismissQnhFab: () -> Unit,
    showVarioDemoFab: Boolean,
    onVarioDemoReferenceClick: () -> Unit,
    onVarioDemoSimClick: () -> Unit,
    onVarioDemoSim2Click: () -> Unit,
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
    val demoFabSize = 48.dp
    val demoSpacing = 12.6.dp // ~2mm gap between FAB edges
    val demoSimBottomPadding = 16.dp
    val demoSim2BottomPadding = demoSimBottomPadding + demoFabSize + demoSpacing
    val demoRefBottomPadding = demoSim2BottomPadding + demoFabSize + demoSpacing
    val demoTaskBottomPadding = if (showVarioDemoFab) {
        demoRefBottomPadding + demoFabSize + demoSpacing
    } else {
        demoSimBottomPadding
    }
    val isTaskSearchVisible by taskScreenManager.showTaskScreen.collectAsStateWithLifecycle()
    val isTaskSheetVisible by taskScreenManager.showTaskBottomSheet.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
            // ❌ REMOVED: .zIndex(50f) was blocking hamburger menu at zIndex(4f)
            // Individual buttons have their own z-index values as needed
    ) {
        // Recenter Button
        if (showRecenterButton && currentLocation != null) {
            RecenterButton(
                onRecenter = onRecenter,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // Return Button
        if (showReturnButton) {
            ReturnButton(
                onReturn = onReturn,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .offset(y = centerOffset)
            )
        }

        // Distance Circles Button - lower z-index when bottom sheet is open
        DistanceCirclesButton(
            isEnabled = showDistanceCircles,
            onToggle = onToggleDistanceCircles,
            isBottomSheetVisible = isTaskSheetVisible || isTaskSearchVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = distanceTopPadding, end = 16.dp)
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

@Composable
private fun RecenterButton(
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 16.dp)
            .size(48.dp)
    ) {
        FloatingActionButton(
            onClick = {
                if (com.example.xcpro.map.BuildConfig.DEBUG) Log.d("MapActionButtons", "Recenter button clicked")
                onRecenter()
            },
            modifier = Modifier.matchParentSize(),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = LocationSailplane,
                contentDescription = "Recenter",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ReturnButton(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
    ) {
        FloatingActionButton(
            onClick = {
                if (com.example.xcpro.map.BuildConfig.DEBUG) Log.d("MapActionButtons", "Return button clicked")
                onReturn()
            },
            modifier = Modifier.matchParentSize(),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "Return to Previous",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DistanceCirclesButton(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    isBottomSheetVisible: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .zIndex(if (isBottomSheetVisible) 10f else 50f)
    ) {
        FloatingActionButton(
            onClick = {
                if (com.example.xcpro.map.BuildConfig.DEBUG) Log.d("MapActionButtons", "Distance circles button clicked")
                onToggle()
            },
            modifier = Modifier.matchParentSize(),
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            ThreeCirclesIcon(
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QnhButton(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .zIndex(50f)
    ) {
            FloatingActionButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = "Set QNH",
                modifier = Modifier.size(24.dp)
            )
        }

        Surface(
            color = Color(0xFFD32F2F),
            shape = CircleShape,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 5.dp, y = (-5).dp)
                .size(18.dp)
                .zIndex(60f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss QNH control",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
private fun VarioDemoButton(
    onClick: () -> Unit,
    badgeText: String,
    badgeColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .zIndex(50f)
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }

        Surface(
            color = badgeColor,
            shape = CircleShape,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .size(22.dp)
                .zIndex(60f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ThreeCirclesIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val strokeWidth = 2.dp.toPx()

        // Draw 3 concentric circles with increasing radii
        val maxRadius = size.minDimension / 2f - strokeWidth

        // Inner circle (33% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius * 0.33f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Middle circle (66% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius * 0.66f,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Outer circle (100% of max radius)
        drawCircle(
            color = color,
            radius = maxRadius,
            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )
    }
}












