package com.example.xcpro.map.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.ui1.UIVariometer
import com.example.ui1.VarioDialConfig
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.ui.toComposeOffset
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlin.math.roundToInt

@Composable
internal fun VariometerWidgetImpl(
    widgetManager: MapUIWidgetManager,
    variometerState: VariometerUiState,
    needleValue: Float,
    fastNeedleValue: Float,
    audioNeedleValue: Float,
    outerArcValue: Float? = null,
    displayValue: Float,
    displayLabel: String = String.format("%+.1f", displayValue),
    secondaryLabel: String? = null,
    secondaryLabelColor: Color? = null,
    dialConfig: VarioDialConfig = VarioDialConfig(),
    windDirectionScreenDeg: Float,
    windIsValid: Boolean,
    windSpeedLabel: String? = null,
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

    val latestVariometerState = rememberUpdatedState(variometerState)

    fun applyDragDelta(dragAmount: Offset) {
        val sizePx = displaySize.value
        val maxX = (screenWidthPx - sizePx).coerceAtLeast(0f)
        val maxY = (screenHeightPx - sizePx).coerceAtLeast(0f)
        val newOffset = MapWidgetMath.boundedOffset(displayOffset.value, dragAmount, maxX, maxY)
        if (newOffset != displayOffset.value) {
            displayOffset.value = newOffset
        }
    }

    val baseModifier = modifier
        .offset { displayOffset.value.toIntOffset() }
        .size(with(density) { displaySize.value.toDp() })
        .background(Color.Transparent, MapWidgetTheme.widgetCorner)
        .onGloballyPositioned { coordinates ->
            widgetManager.updateGestureRegion(
                target = MapOverlayGestureTarget.VARIOMETER,
                bounds = coordinates.boundsInRoot(),
                consumeGestures = true
            )
        }
        .editModeBorder(isEditMode, MapWidgetTheme.widgetCorner)

    val tapModifier = Modifier.pointerInput(isEditMode) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                onLongPress()
            }
        }
    }

    val dragModifier = Modifier.draggableWidget(
        enabled = isEditMode,
        key1 = screenWidthPx,
        key2 = screenHeightPx,
        key3 = displaySize.value,
        onDragStart = {
            isUserInteracting = true
        },
        onDrag = { dragAmount -> applyDragDelta(dragAmount) },
        onDragEnd = {
            isUserInteracting = false
            onOffsetChange(displayOffset.value)
            onEditFinished()
        }
    )

    Surface(
        modifier = baseModifier
            .then(tapModifier)
            .then(dragModifier),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            UIVariometer(
                needleValue = needleValue,
                fastNeedleValue = fastNeedleValue,
                averageNeedleValue = null,
                audioNeedleValue = audioNeedleValue,
                outerArcValue = outerArcValue,
                displayValue = displayValue,
                valueLabel = displayLabel,
                secondaryLabel = secondaryLabel,
                secondaryLabelColor = secondaryLabelColor,
                dialConfig = dialConfig,
                windDirectionScreenDeg = windDirectionScreenDeg,
                windIsValid = windIsValid,
                windSpeedLabel = windSpeedLabel,
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
                            change.consume()
                        },
                        onDragEnd = { onResizeEnd() },
                        onDragCancel = { onResizeEnd() }
                    )
                }
        )
    }
}
