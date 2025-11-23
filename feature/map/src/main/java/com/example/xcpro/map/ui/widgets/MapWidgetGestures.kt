@file:Suppress("DEPRECATION")

package com.example.xcpro.map.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Reusable pointer helpers for draggable widgets.
 */
internal fun Modifier.draggableWidget(
    enabled: Boolean,
    key1: Any?,
    key2: Any?,
    key3: Any? = null,
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit = {}
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(key1, key2, key3) {
        detectDrag(onDragStart, onDrag, onDragEnd)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private suspend fun PointerInputScope.detectDrag(
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    detectDragGestures(
        onDragStart = { onDragStart() },
        onDrag = { change, dragAmount ->
            onDrag(dragAmount)
            change.consumePositionChange()
        },
        onDragEnd = { onDragEnd() },
        onDragCancel = { onDragEnd() }
    )
}

internal fun Offset.toIntOffset(): IntOffset =
    IntOffset(x.roundToInt(), y.roundToInt())
