package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Glider: ImageVector by lazy {
    createIcon(
        name = "Glider",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f
    ) {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(2f, 24f)
            lineTo(20f, 20f)
            lineTo(32f, 6f)
            lineTo(30f, 20f)
            lineTo(46f, 24f)
            lineTo(30f, 28f)
            lineTo(32f, 42f)
            lineTo(20f, 28f)
            lineTo(2f, 24f)
            close()
        }
    }
}
