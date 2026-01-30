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
import androidx.compose.ui.graphics.Path
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
import kotlin.math.roundToInt

private const val DEFAULT_DIAL_MAX_SI = 5f
private const val DEFAULT_DIAL_SWEEP_DEG = 300f
private const val DEFAULT_PIVOT_RATIO = 6f / 14f
private const val DEFAULT_PIVOT_FRACTION = 0.7f
private const val DEFAULT_MICRO_RANGE_SI = 1f

data class VarioDialLabel(
    val valueSi: Float,
    val text: String
)

data class VarioDialConfig(
    val maxValueSi: Float = DEFAULT_DIAL_MAX_SI,
    val sweepDegrees: Float = DEFAULT_DIAL_SWEEP_DEG,
    val pivotValueSi: Float = maxValueSi * DEFAULT_PIVOT_RATIO,
    val pivotDialFraction: Float = DEFAULT_PIVOT_FRACTION,
    val microRangeSi: Float = DEFAULT_MICRO_RANGE_SI,
    val microTicks: List<Float> = listOf(-1f, -0.75f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 0.75f, 1f),
    val majorTickStepSi: Float = 1f,
    val labelValues: List<VarioDialLabel> = defaultLabelValues(maxValueSi)
)

private fun defaultLabelValues(maxValueSi: Float): List<VarioDialLabel> {
    val maxRounded = maxValueSi.roundToInt().coerceAtLeast(1)
    return (-maxRounded..maxRounded).map { tick ->
        VarioDialLabel(tick.toFloat(), tick.toString())
    }
}

private fun mapToDialValue(value: Float, config: VarioDialConfig): Float {
    val maxValue = config.maxValueSi.coerceAtLeast(0.1f)
    val sign = if (value < 0f) -1f else 1f
    val absValue = abs(value)
    val pivot = config.pivotValueSi.coerceIn(0.1f, maxValue - 0.1f)
    val pivotDial = (maxValue * config.pivotDialFraction).coerceIn(0.1f, maxValue - 0.1f)
    val scaled = if (absValue <= pivot) {
        (absValue / pivot) * pivotDial
    } else {
        val highRange = maxValue - pivot
        val highDial = maxValue - pivotDial
        pivotDial + ((absValue - pivot) / highRange) * highDial
    }
    val over = absValue - maxValue
    val softened = if (over <= 0f) {
        scaled
    } else {
        val bleed = maxValue * 0.02f
        maxValue - (bleed / (1f + over))
    }
    return sign * softened.coerceIn(0f, maxValue)
}

private fun dialAngle(value: Float, config: VarioDialConfig): Float {
    val maxValue = config.maxValueSi.coerceAtLeast(0.1f)
    val dialValue = mapToDialValue(value, config)
    val span = maxValue * 2f
    return dialValue * (config.sweepDegrees / span) - 90f
}

@Composable
fun UIVariometer(
    needleValue: Float,
    fastNeedleValue: Float? = null,
    displayValue: Float,
    valueLabel: String = String.format("%+.1f", displayValue),
    secondaryLabel: String? = null,
    averageNeedleValue: Float? = null,
    dialConfig: VarioDialConfig = VarioDialConfig(),
    windDirectionScreenDeg: Float? = null,
    windIsValid: Boolean = false,
    windSpeedLabel: String? = null,
    modifier: Modifier = Modifier
) {
    var isFlashing by remember { mutableStateOf(false) }

    LaunchedEffect(displayValue <= -5f || displayValue > 3f) {
        if (displayValue <= -5f || displayValue > 3f) {
            while (displayValue <= -5f || displayValue > 3f) {
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
        val microRange = dialConfig.microRangeSi.coerceAtLeast(0.1f)
        val microArcValue = (rawNeedleValue / microRange).coerceIn(-1f, 1f)
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

        dialConfig.microTicks.forEach { tick ->
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

        val majorStep = dialConfig.majorTickStepSi.coerceAtLeast(0.1f)
        val maxTick = dialConfig.maxValueSi.coerceAtLeast(majorStep)
        var tick = -maxTick
        while (tick <= maxTick + 1e-3f) {
            val angle = dialAngle(tick, dialConfig)
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
                strokeWidth = if (abs(tick) < 1e-3f) 3.dp.toPx() else 1.dp.toPx()
            )
            tick += majorStep
        }

        val numberPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            alpha = 127
            textSize = (12.sp.toPx() * 1.2f)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }

        dialConfig.labelValues.forEach { label ->
            val angle = dialAngle(label.valueSi, dialConfig)
            val textRadius = radius * 0.65f

            val textX = center.x + cos(Math.toRadians(angle.toDouble())).toFloat() * textRadius
            val textY = center.y + sin(Math.toRadians(angle.toDouble())).toFloat() * textRadius

            drawContext.canvas.nativeCanvas.drawText(
                label.text,
                textX,
                textY + numberPaint.textSize / 3f,
                numberPaint
            )
        }

        val needleAngle = dialAngle(rawNeedleValue, dialConfig)
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

        fastNeedleValue?.let { fast ->
            val fastAngle = dialAngle(fast, dialConfig)
            rotate(fastAngle, center) {
                drawLine(
                    color = Color(0xFFEF4444),
                    start = center,
                    end = Offset(center.x + needleLength, center.y),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        averageNeedleValue?.let { average ->
            val averageAngle = dialAngle(average, dialConfig)
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

        if (windIsValid) {
            windDirectionScreenDeg?.let { rawDirection ->
                val direction = ((rawDirection % 360f) + 360f) % 360f
                val arrowColor = Color(0xFF22C55E)
                val arrowHeight = (radius * 0.16f).coerceIn(10.dp.toPx(), 24.dp.toPx())
                val arrowWidth = (arrowHeight * 1.05f).coerceIn(8.dp.toPx(), 22.dp.toPx())
                val ringStroke = 6.dp.toPx()
                val baseRadius = radius - ringStroke * 0.5f
                val tipRadius = (baseRadius - arrowHeight).coerceAtLeast(radius * 0.4f)
                val baseY = center.y - baseRadius
                val tipY = center.y - tipRadius
                val halfWidth = arrowWidth / 2f
                val arrowPath = Path().apply {
                    moveTo(center.x - halfWidth, baseY)
                    lineTo(center.x + halfWidth, baseY)
                    lineTo(center.x, tipY)
                    close()
                }
                rotate(direction, center) {
                    drawPath(arrowPath, color = arrowColor)
                    drawPath(
                        arrowPath,
                        color = Color(0xFFEF4444),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            val windLabel = windSpeedLabel?.trim().orEmpty()
            if (windLabel.isNotEmpty()) {
                val windPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 14.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    windLabel,
                    center.x,
                    center.y - radius * 0.35f,
                    windPaint
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

        secondaryLabel?.let { label ->
            val secondaryPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 16.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                label,
                center.x,
                center.y + 50.dp.toPx(),
                secondaryPaint
            )
        }

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
