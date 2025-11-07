package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Waypoints: ImageVector by lazy {
    createIcon(
        name = "Waypoints",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.5f, 4.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 12f, 7f)
            arcTo(2.5f, 2.5f, 0f, false, true, 9.5f, 4.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 14.5f, 4.5f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveToRelative(10.2f, 6.3f)
            lineToRelative(-3.9f, 3.9f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 4.5f, 14.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 2f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 7f, 12f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7f, 12f)
            horizontalLineToRelative(10f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(22f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 19.5f, 14.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 17f, 12f)
            arcTo(2.5f, 2.5f, 0f, false, true, 22f, 12f)
            close()
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveToRelative(13.8f, 17.7f)
            lineToRelative(3.9f, -3.9f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(14.5f, 19.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 12f, 22f)
            arcTo(2.5f, 2.5f, 0f, false, true, 9.5f, 19.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 14.5f, 19.5f)
            close()
        }
    }
}
