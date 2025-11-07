package com.example.ui1.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Task: ImageVector by lazy {
    createIcon(
        name = "Task",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ) {
        val black = SolidColor(Color.Black)
        val navel = SolidColor(Color(0xFFFFDEAD))
        val radius = 2.5f

        // Triangle points
        val top = Pair(12f, 6f)
        val left = Pair(6f, 18f)
        val right = Pair(18f, 18f)

        // Circles
        fun drawCircle(centerX: Float, centerY: Float) {
            path(
                stroke = black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(centerX + radius, centerY)
                arcTo(radius, radius, 0f, false, true, centerX - radius, centerY)
                arcTo(radius, radius, 0f, false, true, centerX + radius, centerY)
                close()
            }
        }

        drawCircle(top.first, top.second)
        drawCircle(left.first, left.second)
        drawCircle(right.first, right.second)

        // Lines between circles
        val offset = 2.8f
        path(stroke = black, strokeLineWidth = 2f) {
            moveTo(top.first, top.second + offset)
            lineTo(left.first + offset * 0.5f, left.second - offset * 0.8f)
        }
        path(stroke = black, strokeLineWidth = 2f) {
            moveTo(top.first, top.second + offset)
            lineTo(right.first - offset * 0.5f, right.second - offset * 0.8f)
        }
        path(stroke = black, strokeLineWidth = 2f) {
            moveTo(left.first + offset, left.second)
            lineTo(right.first - offset, right.second)
        }

        // Enlarged + sign (slightly larger than before)
        val plusSize = 5.5f // was 4f
        val plusCenterX = 20f
        val plusCenterY = 4f
        path(
            stroke = navel,
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(plusCenterX, plusCenterY - plusSize / 2)
            lineTo(plusCenterX, plusCenterY + plusSize / 2)
        }
        path(
            stroke = navel,
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(plusCenterX - plusSize / 2, plusCenterY)
            lineTo(plusCenterX + plusSize / 2, plusCenterY)
        }
    }
}
