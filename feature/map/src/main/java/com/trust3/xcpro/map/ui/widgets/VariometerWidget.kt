@file:Suppress("DEPRECATION")

package com.trust3.xcpro.map.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui1.UIVariometer
import com.example.ui1.VarioDialConfig
import com.trust3.xcpro.map.MapOverlayGestureTarget
import com.trust3.xcpro.map.ui.toComposeOffset
import com.trust3.xcpro.map.ui.widgets.common.editModeBorder
import com.trust3.xcpro.map.ui.widgets.common.updateWidgetGestureRegion
import com.trust3.xcpro.variometer.layout.VariometerUiState
import kotlin.math.roundToInt

/**
 * Variometer widget that mirrors the hamburger/flight-mode drag plumbing.
 * Relies on [MapUIWidgetManager] for gesture region updates so map gestures yield correctly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VariometerWidgetContent(
    widgetManager: MapUIWidgetManager,
    variometerState: VariometerUiState,
    needleValue: Float,
    fastNeedleValue: Float,
    displayValue: Float,
    displayLabel: String = String.format("%+.1f", displayValue),
    secondaryLabel: String? = null,
    secondaryLabelColor: Color? = null,
    dialConfig: VarioDialConfig = VarioDialConfig(),
    screenWidthPx: Float,
    screenHeightPx: Float,
    minSizePx: Float,
    maxSizePx: Float,
    isEditMode: Boolean,
    onOffsetChange: (Offset) -> Unit,
    onSizeChange: (Float) -> Unit,
    onLongPress: () -> Unit,
    onEditFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!variometerState.isInitialized) {
        return
    }

    DisposableEffect(Unit) {
        onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.VARIOMETER) }
    }

    val density = LocalDensity.current
    val displayOffset = remember { mutableStateOf(variometerState.offset.toComposeOffset()) }
    val displaySize = remember { mutableStateOf(variometerState.sizePx) }
    var isUserInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(variometerState.offset, variometerState.sizePx, isUserInteracting) {
        if (!isUserInteracting) {
            displayOffset.value = variometerState.offset.toComposeOffset()
            displaySize.value = variometerState.sizePx
        }
    }

    fun applyDragDelta(dragAmount: Offset) {
        val sizePx = displaySize.value
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        val newOffset = Offset(
            x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, maxX),
            y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, maxY)
        )
        if (newOffset != displayOffset.value) {
            displayOffset.value = newOffset
        }
    }

    val baseModifier = modifier
        .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
        .size(with(density) { displaySize.value.toDp() })
        .background(Color.Transparent, RoundedCornerShape(12.dp))
        .onGloballyPositioned { coordinates ->
            widgetManager.updateWidgetGestureRegion(
                target = MapOverlayGestureTarget.VARIOMETER,
                bounds = coordinates,
                consumeGestures = true
            )
        }
        .editModeBorder(isEditMode, RoundedCornerShape(12.dp))

    val tapModifier = Modifier.pointerInput(isEditMode) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                onLongPress()
            }
        }
    }

    val dragModifier = Modifier.pointerInput(screenWidthPx, screenHeightPx, isEditMode) {
        if (isEditMode) {
            detectDragGestures(
                onDragStart = { isUserInteracting = true },
                onDrag = { change, dragAmount ->
                    applyDragDelta(dragAmount)
                    change.consumePositionChange()
                },
                onDragEnd = {
                    isUserInteracting = false
                    onOffsetChange(displayOffset.value)
                    onEditFinished()
                },
                onDragCancel = { isUserInteracting = false }
            )
        }
    }

    Box(
        modifier = baseModifier
            .then(tapModifier)
            .then(dragModifier)
    ) {
        UIVariometer(
            needleValue = needleValue,
            fastNeedleValue = fastNeedleValue,
            displayValue = displayValue,
            valueLabel = displayLabel,
            secondaryLabel = secondaryLabel,
            secondaryLabelColor = secondaryLabelColor,
            dialConfig = dialConfig,
            modifier = Modifier.fillMaxSize()
        )

        if (isEditMode) {
            VariometerResizeHandle(
                onResizeStart = { isUserInteracting = true },
                onResize = { dragAmount ->
                    val newSize = (displaySize.value + (dragAmount.x + dragAmount.y) / 2f)
                        .coerceIn(minSizePx, maxSizePx)
                    if (newSize != displaySize.value) {
                        displaySize.value = newSize
                    }
                },
                onResizeEnd = {
                    isUserInteracting = false
                    onSizeChange(displaySize.value)
                    onEditFinished()
                }
            )
        }
    }
}

@Composable
private fun VariometerResizeHandle(
    onResizeStart: () -> Unit,
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
                .size(24.dp)
                .background(Color(0xB3FF1744), RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onResizeStart() },
                        onDrag = { change, dragAmount ->
                            onResize(dragAmount)
                            change.consumePositionChange()
                        },
                        onDragEnd = { onResizeEnd() },
                        onDragCancel = { onResizeEnd() }
                    )
                }
        )
    }
}
