package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType.Companion.NonZero
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap.Companion.Butt
import androidx.compose.ui.graphics.StrokeJoin.Companion.Miter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Hangglider: ImageVector by lazy {
    createIcon(
        name = "Hangglider",
        defaultWidth = 24.0.dp,
        defaultHeight = 24.0.dp,
        viewportWidth = 24.0f,
        viewportHeight = 24.0f
    ) {
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero
        ) {
            // Wing: Triangle from base (4,18 to 20,18) to apex (12,4)
            moveTo(4.0f, 18.0f)
            lineTo(12.0f, 4.0f)
            lineTo(20.0f, 18.0f)
            close()
        }
        path(
            fill = SolidColor(Color(0xFF000000)),
            stroke = null,
            strokeLineWidth = 0.0f,
            strokeLineCap = Butt,
            strokeLineJoin = Miter,
            strokeLineMiter = 4.0f,
            pathFillType = NonZero
        ) {
            // Pilot Frame: Trapezoid from wing base (9,18 to 15,18) to bottom (10,22 to 14,22)
            moveTo(9.0f, 18.0f)
            lineTo(10.0f, 22.0f)
            lineTo(14.0f, 22.0f)
            lineTo(15.0f, 18.0f)
            close()
        }
    }
}