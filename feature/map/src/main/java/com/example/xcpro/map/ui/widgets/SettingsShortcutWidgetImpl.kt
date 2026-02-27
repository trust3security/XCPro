package com.example.xcpro.map.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.MapOverlayGestureTarget

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsShortcutWidgetImpl(
    widgetManager: MapUIWidgetManager,
    settingsOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onSettingsTap: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Float = 56f,
    shape: Shape = CircleShape
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val iconSizeDp = sizeDp * 0.46f

    DisposableEffect(Unit) {
        onDispose {
            widgetManager.clearGestureRegion(MapOverlayGestureTarget.SETTINGS_SHORTCUT)
        }
    }

    val displayOffset = remember(isEditMode) { mutableStateOf(settingsOffset) }
    LaunchedEffect(settingsOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = settingsOffset
        }
    }

    Surface(
        modifier = modifier
            .offset { displayOffset.value.toIntOffset() }
            .size(sizeDp.dp)
            .clip(shape)
            .editModeBorder(isEditMode, shape)
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.SETTINGS_SHORTCUT,
                    bounds = coordinates.boundsInRoot()
                )
            }
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = {})
                    }
                } else {
                    Modifier.combinedClickable(onClick = onSettingsTap)
                }
            )
            .draggableWidget(
                enabled = isEditMode,
                key1 = screenWidthPx,
                key2 = screenHeightPx,
                onDrag = { dragAmount ->
                    displayOffset.value = MapWidgetMath.boundedOffset(
                        current = displayOffset.value,
                        drag = dragAmount,
                        maxX = screenWidthPx - sizePx,
                        maxY = screenHeightPx - sizePx
                    )
                },
                onDragEnd = {
                    onOffsetChange(displayOffset.value)
                }
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = Color.Black,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Color.Black,
                modifier = Modifier.size(iconSizeDp.dp)
            )
        }
    }
}
