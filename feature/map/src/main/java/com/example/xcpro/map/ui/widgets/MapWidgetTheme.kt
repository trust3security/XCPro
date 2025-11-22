package com.example.xcpro.map.ui.widgets

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared theme tokens for map overlay widgets.
 */
internal object MapWidgetTheme {
    val editBorderColor: Color = Color.Red
    val editBorderWidth: Dp = 2.dp
    val widgetCorner: RoundedCornerShape = RoundedCornerShape(12.dp)
    val pillCorner: RoundedCornerShape = RoundedCornerShape(18.dp)
}
