package com.trust3.xcpro.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
internal fun ensureSafeContainerFallback(
    safeContainerSizeState: MutableState<IntSize>,
    screenWidthPx: Float,
    screenHeightPx: Float
) {
    LaunchedEffect(screenWidthPx, screenHeightPx) {
        if (safeContainerSizeState.value == IntSize.Zero) {
            val fallbackWidth = screenWidthPx.roundToInt().coerceAtLeast(1)
            val fallbackHeight = screenHeightPx.roundToInt().coerceAtLeast(1)
            if (fallbackWidth > 0 && fallbackHeight > 0) {
                safeContainerSizeState.value = IntSize(fallbackWidth, fallbackHeight)
            }
        }
    }
}
