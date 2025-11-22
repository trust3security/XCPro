package com.example.xcpro.map.ui.widgets

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.border
import com.example.xcpro.map.ui.widgets.MapWidgetTheme.editBorderColor
import com.example.xcpro.map.ui.widgets.MapWidgetTheme.editBorderWidth

/**
 * Adds a red border when edit mode is enabled to make draggable areas visible.
 */
internal fun Modifier.editModeBorder(
    isEditMode: Boolean,
    shape: Shape = RectangleShape
): Modifier {
    return if (isEditMode) {
        border(
            width = editBorderWidth,
            color = editBorderColor,
            shape = shape
        )
    } else {
        this
    }
}
