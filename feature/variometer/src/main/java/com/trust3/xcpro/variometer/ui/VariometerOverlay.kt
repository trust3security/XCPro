@file:Suppress("DEPRECATION")

package com.trust3.xcpro.variometer.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ui1.UIVariometer

object VariometerTestTags {
    const val Overlay = "variometerOverlay"
    const val ResizeHandle = "variometerResizeHandle"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VariometerOverlay(
    needleValue: Float,
    fastNeedleValue: Float,
    displayValue: Float,
    offset: Offset,
    sizePx: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    minSizePx: Float,
    maxSizePx: Float,
    isEditMode: Boolean,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    onLongPress: () -> Unit,
    onEditFinished: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { onBoundsChanged(Rect.Zero) }
    }

    val density = LocalDensity.current
    val displayOffset = remember(isEditMode) { mutableStateOf(offset) }
    val displaySize = remember(isEditMode) { mutableStateOf(sizePx) }

    LaunchedEffect(offset, sizePx, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = offset
            displaySize.value = sizePx
            Log.d("VariometerOverlay", "Sync from persisted offset=$offset size=$sizePx")
        }
    }

    var overlayModifier = modifier
        .semantics { testTag = VariometerTestTags.Overlay }
        .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
        .size(with(density) { displaySize.value.toDp() })
        .background(Color.Transparent, RoundedCornerShape(12.dp))
        .onGloballyPositioned { coordinates ->
            onBoundsChanged(coordinates.boundsInRoot())
        }

    if (!isEditMode) {
        overlayModifier = overlayModifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
    }

    overlayModifier = overlayModifier.then(
        if (isEditMode) {
            Modifier.pointerInput(screenWidthPx, screenHeightPx, displaySize.value) {
                Log.d("VARIO_GESTURE", "pointerInput active (isEditMode=$isEditMode, size=${displaySize.value})")
                detectDragGestures(
                    onDragStart = {
                        Log.d("VARIO_GESTURE", "Drag start offset=${displayOffset.value}")
                        Log.d("VariometerOverlay", "Drag start offset=${displayOffset.value}")
                    },
                    onDrag = { change, dragAmount ->
                        val maxX = (screenWidthPx - displaySize.value).coerceAtLeast(0f)
                        val maxY = (screenHeightPx - displaySize.value).coerceAtLeast(0f)
                        val newOffset = Offset(
                            x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, maxX),
                            y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, maxY)
                        )
                        if (newOffset != displayOffset.value) {
                            displayOffset.value = newOffset
                            Log.d("VariometerOverlay", "Dragging -> $newOffset")
                        }
                        change.consumePositionChange()
                    },
                    onDragEnd = {
                        Log.d("VariometerOverlay", "Drag end offset=${displayOffset.value}")
                        onOffsetChange(displayOffset.value)
                        onEditFinished()
                    },
                    onDragCancel = {
                        Log.d("VariometerOverlay", "Drag cancel; restoring $offset")
                        displayOffset.value = offset
                        onEditFinished()
                    }
                )
            }
        } else {
            Modifier
        }
    )

    if (isEditMode) {
        overlayModifier = overlayModifier.border(
            width = 2.dp,
            color = Color.Red,
            shape = RoundedCornerShape(12.dp)
        )
    }

    Box(
        modifier = overlayModifier
    ) {
        UIVariometer(
            needleValue = needleValue,
            fastNeedleValue = fastNeedleValue,
            displayValue = displayValue,
            modifier = Modifier.fillMaxSize()
        )

        if (isEditMode) {
            ResizeHandle(
                onResize = { dragAmount ->
                    displaySize.value = (displaySize.value + (dragAmount.x + dragAmount.y) / 2f)
                        .coerceIn(minSizePx, maxSizePx)
                    Log.d("VariometerOverlay", "Resizing live size=${displaySize.value}")
                },
                onResizeEnd = {
                    onSizeChange(displaySize.value)
                    onEditFinished()
                    Log.d("VariometerOverlay", "Resize committed size=${displaySize.value}")
                }
            )
        }
    }
}

@Composable
private fun ResizeHandle(
    onResize: (dragAmount: Offset) -> Unit,
    onResizeEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomEnd)
    ) {
        Box(
            modifier = Modifier
                .semantics { testTag = VariometerTestTags.ResizeHandle }
                .size(24.dp)
                .background(Color(0xB3FF1744), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { Log.d("VariometerOverlay", "Resize start") },
                            onDrag = { change, dragAmount ->
                                onResize(dragAmount)
                                change.consumePositionChange()
                        },
                        onDragEnd = {
                            Log.d("VariometerOverlay", "Resize end")
                            onResizeEnd()
                        }
                    )
                }
        )
    }
}
