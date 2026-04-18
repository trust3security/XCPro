package com.trust3.xcpro.map.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.core.common.geometry.DensityScale
import com.trust3.xcpro.map.MapOverlayGestureTarget
import com.trust3.xcpro.map.widgets.MapWidgetId
import com.trust3.xcpro.map.widgets.MapWidgetSizePolicy

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsShortcutWidgetImpl(
    widgetManager: MapUIWidgetManager,
    settingsOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    sizePx: Float,
    onSettingsTap: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val density = LocalDensity.current
    val densityScale = remember(density.density, density.fontScale) {
        DensityScale(density = density.density, fontScale = density.fontScale)
    }

    DisposableEffect(Unit) {
        onDispose {
            widgetManager.clearGestureRegion(MapOverlayGestureTarget.SETTINGS_SHORTCUT)
        }
    }

    val displayOffset = remember(isEditMode) { mutableStateOf(settingsOffset) }
    val displaySizePx = remember { mutableStateOf(sizePx) }

    LaunchedEffect(settingsOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = settingsOffset
        }
    }
    LaunchedEffect(sizePx, isEditMode) {
        if (!isEditMode) {
            displaySizePx.value = sizePx
        }
    }

    val containerSizePx = displaySizePx.value
    val containerSizeDp = with(density) { containerSizePx.toDp() }
    val iconSizeDp = with(density) { (containerSizePx * 0.46f).toDp() }
    val surfaceShape = if (isEditMode) RectangleShape else shape

    Surface(
        modifier = modifier
            .offset { displayOffset.value.toIntOffset() }
            .size(containerSizeDp)
            .editModeBorder(isEditMode, surfaceShape)
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.SETTINGS_SHORTCUT,
                    bounds = coordinates.boundsInRoot()
                )
            }
            .then(
                if (isEditMode) {
                    Modifier
                } else {
                    Modifier.combinedClickable(onClick = onSettingsTap)
                }
            ),
        shape = surfaceShape,
        color = Color.Transparent,
        contentColor = Color.Black,
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
                                onDrag = { change, dragAmount ->
                                    val currentSize = displaySizePx.value
                                    displayOffset.value = MapWidgetMath.boundedOffset(
                                        current = displayOffset.value,
                                        drag = dragAmount,
                                        maxX = screenWidthPx - currentSize,
                                        maxY = screenHeightPx - currentSize
                                    )
                                    change.consume()
                                },
                                onDragEnd = {
                                    onOffsetChange(displayOffset.value)
                                }
                            )
                        }
                )
            }

            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Color.Black,
                modifier = Modifier.size(iconSizeDp)
            )

            if (isEditMode) {
                SettingsResizeHandle(
                    onResize = { dragAmount ->
                        val requested = displaySizePx.value + ((dragAmount.x + dragAmount.y) / 2f)
                        val clamped = MapWidgetSizePolicy.clampSizePx(
                            widgetId = MapWidgetId.SETTINGS_SHORTCUT,
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
private fun SettingsResizeHandle(
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
                            change.consume()
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
                    .semantics { contentDescription = "Settings resize handle" }
            )
        }
    }
}
