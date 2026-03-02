package com.example.xcpro.map.ui.widgets

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.widgets.MapWidgetId
import com.example.xcpro.map.widgets.MapWidgetSizePolicy

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SideHamburgerMenuImpl(
    widgetManager: MapUIWidgetManager,
    hamburgerOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    sizePx: Float,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val densityScale = remember(density.density, density.fontScale) {
        DensityScale(density = density.density, fontScale = density.fontScale)
    }

    DisposableEffect(Unit) {
        onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.SIDE_HAMBURGER) }
    }

    val displayOffset = remember(isEditMode) { mutableStateOf(hamburgerOffset) }
    val displaySizePx = remember { mutableStateOf(sizePx) }

    LaunchedEffect(hamburgerOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = hamburgerOffset
        }
    }
    LaunchedEffect(sizePx, isEditMode) {
        if (!isEditMode) {
            displaySizePx.value = sizePx
        }
    }

    val containerSizePx = displaySizePx.value
    val containerSizeDp = with(density) { containerSizePx.toDp() }
    val iconSizePx = containerSizePx * 0.6f

    Surface(
        modifier = modifier
            .offset { displayOffset.value.toIntOffset() }
            .size(containerSizeDp)
            .editModeBorder(isEditMode, RectangleShape)
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.SIDE_HAMBURGER,
                    bounds = coordinates.boundsInRoot()
                )
            }
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            // Keep long-press exit behavior without consuming the drag stream.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                            if (longPress != null) {
                                onHamburgerLongPress()
                            }
                        }
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = onHamburgerTap,
                        onLongClick = onHamburgerLongPress
                    )
                }
            ),
        shape = RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(screenWidthPx, screenHeightPx, displaySizePx.value) {
                            detectDragGestures(
                                onDragStart = {
                                    Log.d("MapUIWidgetManager", "Hamburger drag started from ${displayOffset.value}")
                                },
                                onDrag = { change, dragAmount ->
                                    val currentSize = displaySizePx.value
                                    displayOffset.value = MapWidgetMath.boundedOffset(
                                        current = displayOffset.value,
                                        drag = dragAmount,
                                        maxX = screenWidthPx - currentSize,
                                        maxY = screenHeightPx - currentSize
                                    )
                                    change.consumePositionChange()
                                },
                                onDragEnd = {
                                    Log.d("MapUIWidgetManager", "Hamburger drag ended at ${displayOffset.value}")
                                    onOffsetChange(displayOffset.value)
                                }
                            )
                        }
                )
            }

            val lineWidth = with(density) { (iconSizePx * 0.72f).toDp() }
            val lineHeight = with(density) { (iconSizePx * 0.08f).toDp() }
            val columnHeight = with(density) { iconSizePx.toDp() } + lineHeight * 2
            val lineShape = RoundedCornerShape(percent = 50)
            Column(
                verticalArrangement = Arrangement.spacedBy(lineHeight, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.height(columnHeight)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(lineWidth)
                            .height(lineHeight)
                            .clip(lineShape)
                            .background(Color.Black)
                    )
                }
            }

            if (isEditMode) {
                HamburgerResizeHandle(
                    onResize = { dragAmount ->
                        val requested = displaySizePx.value + ((dragAmount.x + dragAmount.y) / 2f)
                        val clamped = MapWidgetSizePolicy.clampSizePx(
                            widgetId = MapWidgetId.SIDE_HAMBURGER,
                            requestedSizePx = requested,
                            screenWidthPx = screenWidthPx,
                            screenHeightPx = screenHeightPx,
                            density = densityScale
                        )
                        if (clamped != displaySizePx.value) {
                            displaySizePx.value = clamped
                            displayOffset.value = MapWidgetMath.boundedOffset(
                                current = displayOffset.value,
                                drag = Offset.Zero,
                                maxX = screenWidthPx - clamped,
                                maxY = screenHeightPx - clamped
                            )
                        }
                    },
                    onResizeEnd = {
                        onSizeChange(displaySizePx.value)
                    }
                )
            }
        }
    }
}

@Composable
private fun HamburgerResizeHandle(
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomEnd)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            onResize(dragAmount)
                            change.consumePositionChange()
                        },
                        onDragEnd = { onResizeEnd() },
                        onDragCancel = { onResizeEnd() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(MapWidgetTheme.editAccentColor, CircleShape)
                    .semantics { contentDescription = "Hamburger resize handle" }
            )
        }
    }
}
