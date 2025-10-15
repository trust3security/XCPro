package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Reply_all: ImageVector by lazy {
    createIcon(
        name = "Reply_all",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ) {
        path(
            fill = SolidColor(Color(0xFF000000))
        ) {
            moveTo(320f, 680f)
            lineTo(80f, 440f)
            lineToRelative(240f, -240f)
            lineToRelative(57f, 56f)
            lineToRelative(-184f, 184f)
            lineToRelative(184f, 184f)
            close()
            moveToRelative(480f, 80f)
            verticalLineToRelative(-160f)
            quadToRelative(0f, -50f, -35f, -85f)
            reflectiveQuadToRelative(-85f, -35f)
            horizontalLineTo(433f)
            lineToRelative(144f, 144f)
            lineToRelative(-57f, 56f)
            lineToRelative(-240f, -240f)
            lineToRelative(240f, -240f)
            lineToRelative(57f, 56f)
            lineToRelative(-144f, 144f)
            horizontalLineToRelative(247f)
            quadToRelative(83f, 0f, 141.5f, 58.5f)
            reflectiveQuadTo(880f, 600f)
            verticalLineToRelative(160f)
            close()
        }
    }
}

