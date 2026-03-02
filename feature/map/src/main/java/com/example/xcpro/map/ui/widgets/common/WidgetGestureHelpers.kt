package com.example.xcpro.map.ui.widgets.common

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.unit.dp
import com.example.xcpro.map.MapOverlayGestureTarget
import com.example.xcpro.map.ui.widgets.MapUIWidgetManager
import com.example.xcpro.map.ui.widgets.MapWidgetTheme

internal fun Modifier.editModeBorder(
    isEditMode: Boolean,
    shape: Shape = RectangleShape
): Modifier {
    return if (isEditMode) {
        border(width = 2.dp, color = MapWidgetTheme.editBorderColor, shape = shape)
    } else {
        this
    }
}

internal fun MapUIWidgetManager.updateWidgetGestureRegion(
    target: MapOverlayGestureTarget,
    bounds: LayoutCoordinates,
    consumeGestures: Boolean = false
) {
    updateGestureRegion(target = target, bounds = bounds.boundsInRoot(), consumeGestures = consumeGestures)
}
