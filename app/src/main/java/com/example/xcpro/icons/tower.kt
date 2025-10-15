package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val ControlTower: ImageVector by lazy {
    createIcon(
        name = "ControlTower",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f
    ) {
        // Tower base
        path(
            fill = SolidColor(Color(0xFF000000))
        ) {
            moveTo(384f, 896f)
            lineTo(640f, 896f)
            lineTo(640f, 512f)
            lineTo(384f, 512f)
            close()

            // Lower section
            moveTo(320f, 448f)
            lineTo(704f, 448f)
            lineTo(640f, 384f)
            lineTo(384f, 384f)
            close()

            // Main cabin
            moveTo(288f, 352f)
            lineTo(736f, 352f)
            lineTo(672f, 128f)
            lineTo(352f, 128f)
            close()

            // Antenna
            moveTo(480f, 96f)
            lineTo(544f, 96f)
            lineTo(544f, 48f)
            lineTo(480f, 48f)
            close()

            // Left radio waves
            moveTo(192f, 192f)
            curveTo(128f, 256f, 128f, 352f, 192f, 416f)
            moveTo(128f, 128f)
            curveTo(48f, 224f, 48f, 384f, 128f, 480f)

            // Right radio waves
            moveTo(832f, 192f)
            curveTo(896f, 256f, 896f, 352f, 832f, 416f)
            moveTo(896f, 128f)
            curveTo(976f, 224f, 976f, 384f, 896f, 480f)
        }
    }
}
