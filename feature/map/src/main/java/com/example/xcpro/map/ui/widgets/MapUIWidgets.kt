package com.example.xcpro.map.ui.widgets

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui1.UIVariometer
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.ballast.BallastCommand
import com.example.xcpro.map.ballast.BallastPill
import com.example.xcpro.map.ballast.BallastUiState
import com.example.xcpro.variometer.layout.VariometerUiState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MapUIWidgets {

    /**
     * Variometer widget that mirrors the hamburger/flight-mode drag plumbing.
     * Relies on [MapUIWidgetManager] for gesture region updates so map gestures yield correctly.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun VariometerWidget(
        widgetManager: MapUIWidgetManager,
        variometerState: VariometerUiState,
        needleValue: Float,
        displayValue: Float,
        displayLabel: String = String.format("%+.1f", displayValue),
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
            Log.d("VARIO_GESTURE", "render skipped; state not initialized")
            return
        }
        Log.d("VARIO_GESTURE", "render editMode=$isEditMode offset=${variometerState.offset}")

        DisposableEffect(Unit) {
            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.VARIOMETER) }
        }

        val density = LocalDensity.current
        val displayOffset = remember { mutableStateOf(variometerState.offset) }
        val displaySize = remember { mutableStateOf(variometerState.sizePx) }
        var isUserInteracting by remember { mutableStateOf(false) }

        LaunchedEffect(variometerState.offset, variometerState.sizePx, isUserInteracting) {
            if (!isUserInteracting) {
                displayOffset.value = variometerState.offset
                displaySize.value = variometerState.sizePx
                Log.d(
                    "VARIO_GESTURE",
                    "sync from state offset=${variometerState.offset} size=${variometerState.sizePx}"
                )
            }
        }

        val latestVariometerState = rememberUpdatedState(variometerState)

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
                Log.d("VARIO_GESTURE", "dragging -> $newOffset (bounds=[0,$maxX]x[0,$maxY])")
            }
        }

        val baseModifier = modifier
            .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
            .size(with(density) { displaySize.value.toDp() })
            .background(Color.Transparent, RoundedCornerShape(12.dp))
            .onGloballyPositioned { coordinates ->
                widgetManager.updateGestureRegion(
                    target = MapOverlayGestureTarget.VARIOMETER,
                    bounds = coordinates.boundsInRoot(),
                    consumeGestures = true
                )
            }
            .editModeBorder(isEditMode, RoundedCornerShape(12.dp))

        val tapModifier = Modifier.pointerInput(isEditMode) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id)
                if (longPress != null) {
                    Log.d("VARIO_GESTURE", "longPress detected -> toggling edit mode")
                    onLongPress()
                }
            }
        }

        val dragModifier = if (isEditMode) {
            Modifier.pointerInput(screenWidthPx, screenHeightPx, displaySize.value) {
                detectDragGestures(
                    onDragStart = {
                        isUserInteracting = true
                        Log.d("VARIO_GESTURE", "dragStart offset=${displayOffset.value}")
                    },
                    onDrag = { change, dragAmount ->
                        applyDragDelta(dragAmount)
                        change.consumePositionChange()
                    },
                    onDragEnd = {
                        isUserInteracting = false
                        Log.d("VARIO_GESTURE", "dragEnd offset=${displayOffset.value}")
                        onOffsetChange(displayOffset.value)
                        onEditFinished()
                    },
                    onDragCancel = {
                        isUserInteracting = false
                        Log.d("VARIO_GESTURE", "dragCancel restoring ${latestVariometerState.value.offset}")
                        displayOffset.value = latestVariometerState.value.offset
                        onEditFinished()
                    }
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = baseModifier
                .then(tapModifier)
                .then(dragModifier)
        ) {
            UIVariometer(
                needleValue = needleValue,
                displayValue = displayValue,
                valueLabel = displayLabel,
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
                            Log.d("VARIO_GESTURE", "resize -> size=$newSize")
                        }
                    },
                    onResizeEnd = {
                        isUserInteracting = false
                        Log.d("VARIO_GESTURE", "resizeEnd size=${displaySize.value}")
                        onSizeChange(displaySize.value)
                        onEditFinished()
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun BallastWidget(

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

                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }

                .height(heightDp.dp)

                .wrapContentWidth(Alignment.Start)

                .widthIn(min = widthDp.dp)

                .editModeBorder(isEditMode, RoundedCornerShape(18.dp))

                .onGloballyPositioned { coordinates ->

                    widgetManager.updateGestureRegion(

                        target = MapOverlayGestureTarget.BALLAST,

                        bounds = coordinates.boundsInRoot()

                    )

                }

                .then(

                    if (isEditMode) {

                        Modifier.pointerInput(screenWidthPx, screenHeightPx) {

                            detectDragGestures(

                                onDrag = { change, dragAmount ->

                                    displayOffset.value = Offset(

                                        x = (displayOffset.value.x + dragAmount.x).coerceIn(

                                            0f,

                                            (screenWidthPx - widthPx).coerceAtLeast(0f)

                                        ),

                                        y = (displayOffset.value.y + dragAmount.y).coerceIn(

                                            0f,

                                            (screenHeightPx - heightPx).coerceAtLeast(0f)

                                        )

                                    )

                                    change.consumePositionChange()

                                },

                                onDragEnd = {

                                    widgetManager.saveWidgetPosition("ballast_pill", displayOffset.value)

                                    onOffsetChange(displayOffset.value)

                                }

                            )

                        }

                    } else {

                        Modifier

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

                                change.consumePositionChange()

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


    /**
     * Draggable hamburger button docked along the left edge.
     * Users can reposition it while edit mode is active.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SideHamburgerMenu(
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
                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
                .size(containerSizeDp.dp)
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
                .pointerInput(isEditMode, screenWidthPx, screenHeightPx) {
                    if (isEditMode) {
                        detectDragGestures(
                            onDragStart = {
                                Log.d("MapUIWidgetManager", "Hamburger drag started from ${displayOffset.value}")
                            },
                            onDrag = { change, dragAmount ->
                                displayOffset.value = Offset(
                                    x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, (screenWidthPx - sizePx).coerceAtLeast(0f)),
                                    y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, (screenHeightPx - sizePx).coerceAtLeast(0f))
                                )
                                change.consumePositionChange()
                            },
                            onDragEnd = {
                                Log.d("MapUIWidgetManager", "Hamburger drag ended at ${displayOffset.value}")
                                widgetManager.saveWidgetPosition("side_hamburger", displayOffset.value)
                                onOffsetChange(displayOffset.value)
                            }
                        )
                    }
                },
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

    /**
     * Flight mode selector that mirrors the hamburger widget's gesture plumbing so taps land reliably.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun FlightModeMenu(
        widgetManager: MapUIWidgetManager,
        currentMode: FlightMode,
        visibleModes: List<FlightMode>,
        onModeChange: (FlightMode) -> Unit,
        flightModeOffset: Offset,
        screenWidthPx: Float,
        screenHeightPx: Float,
        onOffsetChange: (Offset) -> Unit,
        isEditMode: Boolean,
        modifier: Modifier = Modifier,
        widthDp: Float = 96f,
        heightDp: Float = 36f
    ) {
        val tag = "FlightModeMenu"
        val density = LocalDensity.current
        val widthPx = with(density) { widthDp.dp.toPx() }
        val heightPx = with(density) { heightDp.dp.toPx() }

        DisposableEffect(Unit) {
            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.FLIGHT_MODE) }
        }

        val displayOffset = remember(isEditMode) { mutableStateOf(flightModeOffset) }
        LaunchedEffect(flightModeOffset, isEditMode) {
            if (!isEditMode) {
                displayOffset.value = flightModeOffset
            }
        }

        var isExpanded by remember { mutableStateOf(false) }
        LaunchedEffect(isEditMode) {
            if (isEditMode) {
                isExpanded = false
            }
        }

        Box(
            modifier = modifier
                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
                .width(widthDp.dp)
                .height(heightDp.dp)
                .editModeBorder(isEditMode, RoundedCornerShape(18.dp))
                .onGloballyPositioned { coordinates ->
                    widgetManager.updateGestureRegion(
                        target = MapOverlayGestureTarget.FLIGHT_MODE,
                        bounds = coordinates.boundsInRoot()
                    )
                }
                .then(
                    if (isEditMode) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { isExpanded = false })
                        }
                    } else {
                        Modifier.combinedClickable(
                            onClick = {
                                isExpanded = true
                                Log.d(tag, "Surface clicked; opening dropdown")
                            }
                        )
                    }
                )
                .pointerInput(isEditMode, screenWidthPx, screenHeightPx) {
                    if (isEditMode) {
                        detectDragGestures(
                            onDragStart = {
                                Log.d(tag, "Drag started from ${displayOffset.value}")
                            },
                            onDrag = { change, dragAmount ->
                                displayOffset.value = Offset(
                                    x = (displayOffset.value.x + dragAmount.x).coerceIn(
                                        0f,
                                        (screenWidthPx - widthPx).coerceAtLeast(0f)
                                    ),
                                    y = (displayOffset.value.y + dragAmount.y).coerceIn(
                                        0f,
                                        (screenHeightPx - heightPx).coerceAtLeast(0f)
                                    )
                                )
                                change.consumePositionChange()
                            },
                            onDragEnd = {
                                Log.d(tag, "Drag ended at ${displayOffset.value}")
                                widgetManager.saveWidgetPosition("flight_mode_menu", displayOffset.value)
                                onOffsetChange(displayOffset.value)
                            }
                        )
                    }
                }
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colorForMode(currentMode))
                    )
                    Text(
                        text = currentMode.displayName,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = {
                    isExpanded = false
                    Log.d(tag, "Dropdown dismissed")
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                visibleModes.forEach { mode ->
                    DropdownMenuItem(
                        onClick = {
                            onModeChange(mode)
                            isExpanded = false
                            Log.d(tag, "Mode selected ${mode.displayName}")
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            colorForMode(mode).copy(
                                                alpha = if (mode == currentMode) 1f else 0.4f
                                            )
                                        )
                                )
                                Text(
                                    text = mode.displayName,
                                    style = if (mode == currentMode) {
                                        MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        MaterialTheme.typography.labelMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
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
                            onDragStart = {
                                Log.d("VARIO_GESTURE", "resize start")
                                onResizeStart()
                            },
                            onDrag = { change, dragAmount ->
                                onResize(dragAmount)
                                change.consumePositionChange()
                            },
                            onDragEnd = {
                                Log.d("VARIO_GESTURE", "resize end")
                                onResizeEnd()
                            },
                            onDragCancel = {
                                Log.d("VARIO_GESTURE", "resize cancel")
                                onResizeEnd()
                            }
                        )
                    }
            )
        }
    }

    private fun colorForMode(mode: FlightMode): Color {
        return when (mode) {
            FlightMode.CRUISE -> Color(0xFF2196F3)
            FlightMode.THERMAL -> Color(0xFF9C27B0)
            FlightMode.FINAL_GLIDE -> Color(0xFFF44336)
            FlightMode.HAWK -> Color(0xFF00BCD4)
        }
    }

}

private fun Modifier.editModeBorder(
    isEditMode: Boolean,
    shape: androidx.compose.ui.graphics.Shape = RectangleShape
): Modifier {
    return if (isEditMode) {
        border(
            width = 2.dp,
            color = Color.Red,
            shape = shape
        )
    } else {
        this
    }
}









