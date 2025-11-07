package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Clouds: ImageVector by lazy {
    createIcon(
        name = "Clouds",
        defaultWidth = 200.dp,
        defaultHeight = 80.dp,
        viewportWidth = 200f,
        viewportHeight = 80f
    ) {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(40f, 50f)
            curveTo(20f, 50f, 10f, 35f, 10f, 25f)
            curveTo(10f, 15f, 20f, 5f, 35f, 5f)
            curveTo(40f, -5f, 60f, -5f, 65f, 5f)
            curveTo(80f, 0f, 90f, 10f, 90f, 20f)
            curveTo(105f, 10f, 125f, 15f, 125f, 30f)
            curveTo(140f, 25f, 160f, 35f, 160f, 50f)
            curveTo(175f, 45f, 190f, 55f, 190f, 65f)
            curveTo(190f, 75f, 175f, 80f, 160f, 75f)
            lineTo(40f, 75f)
            curveTo(25f, 80f, 10f, 75f, 10f, 65f)
            curveTo(10f, 55f, 25f, 50f, 40f, 50f)
            close()
        }
    }
}
