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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UIVariometer(
    needleValue: Float,
    displayValue: Float,
    valueLabel: String = String.format("%+.1f", displayValue),
    averageNeedleValue: Float? = null,
    modifier: Modifier = Modifier
) {
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(needleValue <= -5f || needleValue > 3f) {
        if (needleValue <= -5f || needleValue > 3f) {
            while (needleValue <= -5f || needleValue > 3f) {
                isFlashing = !isFlashing
                delay(500)
            }
        } else {
            isFlashing = false
        }
    }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 20.dp.toPx()

        val rawNeedleValue = needleValue
        val outerNeedleValue = rawNeedleValue.coerceIn(-9f, 9f)
        val microArcValue = rawNeedleValue.coerceIn(-1f, 1f)
        val microArcAngle = microArcValue * 135f

        val microBandOuter = radius * 0.78f
        val microBandInner = radius * 0.6f
        val microBandWidth = microBandOuter - microBandInner
        val microBandRectSize = Size(microBandOuter * 2f, microBandOuter * 2f)
        val microBandTopLeft = Offset(center.x - microBandOuter, center.y - microBandOuter)

        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = radius,
            center = center,
            style = Stroke(width = 6.dp.toPx())
        )

        drawCircle(
            color = Color.Black.copy(alpha = 0.1f),
            radius = radius * 0.8f,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        drawArc(
            color = Color.Black.copy(alpha = 0.08f),
            startAngle = -225f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = microBandTopLeft,
            size = microBandRectSize,
            style = Stroke(width = microBandWidth, cap = StrokeCap.Round)
        )

        if (microArcValue > 0f) {
            val liftColor = if (microArcValue > 0.3f) Color(0xFF22C55E) else Color(0xFF86EFAC)
            drawArc(
                color = liftColor.copy(alpha = 0.5f),
                startAngle = -90f,
                sweepAngle = microArcAngle,
                useCenter = false,
                topLeft = microBandTopLeft,
                size = microBandRectSize,
                style = Stroke(width = microBandWidth, cap = StrokeCap.Round)
            )
        } else if (microArcValue < 0f) {
            val sinkColor = if (microArcValue < -0.3f) Color(0xFFF87171) else Color(0xFFFECACA)
            drawArc(
                color = sinkColor,
                startAngle = -90f + microArcAngle,
                sweepAngle = -microArcAngle,
                useCenter = false,
                topLeft = microBandTopLeft,
                size = microBandRectSize,
                style = Stroke(width = microBandWidth, cap = StrokeCap.Round)
            )
        }

        val microTicks = listOf(-1f, -0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 0.75f, 1f)
        microTicks.forEach { tick ->
            val angle = tick * 135f - 90f
            val innerR = microBandInner - 6.dp.toPx()
            val outerR = microBandOuter + 4.dp.toPx()
            val startX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * innerR
            val startY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * innerR
            val endX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * outerR
            val endY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * outerR
            drawLine(
                color = Color.Black.copy(alpha = if (tick == 0f) 0.45f else 0.25f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = if (tick == 0f || abs(tick) == 1f) 3.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        for (i in -9..9) {
            val angle = i * (300f / 18f) - 90f
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

        val numberPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 127
            textSize = (12.sp.toPx() * 1.2f)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        val numbersToShow = listOf(-9, -6, -3, 0, 3, 6, 9)
        numbersToShow.forEach { number ->
            val angle = number * (300f / 18f) - 90f
            val textRadius = radius * 0.65f

            val textX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * textRadius
            val textY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * textRadius

            drawContext.canvas.nativeCanvas.drawText(
                number.toString(),
                textX,
                textY + numberPaint.textSize / 3f,
                numberPaint
            )
        }

        val needleAngle = (outerNeedleValue * (300f / 18f) - 90f)
        val needleLength = radius * 0.7f

        rotate(needleAngle, center) {
            drawLine(
                color = Color(0xFF60A5FA),
                start = center,
                end = Offset(center.x + needleLength, center.y),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        averageNeedleValue?.let { average ->
            val averageAngle = (average.coerceIn(-9f, 9f) * (300f / 18f) - 90f)
            rotate(averageAngle, center) {
                drawLine(
                    color = Color(0xFF7C3AED),
                    start = center,
                    end = Offset(center.x + needleLength, center.y),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        val microLabelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            valueLabel,
            center.x,
            center.y + 30.dp.toPx(),
            microLabelPaint
        )

        val centerColor = when {
            microArcValue > 0.3f -> Color(0xFF16A34A)
            microArcValue > 0f -> Color(0xFF4ADE80)
            microArcValue < -0.3f -> Color(0xFFDC2626)
            microArcValue < 0f -> Color(0xFFFCA5A5)
            else -> Color.Black
        }
        drawCircle(
            color = centerColor,
            radius = 6.dp.toPx(),
            center = center
        )
    }
}
