package com.example.ui1

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UIVariometer(
    value: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isFlashing by remember { mutableStateOf(false) }

    // Flash animation when value is in extreme ranges
    LaunchedEffect(value <= -5f || value > 3f) {
        if (value <= -5f || value > 3f) {
            while (value <= -5f || value > 3f) {
                isFlashing = !isFlashing
                delay(500) // Flash every 500ms
            }
        } else {
            isFlashing = false
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 20.dp.toPx()

        // Draw outer circle
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = 6.dp.toPx())
        )

        // Draw inner circle
        drawCircle(
            color = Color.Black.copy(alpha = 0.1f),
            radius = radius * 0.8f,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        // Draw scale marks (-9 to +9, 300 degree arc)
        for (i in -9..9) {
            val angle = i * (300f / 18f) - 90f // Each unit = 16.67°, offset by -90° so 0 is at 12 o'clock
            val startRadius = radius * 0.85f
            val endRadius = radius * 0.95f

            val startX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * startRadius
            val startY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * startRadius
            val endX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * endRadius
            val endY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * endRadius

            drawLine(
                color = Color.Black.copy(alpha = 0.4f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (i == 0) 3.dp.toPx() else 1.dp.toPx()
            )
        }

        // Draw numbers inside the clock with 50% transparency
        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 127 // 50% transparency (255 * 0.5 = 127.5)
            textSize = (12.sp.toPx() * 1.2f) // 20% larger
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true // Make text bold
        }

        // Draw numbers at key positions
        val numbersToShow = listOf(-9, -6, -3, 0, 3, 6, 9)
        for (number in numbersToShow) {
            val angle = number * (300f / 18f) - 90f
            val textRadius = radius * 0.65f

            val textX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * textRadius
            val textY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * textRadius

            drawContext.canvas.nativeCanvas.drawText(
                number.toString(),
                textX,
                textY + textPaint.textSize / 3f, // Adjust vertical centering
                textPaint
            )
        }

        // Draw needle
        val needleAngle = (value.coerceIn(-9f, 9f) * (300f / 18f) - 90f)
        val needleLength = radius * 0.7f

        rotate(needleAngle, center) {
            drawLine(
                color = when {
                    value >= 0f -> {
                        if (value > 3f) {
                            // Flash between green and dark purple for strong climb
                            if (isFlashing) Color(0xFF22C55E) // Green
                            else Color(0xFF6B21A8) // Dark purple
                        } else {
                            Color(0xFF22C55E) // Normal green
                        }
                    }
                    value >= -2.6f -> Color(0xFF60A5FA) // Blue for neutral (-2.6 to 0)
                    else -> {
                        if (value <= -5f) {
                            // Flash between light red and dark red for strong descent
                            if (isFlashing) Color(0xFFEF4444) // Light red
                            else Color(0xFFB91C1C) // Dark red
                        } else {
                            Color(0xFFEF4444) // Normal red
                        }
                    }
                },
                start = center,
                end = Offset(center.x + needleLength, center.y),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Draw center dot
        drawCircle(
            color = Color.Black,
            radius = 6.dp.toPx(),
            center = center
        )
    }
}