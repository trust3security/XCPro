package com.trust3.xcpro.map.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.trust3.xcpro.map.MapOverlayGestureTarget
import com.trust3.xcpro.map.ballast.BallastCommand
import com.trust3.xcpro.map.ballast.BallastPill
import com.trust3.xcpro.map.ballast.BallastUiState
import kotlin.math.roundToInt

@Composable
internal fun BallastWidgetImpl(
    widgetManager: MapUIWidgetManager,
    ballastState: BallastUiState,
    onCommand: (BallastCommand) -> Unit,
    ballastOffset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onOffsetChange: (Offset) -> Unit,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    widthDp: Float = 40f,
    heightDp: Float = 120f
) {
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.dp.toPx() }
    val heightPx = with(density) { heightDp.dp.toPx() }
    val swipeThresholdPx = with(density) { 32.dp.toPx() }

    val displayOffset = remember(isEditMode) { mutableStateOf(ballastOffset) }
    var showSwipeHint by rememberSaveable { mutableStateOf(true) }
    var dragAccumulation by remember { mutableStateOf(0f) }
    val latestBallastState by rememberUpdatedState(ballastState)

    LaunchedEffect(ballastOffset, isEditMode) {
        if (!isEditMode) {
            displayOffset.value = ballastOffset
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            widgetManager.clearGestureRegion(MapOverlayGestureTarget.BALLAST)
        }
    }

    Box(
        modifier = modifier
            .offset { displayOffset.value.toIntOffset() }
            .height(heightDp.dp)
            .wrapContentWidth(Alignment.Start)
            .widthIn(min = widthDp.dp)
            .editModeBorder(isEditMode, MapWidgetTheme.pillCorner)
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.BALLAST,
                    bounds = coordinates.boundsInRoot()
                )
            }
            .draggableWidget(
                enabled = isEditMode,
                key1 = screenWidthPx,
                key2 = screenHeightPx,
                onDrag = { dragAmount ->
                    displayOffset.value = MapWidgetMath.boundedOffset(
                        displayOffset.value,
                        dragAmount,
                        (screenWidthPx - widthPx),
                        (screenHeightPx - heightPx)
                    )
                },
                onDragEnd = {
                    onOffsetChange(displayOffset.value)
                }
            )
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectTapGestures(onTap = {
                        if (latestBallastState.isAnimating) {
                            onCommand(BallastCommand.Cancel)
                        } else {
                            showSwipeHint = false
                        }
                    })
                }
            }
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragAccumulation = 0f
                            showSwipeHint = false
                        },
                        onVerticalDrag = { change, dragAmount ->
                            dragAccumulation += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                dragAccumulation <= -swipeThresholdPx -> onCommand(BallastCommand.StartFill)
                                dragAccumulation >= swipeThresholdPx -> onCommand(BallastCommand.StartDrain)
                            }
                            dragAccumulation = 0f
                        },
                        onDragCancel = {
                            dragAccumulation = 0f
                        }
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BallastPill(
                state = ballastState,
                onCommand = onCommand,
                modifier = Modifier
                    .width(widthDp.dp)
                    .height(heightDp.dp)
            )

            AnimatedVisibility(
                visible = showSwipeHint && !isEditMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(end = 6.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Swipe Up Fill",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false
                    )

                    Text(
                        text = "Swipe Down Drain",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
