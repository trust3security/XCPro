package com.example.xcpro.map.ui.widgets

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import com.example.xcpro.map.MapOverlayGestureTarget
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SideHamburgerMenuImpl(
    widgetManager: MapUIWidgetManager,
    hamburgerOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onHamburgerTap: () -> Unit,
    onHamburgerLongPress: () -> Unit,
    onOffsetChange: (Offset) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Float = 90f
) {
    val density = LocalDensity.current
    val iconSizeDp = sizeDp * 0.6f
    val containerSizeDp = if (isEditMode) sizeDp * 0.8f else sizeDp
    val sizePx = with(density) { containerSizeDp.dp.toPx() }

    DisposableEffect(Unit) {
        onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.SIDE_HAMBURGER) }
    }

    val displayOffset = remember(isEditMode) {
        mutableStateOf(hamburgerOffset)
    }

    LaunchedEffect(hamburgerOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = hamburgerOffset
        }
    }

    Surface(
        modifier = modifier
            .offset { displayOffset.value.toIntOffset() }
            .size(containerSizeDp.dp)
            .editModeBorder(isEditMode, androidx.compose.ui.graphics.RectangleShape)
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.SIDE_HAMBURGER,
                    bounds = coordinates.boundsInRoot()
                )
            }
            .then(
                if (isEditMode) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onHamburgerLongPress() }
                        )
                    }
                } else {
                    Modifier.combinedClickable(
                        onClick = onHamburgerTap,
                        onLongClick = onHamburgerLongPress
                    )
                }
            )
            .draggableWidget(
                enabled = isEditMode,
                key1 = screenWidthPx,
                key2 = screenHeightPx,
                onDragStart = {
                    Log.d("MapUIWidgetManager", "Hamburger drag started from ${displayOffset.value}")
                },
                onDrag = { dragAmount ->
                    displayOffset.value = MapWidgetMath.boundedOffset(
                        displayOffset.value,
                        dragAmount,
                        (screenWidthPx - sizePx),
                        (screenHeightPx - sizePx)
                    )
                },
                onDragEnd = {
                    Log.d("MapUIWidgetManager", "Hamburger drag ended at ${displayOffset.value}")
                    widgetManager.saveWidgetPosition("side_hamburger", displayOffset.value)
                    onOffsetChange(displayOffset.value)
                }
            ),
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val lineWidth = (iconSizeDp * 0.72f).dp
            val lineHeight = (iconSizeDp * 0.08f).dp
            val columnHeight = iconSizeDp.dp + lineHeight * 2
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
        }
    }
}
