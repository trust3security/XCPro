package com.example.xcpro.map

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DrawerState
import com.example.ui1.UIVariometer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    /**
     * Load saved widget positions and sizes from SharedPreferences
     */
    fun loadWidgetPositions(
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: androidx.compose.ui.unit.Density
    ): WidgetPositions {
        val variometerX = sharedPrefs.getFloat("variometer_x", 20f)
        val variometerY = sharedPrefs.getFloat("variometer_y", screenHeightPx - 400f)
        val variometerSize = sharedPrefs.getFloat("variometer_size", with(density) { 150.dp.toPx() })

        val hamburgerX = sharedPrefs.getFloat("hamburger_icon_x", 20f)
        val hamburgerY = sharedPrefs.getFloat("hamburger_icon_y", 20f)

        return WidgetPositions(
            variometerOffset = Offset(variometerX, variometerY),
            variometerSizePx = variometerSize,
            hamburgerOffset = Offset(hamburgerX, hamburgerY)
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
     * Toggle UI edit mode
     */
    fun toggleEditMode() {
        mapState.isUIEditMode = !mapState.isUIEditMode
        Log.d(TAG, "Edit mode toggled: ${mapState.isUIEditMode}")
    }

    /**
     * Get edit mode border modifier
     */
    fun getEditModeBorder(): Modifier {
        return Modifier.border(
            width = if (mapState.isUIEditMode) 2.dp else 0.dp,
            color = if (mapState.isUIEditMode) Color.Red else Color.Transparent
        )
    }

    /**
     * Get edit mode border modifier with custom shape
     */
    fun getEditModeBorder(shape: androidx.compose.ui.graphics.Shape): Modifier {
        return Modifier.border(
            width = if (mapState.isUIEditMode) 2.dp else 0.dp,
            color = if (mapState.isUIEditMode) Color.Red else Color.Transparent,
            shape = shape
        )
    }
}

/**
 * Data class for widget positions and sizes
 */
data class WidgetPositions(
    val variometerOffset: Offset,
    val variometerSizePx: Float,
    val hamburgerOffset: Offset
)

/**
 * Compose components for UI widgets
 */
object MapUIWidgets {

    /**
     * Draggable Variometer widget with resize handle
     */
    @Composable
    fun DraggableVariometer(
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
        // Use independent position state during edit mode
        val displayOffset = remember(widgetManager.mapState.isUIEditMode) {
            mutableStateOf(variometerOffset)
        }

        // Use independent size state during edit mode
        val displaySize = remember(widgetManager.mapState.isUIEditMode) {
            mutableStateOf(variometerSizePx)
        }

        // Sync with parameters when NOT in edit mode
        LaunchedEffect(variometerOffset, variometerSizePx, widgetManager.mapState.isUIEditMode) {
            if (!widgetManager.mapState.isUIEditMode) {
                displayOffset.value = variometerOffset
                displaySize.value = variometerSizePx
            }
        }

        Box(
            modifier = modifier
                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
                .size(with(density) { displaySize.value.toDp() })
                .background(Color.Transparent, RoundedCornerShape(12.dp))
                .then(
                    if (widgetManager.mapState.isUIEditMode) {
                        Modifier.pointerInput(screenWidthPx, screenHeightPx) {
                            detectDragGestures(
                                onDragStart = {
                                    Log.d("MapUIWidgetManager", "dYZ_ Variometer drag STARTED from ${displayOffset.value}")
                                },
                                onDrag = { change, dragAmount ->
                                    displayOffset.value = Offset(
                                        x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, screenWidthPx),
                                        y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, screenHeightPx)
                                    )
                                    change.consume()
                                },
                                onDragEnd = {
                                    Log.d("MapUIWidgetManager", "dY?? Variometer drag ENDED at: ${displayOffset.value}")
                                    widgetManager.saveWidgetPosition("variometer", displayOffset.value)
                                    onOffsetChange(displayOffset.value) // Update parent state once at end
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .then(widgetManager.getEditModeBorder())
        ) {
            UIVariometer(
                needleValue = variometerNeedleValue,
                displayValue = variometerDisplayValue,
                modifier = Modifier.fillMaxSize()
            )

            if (widgetManager.mapState.isUIEditMode) {
                ResizeHandle(
                    onResize = { dragAmount ->
                        displaySize.value = (displaySize.value + (dragAmount.x + dragAmount.y) / 2)
                            .coerceIn(minSizePx, maxSizePx)
                        Log.d("MapUIWidgetManager", "Variometer resizing: ${displaySize.value} px")
                    },
                    onResizeEnd = {
                        widgetManager.saveWidgetSize("variometer", displaySize.value)
                        onSizeChange(displaySize.value)
                        Log.d("MapUIWidgetManager", "Variometer resize ended: ${displaySize.value} px")
                    }
                )
            }
        }
    }

    /**
     * Draggable Hamburger Menu widget
     */
    @Composable
    fun DraggableHamburgerMenu(
        hamburgerOffset: Offset,
        iconSize: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        widgetManager: MapUIWidgetManager,
        density: androidx.compose.ui.unit.Density,
        drawerState: DrawerState,
        coroutineScope: CoroutineScope,
        onOffsetChange: (Offset) -> Unit,
        onSizeChange: (Float) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Use independent position state during edit mode
        val displayOffset = remember(widgetManager.mapState.isUIEditMode) {
            mutableStateOf(hamburgerOffset)
        }

        // Use independent size state during edit mode
        val displaySize = remember(widgetManager.mapState.isUIEditMode) {
            mutableStateOf(iconSize)
        }

        // Sync with parameters when NOT in edit mode
        LaunchedEffect(hamburgerOffset, iconSize, widgetManager.mapState.isUIEditMode) {
            if (!widgetManager.mapState.isUIEditMode) {
                displayOffset.value = hamburgerOffset
                displaySize.value = iconSize
            }
        }

        Box(
            modifier = modifier
                .offset { IntOffset(displayOffset.value.x.roundToInt(), displayOffset.value.y.roundToInt()) }
                .size(displaySize.value.dp)
                .then(widgetManager.getEditModeBorder(RectangleShape))
                // Long-press detection (works in both modes)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            Log.d("MapUIWidgetManager", "🔴 Hamburger LONG-PRESSED - toggling edit mode")
                            widgetManager.toggleEditMode()
                        },
                        onTap = {
                            if (!widgetManager.mapState.isUIEditMode) {
                                Log.d("MapUIWidgetManager", "👆 Hamburger TAPPED")
                                coroutineScope.launch {
                                    if (drawerState.isOpen) {
                                        drawerState.close()
                                    } else {
                                        drawerState.open()
                                    }
                                }
                            }
                        }
                    )
                }
                // Drag detection (only in edit mode)
                .pointerInput(widgetManager.mapState.isUIEditMode) {
                    if (widgetManager.mapState.isUIEditMode) {
                        detectDragGestures(
                            onDragStart = {
                                Log.d("MapUIWidgetManager", "✅ Drag started from: ${displayOffset.value}")
                            },
                            onDrag = { change, dragAmount ->
                                displayOffset.value = Offset(
                                    x = (displayOffset.value.x + dragAmount.x).coerceIn(0f, screenWidthPx),
                                    y = (displayOffset.value.y + dragAmount.y).coerceIn(0f, screenHeightPx)
                                )
                                change.consume()
                            },
                            onDragEnd = {
                                Log.d("MapUIWidgetManager", "🏁 Drag ended at: ${displayOffset.value}")
                                widgetManager.saveWidgetPosition("hamburger_icon", displayOffset.value)
                                onOffsetChange(displayOffset.value) // Update parent state once at end
                            }
                        )
                    }
                }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle navigation drawer",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(((iconSize - 4) * 0.5f).dp)
                        .alpha(0.6f)
                )
            }

            if (widgetManager.mapState.isUIEditMode) {
                ResizeHandle(
                    onResize = { dragAmount ->
                        displaySize.value = (displaySize.value + (dragAmount.x + dragAmount.y) / 2)
                            .coerceIn(50f, 200f)
                        Log.d("MapUIWidgetManager", "Hamburger resizing: ${displaySize.value} dp")
                    },
                    onResizeEnd = {
                        widgetManager.saveWidgetSize("hamburger_icon", displaySize.value)
                        onSizeChange(displaySize.value)
                        Log.d("MapUIWidgetManager", "Hamburger resize ended: ${displaySize.value} dp")
                    }
                )
            }
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
