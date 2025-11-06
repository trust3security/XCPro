package com.example.xcpro.map

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.xcpro.FlightMode
import com.example.ui1.UIVariometer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Centralized UI Widget Management for MapScreen
 * Handles draggable components, edit mode, and widget positioning/sizing
 */
class MapUIWidgetManager(
    internal val mapState: MapScreenState,
    private val sharedPrefs: SharedPreferences
) {
    companion object {
        private const val TAG = "MapUIWidgetManager"
    }

    private val regionRegistry = linkedMapOf<MapOverlayGestureTarget, MapGestureRegion>()
    private val _gestureRegions = MutableStateFlow<List<MapGestureRegion>>(emptyList())
    val gestureRegions: StateFlow<List<MapGestureRegion>> = _gestureRegions.asStateFlow()

    /**
     * Load saved widget positions and sizes from SharedPreferences
     */
    fun loadWidgetPositions(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: androidx.compose.ui.unit.Density
    ): WidgetPositions {
        val legacyVariometerX = sharedPrefs.getFloat("variometer_x", 20f)
        val legacyVariometerY = sharedPrefs.getFloat("variometer_y", screenHeightPx - 400f)
        val legacyVariometerSize = sharedPrefs.getFloat("variometer_size", with(density) { 150.dp.toPx() })

        val levoX = sharedPrefs.getFloat("uilevo_x", legacyVariometerX)
        val levoY = sharedPrefs.getFloat("uilevo_y", legacyVariometerY)
        val levoSize = sharedPrefs.getFloat("uilevo_size", legacyVariometerSize)
        val defaultHamburgerX = 16f
        val defaultHamburgerY = (screenHeightPx / 2f) - with(density) { 32.dp.toPx() }
        val hamburgerX = sharedPrefs.getFloat("side_hamburger_x", defaultHamburgerX)
        val hamburgerY = sharedPrefs.getFloat("side_hamburger_y", defaultHamburgerY.coerceAtLeast(0f))
        val defaultFlightModeX = 16f
        val defaultFlightModeY = with(density) { 80.dp.toPx() }
        val flightModeX = sharedPrefs.getFloat("flight_mode_menu_x", defaultFlightModeX)
        val flightModeY = sharedPrefs.getFloat("flight_mode_menu_y", defaultFlightModeY)

        return WidgetPositions(
            variometerOffset = Offset(levoX, levoY),
            variometerSizePx = levoSize,
            sideHamburgerOffset = Offset(hamburgerX, hamburgerY),
            flightModeOffset = Offset(flightModeX, flightModeY)
        )
    }

    /**
     * Save widget position to SharedPreferences
     */
    fun saveWidgetPosition(key: String, offset: Offset) {
        with(sharedPrefs.edit()) {
            putFloat("${key}_x", offset.x)
            putFloat("${key}_y", offset.y)
            apply()
        }
        Log.d(TAG, "$key position saved: x=${offset.x}, y=${offset.y}")
    }

    /**
     * Save widget size to SharedPreferences
     */
    fun saveWidgetSize(key: String, size: Float) {
        with(sharedPrefs.edit()) {
            putFloat("${key}_size", size)
            apply()
        }
        Log.d(TAG, "$key size saved: $size")
    }

    /**
     * Register or update a gesture region so the map can short-circuit pointer handling.
     *
     * AI-NOTE: Gesture pass-through relies on screen-space bounds; we update them whenever the
     * overlay recomposes to keep the map gesture layer in sync.
     */
    fun updateGestureRegion(
        target: MapOverlayGestureTarget,
        bounds: Rect,
        consumeGestures: Boolean = true
    ) {
        regionRegistry[target] = MapGestureRegion(target, bounds, consumeGestures)
        refreshGestureRegions()
        Log.d(TAG, "Updated gesture region for $target: $bounds (consume=$consumeGestures)")
    }

    /**
     * Clear a gesture region when its overlay leaves composition.
     */
    fun clearGestureRegion(target: MapOverlayGestureTarget) {
        regionRegistry.remove(target)
        refreshGestureRegions()
        Log.d(TAG, "Cleared gesture region for $target")
    }

    private fun refreshGestureRegions() {
        val sorted = regionRegistry.values
            .sortedWith(
                compareBy<MapGestureRegion> { priorityFor(it.target) }
                    .thenBy { it.bounds.width * it.bounds.height }
            )
        _gestureRegions.value = sorted
    }

    private fun priorityFor(target: MapOverlayGestureTarget): Int {
        return when (target) {
            MapOverlayGestureTarget.FLIGHT_MODE -> 0
            MapOverlayGestureTarget.SIDE_HAMBURGER -> 1
            MapOverlayGestureTarget.VARIOMETER -> 1
        }
    }
}

/**
 * Data class for widget positions and sizes
 */
data class WidgetPositions(
    val variometerOffset: Offset,
    val variometerSizePx: Float,
    val sideHamburgerOffset: Offset,
    val flightModeOffset: Offset
)

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

/**
 * Compose components for UI widgets
 */
object MapUIWidgets {

    /**
     * UILevo: variometer widget that mirrors the hamburger's gesture handling.
     */
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun UILevo(
        variometerNeedleValue: Float,
        variometerDisplayValue: Float,
        variometerOffset: Offset,
        variometerSizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        minSizePx: Float,
        maxSizePx: Float,
        widgetManager: MapUIWidgetManager,
        density: androidx.compose.ui.unit.Density,
        onOffsetChange: (Offset) -> Unit,
        onSizeChange: (Float) -> Unit,
        modifier: Modifier = Modifier
    ) {
        DisposableEffect(Unit) {
            onDispose { widgetManager.clearGestureRegion(MapOverlayGestureTarget.VARIOMETER) }
        }

        val isEditMode = widgetManager.mapState.isUIEditMode
        val displayOffset = remember(isEditMode) { mutableStateOf(variometerOffset) }
        val displaySize = remember(isEditMode) { mutableStateOf(variometerSizePx) }

        LaunchedEffect(variometerOffset, variometerSizePx, isEditMode) {
            if (!isEditMode) {
                displayOffset.value = variometerOffset
                displaySize.value = variometerSizePx
                Log.d("MapUIWidgetManager", "UILevo sync from persisted offset=$variometerOffset size=$variometerSizePx")
            }
        }

        Box(
            modifier = modifier
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
                .then(
                    if (isEditMode) {
                        Modifier.pointerInput(screenWidthPx, screenHeightPx, displaySize.value) {
                            detectDragGestures(
                                onDragStart = {
                                    Log.d("MapUIWidgetManager", "UILevo drag start offset=${displayOffset.value}")
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
                                        Log.d("MapUIWidgetManager", "UILevo dragging -> $newOffset")
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    Log.d("MapUIWidgetManager", "UILevo drag end offset=${displayOffset.value}")
                                    widgetManager.saveWidgetPosition("uilevo", displayOffset.value)
                                    onOffsetChange(displayOffset.value)
                                },
                                onDragCancel = {
                                    Log.d("MapUIWidgetManager", "UILevo drag cancelled; restoring ${variometerOffset}")
                                    displayOffset.value = variometerOffset
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .editModeBorder(isEditMode, RoundedCornerShape(12.dp))
        ) {
            UIVariometer(
                needleValue = variometerNeedleValue,
                displayValue = variometerDisplayValue,
                modifier = Modifier.fillMaxSize()
            )

            if (isEditMode) {
                ResizeHandle(
                    onResize = { dragAmount ->
                        displaySize.value = (displaySize.value + (dragAmount.x + dragAmount.y) / 2f)
                            .coerceIn(minSizePx, maxSizePx)
                        Log.d("MapUIWidgetManager", "UILevo resizing live size=${displaySize.value} px")
                    },
                    onResizeEnd = {
                        widgetManager.saveWidgetSize("uilevo", displaySize.value)
                        onSizeChange(displaySize.value)
                        Log.d("MapUIWidgetManager", "UILevo resize committed size=${displaySize.value} px")
                    }
                )
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
        modifier: Modifier = Modifier,
        sizeDp: Float = 90f
    ) {
        val isEditMode = widgetManager.mapState.isUIEditMode
        val density = LocalDensity.current
        val sizePx = with(density) { sizeDp.dp.toPx() }
        val iconSizeDp = sizeDp * 0.6f

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
                .size(sizeDp.dp)
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
                                change.consume()
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
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open navigation drawer",
                    modifier = Modifier.size(iconSizeDp.dp),
                    tint = Color(0xFF7A7A7A)
                )
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
        modifier: Modifier = Modifier,
        widthDp: Float = 150f,
        heightDp: Float = 56f
    ) {
        val tag = "FlightModeMenu"
        val isEditMode = widgetManager.mapState.isUIEditMode
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
                .editModeBorder(isEditMode, RoundedCornerShape(16.dp))
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
                                change.consume()
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
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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
                }
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

    private fun colorForMode(mode: FlightMode): Color {
        return when (mode) {
            FlightMode.CRUISE -> Color(0xFF2196F3)
            FlightMode.THERMAL -> Color(0xFF9C27B0)
            FlightMode.FINAL_GLIDE -> Color(0xFFF44336)
            FlightMode.HAWK -> Color(0xFF00BCD4)
        }
    }

    /**
     * Resize handle for widgets in edit mode
     */
    @Composable
    private fun ResizeHandle(
        onResize: (dragAmount: Offset) -> Unit,
        onResizeEnd: () -> Unit = {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp) // Reasonable size
                    .background(Color.Red.copy(alpha = 0.7f), CircleShape) // Red and semi-transparent
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { Log.d("MapUIWidgetManager", "Resize started") },
                            onDrag = { change, dragAmount ->
                                onResize(dragAmount)
                                change.consume()
                            },
                            onDragEnd = {
                                Log.d("MapUIWidgetManager", "Resize ended")
                                onResizeEnd()
                            }
                        )
                    }
            )
        }
    }
}
