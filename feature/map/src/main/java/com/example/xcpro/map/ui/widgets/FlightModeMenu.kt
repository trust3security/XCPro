@file:Suppress("DEPRECATION")

package com.example.xcpro.map.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.ui.widgets.common.editModeBorder
import com.example.xcpro.map.ui.widgets.common.updateWidgetGestureRegion
import kotlin.math.roundToInt

/**
 * Flight mode selector that mirrors the hamburger widget's gesture plumbing so taps land reliably.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FlightModeMenuContent(
    widgetManager: MapUIWidgetManager,
    currentMode: FlightMode,
    visibleModes: List<FlightMode>,
    onModeChange: (FlightMode) -> Unit,
    flightModeOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onOffsetChange: (Offset) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    widthDp: Float = 96f,
    heightDp: Float = 36f
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }

    DisposableEffect(Unit) {
        onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.FLIGHT_MODE) }
    }

    val displayOffset = remember(isEditMode) { mutableStateOf(flightModeOffset) }
    LaunchedEffect(flightModeOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = flightModeOffset
        }
    }

    var isExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            isExpanded = false
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
            .width(widthDp.dp)
            .height(heightDp.dp)
            .editModeBorder(isEditMode, RoundedCornerShape(18.dp))
            .onGloballyPositioned { coordinates ->
                widgetManager.updateWidgetGestureRegion(
                    target = MapOverlayGestureTarget.FLIGHT_MODE,
                    bounds = coordinates
                )
            }
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { isExpanded = false })
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            isExpanded = true
                        }
                    )
                }
            )
            .pointerInput(isEditMode, screenWidthPx, screenHeightPx) {
                if (isEditMode) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            displayOffset.value = Offset(
                                x = (displayOffset.value.x + dragAmount.x).coerceIn(
                                    0f,
                                    (screenWidthPx - widthPx).coerceAtLeast(0f)
                                ),
                                y = (displayOffset.value.y + dragAmount.y).coerceIn(
                                    0f,
                                    (screenHeightPx - heightPx).coerceAtLeast(0f)
                                )
                            )
                            change.consumePositionChange()
                        },
                        onDragEnd = {
                            onOffsetChange(displayOffset.value)
                        }
                    )
                }
            }
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colorForMode(currentMode))
                )
                Text(
                    text = currentMode.displayName,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = {
                isExpanded = false
            },
            shape = RoundedCornerShape(20.dp)
        ) {
            visibleModes.forEach { mode ->
                DropdownMenuItem(
                    onClick = {
                        onModeChange(mode)
                        isExpanded = false
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        colorForMode(mode).copy(
                                            alpha = if (mode == currentMode) 1f else 0.4f
                                        )
                                    )
                            )
                            Text(
                                text = mode.displayName,
                                style = if (mode == currentMode) {
                                    MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                } else {
                                    MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun colorForMode(mode: FlightMode): Color {
    return when (mode) {
        FlightMode.CRUISE -> Color(0xFF2196F3)
        FlightMode.THERMAL -> Color(0xFF9C27B0)
        FlightMode.FINAL_GLIDE -> Color(0xFFF44336)
    }
}
