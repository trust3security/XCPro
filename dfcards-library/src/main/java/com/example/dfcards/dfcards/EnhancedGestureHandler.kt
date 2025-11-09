package com.example.dfcards.dfcards

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

enum class ResizeCorner {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Composable
fun EnhancedGestureCard(
    cardState: CardState,
    containerSize: IntSize,
    isEditMode: Boolean,
    allCards: List<CardState>,
    onCardUpdated: (CardState) -> Unit,
    onCardSelected: (String) -> Unit,
    onLongPress: () -> Unit,
    onDoubleClick: () -> Unit,
    onDragStart: () -> Unit = {},
    onDragFinished: () -> Unit = {},
    enableSnapToGrid: Boolean = true,
    content: @Composable BoxScope.(Float, Float) -> Unit
) {
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var resizeCorner by remember { mutableStateOf(ResizeCorner.NONE) }
    var isUserInteracting by remember { mutableStateOf(false) }

    var localX by remember(cardState.id) { mutableStateOf(cardState.x) }
    var localY by remember(cardState.id) { mutableStateOf(cardState.y) }
    var localWidth by remember(cardState.id) { mutableStateOf(cardState.width) }
    var localHeight by remember(cardState.id) { mutableStateOf(cardState.height) }

    LaunchedEffect(cardState.id, isEditMode) {
        android.util.Log.d("CARD_GESTURE", "render ${cardState.id} editMode=$isEditMode")
    }

    LaunchedEffect(cardState, isUserInteracting) {
        if (!isUserInteracting) {
            localX = cardState.x
            localY = cardState.y
            localWidth = cardState.width
            localHeight = cardState.height
        }
    }

    Box(
        modifier = Modifier
            .size(
                width = with(density) { localWidth.toDp() },
                height = with(density) { localHeight.toDp() }
            )
            .offset(
                x = with(density) { localX.toDp() },
                y = with(density) { localY.toDp() }
            )
            .onSizeChanged { size ->
                cardSize = size
            }
            .let { modifier ->
                if (isEditMode) {
                    modifier.border(1.dp, Color.Red, RoundedCornerShape(6.dp))
                } else {
                    modifier
                }
            }
            .pointerInput("long_press_${cardState.id}") {
                detectTapGestures(
                    onTap = { onCardSelected(cardState.id) },
                    onLongPress = { onLongPress() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .let { modifier ->
                if (isEditMode) {
                    modifier.pointerInput("drag_${cardState.id}", containerSize) {
                        Log.d(
                            "CARD_GESTURE",
                            "pointerInput active for ${cardState.id} (container=${containerSize.width}x${containerSize.height})"
                        )
                        detectDragGestures(
                            onDragStart = { offset ->
                                isUserInteracting = true
                                Log.d(
                                    "CARD_GESTURE",
                                    "Drag start for ${cardState.id} at $offset (cardSize=${cardSize.width}x${cardSize.height}, container=${containerSize.width}x${containerSize.height})"
                                )
                                onDragStart()
                                val cornerSize = 80f
                                val edgeSize = 60f
                                val width = cardSize.width.toFloat()
                                val height = cardSize.height.toFloat()

                                resizeCorner = when {
                                    offset.x <= cornerSize && offset.y <= cornerSize -> ResizeCorner.TOP_LEFT
                                    offset.x >= width - cornerSize && offset.y <= cornerSize -> ResizeCorner.TOP_RIGHT
                                    offset.x <= cornerSize && offset.y >= height - cornerSize -> ResizeCorner.BOTTOM_LEFT
                                    offset.x >= width - cornerSize && offset.y >= height - cornerSize -> ResizeCorner.BOTTOM_RIGHT
                                    offset.y <= edgeSize -> ResizeCorner.TOP_LEFT
                                    offset.x <= edgeSize -> ResizeCorner.TOP_LEFT
                                    else -> ResizeCorner.NONE
                                }
                            },
                            onDrag = { _, dragAmount ->
                                val sensitivity = 1.2f
                                when (resizeCorner) {
                                    ResizeCorner.TOP_LEFT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth - adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight - adjustedDrag.y).coerceAtLeast(60f)

                                        localX += localWidth - newWidth
                                        localY += localHeight - newHeight
                                        localWidth = newWidth
                                        localHeight = newHeight
                                    }
                                    ResizeCorner.TOP_RIGHT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth + adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight - adjustedDrag.y).coerceAtLeast(60f)

                                        localY += localHeight - newHeight
                                        localWidth = newWidth
                                        localHeight = newHeight
                                    }
                                    ResizeCorner.BOTTOM_LEFT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth - adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight + adjustedDrag.y).coerceAtLeast(60f)

                                        localX += localWidth - newWidth
                                        localWidth = newWidth
                                        localHeight = newHeight
                                    }
                                    ResizeCorner.BOTTOM_RIGHT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        localWidth = (localWidth + adjustedDrag.x).coerceAtLeast(80f)
                                        localHeight = (localHeight + adjustedDrag.y).coerceAtLeast(60f)
                                    }
                                    ResizeCorner.NONE -> {
                                        localX = (localX + dragAmount.x).coerceAtLeast(0f)
                                        localY = (localY + dragAmount.y).coerceAtLeast(0f)
                                    }
                                }
                            },
                            onDragEnd = {
                                isUserInteracting = false
                                val boundedX = localX.coerceIn(0f, (containerSize.width - localWidth).coerceAtLeast(0f))
                                val boundedY = localY.coerceIn(0f, (containerSize.height - localHeight).coerceAtLeast(0f))
                                val finalState = cardState.copy(
                                    x = boundedX,
                                    y = boundedY,
                                    width = localWidth,
                                    height = localHeight
                                )
                                onCardUpdated(finalState)
                                resizeCorner = ResizeCorner.NONE
                                onDragFinished()
                            },
                            onDragCancel = {
                                isUserInteracting = false
                                val resetState = cardState
                                localX = resetState.x
                                localY = resetState.y
                                localWidth = resetState.width
                                localHeight = resetState.height
                                resizeCorner = ResizeCorner.NONE
                                onDragFinished()
                            }
                        )
                    }
                } else {
                    modifier
                }
            }
    ) {
        content(localWidth, localHeight)

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Blue, CircleShape)
                    .align(Alignment.TopStart)
                    .offset((-4).dp, (-4).dp)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Blue, CircleShape)
                    .align(Alignment.TopEnd)
                    .offset(4.dp, (-4).dp)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Blue, CircleShape)
                    .align(Alignment.BottomStart)
                    .offset((-4).dp, 4.dp)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Blue, CircleShape)
                    .align(Alignment.BottomEnd)
                    .offset(4.dp, 4.dp)
            )
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(Color.Blue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .align(Alignment.TopCenter)
                    .offset(y = (-1.5).dp)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(Color.Blue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterStart)
                    .offset(x = (-1.5).dp)
            )
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(3.dp)
                    .background(Color.Blue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .align(Alignment.BottomCenter)
                    .offset(y = 1.5.dp)
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(Color.Blue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterEnd)
                    .offset(x = 1.5.dp)
            )
        }
    }
}

