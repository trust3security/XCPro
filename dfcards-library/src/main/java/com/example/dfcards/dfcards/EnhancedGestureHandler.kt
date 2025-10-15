package com.example.dfcards.dfcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import android.util.Log

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
    onDragStart: () -> Unit = {}, // 🔥 ADD THIS LINE
    enableSnapToGrid: Boolean = true,
    content: @Composable BoxScope.(Float, Float) -> Unit
) {
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var resizeCorner by remember { mutableStateOf(ResizeCorner.NONE) }

    // ✅ ADD LOGGING for card creation
    LaunchedEffect(cardState.id) {
        Log.d("FlightCards", "EnhancedGestureCard created for: ${cardState.id}")
        Log.d("FlightCards", "Card position: (${cardState.x}, ${cardState.y})")
        Log.d("FlightCards", "Card size: ${cardState.width}x${cardState.height}")
    }

    // Local state for smooth dragging
    var localX by remember(cardState.id) { mutableStateOf(cardState.x) }
    var localY by remember(cardState.id) { mutableStateOf(cardState.y) }
    var localWidth by remember(cardState.id) { mutableStateOf(cardState.width) }
    var localHeight by remember(cardState.id) { mutableStateOf(cardState.height) }

    // Sync with ViewModel when not dragging
    LaunchedEffect(cardState) {
        if (resizeCorner == ResizeCorner.NONE) {
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
                Log.d("FlightCards", "Card ${cardState.id} size changed: $size")
            }
            .let { modifier ->
                if (isEditMode) {
                    Log.d("FlightCards", "Card ${cardState.id} is in EDIT MODE - showing red border")
                    modifier.border(1.dp, Color.Red, RoundedCornerShape(6.dp))
                } else {
                    modifier
                }
            }
            // ✅ CRITICAL: Add logging to long press detection
            .pointerInput("long_press_${cardState.id}") {
                Log.d("FlightCards", "Setting up long press detection for card: ${cardState.id}")
                detectTapGestures(
                    onTap = { offset ->
                        Log.d("FlightCards", "TAP detected on card ${cardState.id} at offset: $offset")
                    },
                    onLongPress = { offset ->
                        Log.d("FlightCards", "🔥 LONG PRESS detected on card ${cardState.id} at offset: $offset")
                        Log.d("FlightCards", "🔥 Calling onLongPress() callback...")
                        onLongPress()
                        Log.d("FlightCards", "🔥 onLongPress() callback completed")
                    },
                    onDoubleTap = { offset ->
                        Log.d("FlightCards", "DOUBLE TAP detected on card ${cardState.id} at offset: $offset")
                        onDoubleClick()
                    }
                )
            }
            .let { modifier ->
                if (isEditMode) {
                    Log.d("FlightCards", "Card ${cardState.id} adding drag gestures (edit mode)")
                    modifier.pointerInput("drag_${cardState.id}") {
                        detectDragGestures(
                            onDragStart = { offset ->
                                Log.d("FlightCards", "Drag started on card ${cardState.id} at offset: $offset")
                                onDragStart() // 🔥 ADD THIS LINE HERE
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

                                Log.d("FlightCards", "Drag started on card ${cardState.id} - corner: $resizeCorner")
                            },
                            onDrag = { _, dragAmount ->
                                val sensitivity = 1.2f

                                when (resizeCorner) {
                                    ResizeCorner.TOP_LEFT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth - adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight - adjustedDrag.y).coerceAtLeast(60f)

                                        localX = localX + (localWidth - newWidth)
                                        localY = localY + (localHeight - newHeight)
                                        localWidth = newWidth
                                        localHeight = newHeight
                                    }
                                    ResizeCorner.TOP_RIGHT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth + adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight - adjustedDrag.y).coerceAtLeast(60f)

                                        localY = localY + (localHeight - newHeight)
                                        localWidth = newWidth
                                        localHeight = newHeight
                                    }
                                    ResizeCorner.BOTTOM_LEFT -> {
                                        val adjustedDrag = dragAmount * sensitivity
                                        val newWidth = (localWidth - adjustedDrag.x).coerceAtLeast(80f)
                                        val newHeight = (localHeight + adjustedDrag.y).coerceAtLeast(60f)

                                        localX = localX + (localWidth - newWidth)
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
                                        Log.d("FlightCards", "Moving card ${cardState.id} to ($localX, $localY)")
                                    }
                                }
                            },
                            onDragEnd = {
                                val finalState = cardState.copy(
                                    x = localX.coerceIn(0f, (containerSize.width - localWidth).coerceAtLeast(0f)),
                                    y = localY.coerceIn(0f, (containerSize.height - localHeight).coerceAtLeast(0f)),
                                    width = localWidth,
                                    height = localHeight
                                )
                                onCardUpdated(finalState)
                                resizeCorner = ResizeCorner.NONE
                                Log.d("FlightCards", "Drag ended on card ${cardState.id} - final state: (${finalState.x}, ${finalState.y}, ${finalState.width}x${finalState.height})")
                            }
                        )
                    }
                } else {
                    Log.d("FlightCards", "Card ${cardState.id} NOT in edit mode - no drag gestures")
                    modifier
                }
            }
    ) {
        content(localWidth, localHeight)

        // Resize indicators
        if (isEditMode) {
            Log.d("FlightCards", "Card ${cardState.id} showing resize indicators")
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