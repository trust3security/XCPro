package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Sailplane icon optimized for current location display on map.
 * Scaled down from original Sailplane.kt for map visibility.
 * Points upward (north) by default for proper rotation based on bearing.
 * White fill with black stroke for visibility on all map backgrounds.
 */
val LocationSailplane: ImageVector by lazy {
    createIcon(
        name = "LocationSailplane",
        defaultWidth = 18.dp,
        defaultHeight = 18.dp,
        viewportWidth = 18f,
        viewportHeight = 18f
    ) {
        // Main wing - white fill with black stroke
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 0.5f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(1f, 8f)
            lineTo(7f, 7f)
            lineTo(11f, 7f)
            lineTo(17f, 8f)
            lineTo(11f, 9f)
            lineTo(7f, 9f)
            close()
        }
        // Fuselage - points upward (north) for rotation
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 0.5f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(9f, 2f)  // Nose points up (north)
            lineTo(9.5f, 14f)  // Tail points down (south)
            lineTo(8.5f, 14f)
            lineTo(8f, 2f)
            close()
        }
        // Tailplane (horizontal stabilizer)
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 0.5f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(7f, 13f)
            lineTo(11f, 13f)
            lineTo(11f, 13.5f)
            lineTo(7f, 13.5f)
            close()
        }
        // Fin (vertical stabilizer)
        path(
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 0.5f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(8.2f, 12f)
            lineTo(9.8f, 12f)
            lineTo(9f, 10f)
            close()
        }
    }
}