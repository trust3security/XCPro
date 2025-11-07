package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Sailplane: ImageVector by lazy {
    createIcon(
        name = "Sailplane",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 48f,
        viewportHeight = 48f
    ) {
        // Main wing
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(2f, 22f)
            lineTo(18f, 20f)
            lineTo(30f, 20f)
            lineTo(46f, 22f)
            lineTo(30f, 24f)
            lineTo(18f, 24f)
            close()
        }
        // Fuselage
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(24f, 12f)
            lineTo(25f, 36f)
            lineTo(23f, 36f)
            lineTo(22f, 12f)
            close()
        }
        // Tailplane (horizontal stabilizer)
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(18f, 34f)
            lineTo(30f, 34f)
            lineTo(30f, 35f)
            lineTo(18f, 35f)
            close()
        }
        // Fin (vertical stabilizer)
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(22.5f, 32f)
            lineTo(25.5f, 32f)
            lineTo(24f, 28f)
            close()
        }
    }
}
