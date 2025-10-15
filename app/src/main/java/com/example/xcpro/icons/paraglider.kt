package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Paraglider: ImageVector by lazy {
    createIcon(
        name = "Paraglider",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ) {
        // Canopy
        path(fill = SolidColor(Color.Black)) {
            moveTo(120f, 240f)
            quadTo(480f, 40f, 840f, 240f)
            lineTo(800f, 280f)
            quadTo(480f, 100f, 160f, 280f)
            close()
        }

        // Suspension lines
        path(fill = SolidColor(Color.Black)) {
            moveTo(300f, 280f)
            lineTo(450f, 480f)

            moveTo(660f, 280f)
            lineTo(510f, 480f)
        }

        // Pilot body
        path(fill = SolidColor(Color.Black)) {
            moveTo(450f, 480f)
            quadTo(470f, 500f, 480f, 530f)
            quadTo(490f, 500f, 510f, 480f)
            close()
        }

        // Legs
        path(fill = SolidColor(Color.Black)) {
            moveTo(480f, 530f)
            lineTo(460f, 580f)

            moveTo(480f, 530f)
            lineTo(500f, 580f)
        }

        // Head (as oval)
        path(fill = SolidColor(Color.Black)) {

        }
    }
}
