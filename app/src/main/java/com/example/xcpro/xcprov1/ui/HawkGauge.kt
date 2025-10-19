package com.example.xcpro.xcprov1.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal object HawkGaugePalette {
    val SinkSevere = Color(0xFFB71C1C)
    val SinkModerate = Color(0xFFFF8F00)
    val Neutral = Color(0xFF6D7075)
    val Climb = Color(0xFF00B050)
    val ClimbStrong = Color(0xFF00BCD4)

    val ActualNeedle = Color(0xFFFFC94D)
    val PotentialNeedle = Color(0xFF66E5FF)
    val Confidence = Color(0xFF64FFDA)
    val AoA = Color(0xFF00BCD4)
    val Slip = Color(0xFFFF7043)
}

internal val HawkGaugeDefaultSize = 260.dp

/**
 * Recreates the LXNAV HAWK dual-needle variometer with confidence ring,
 * colour-banded vertical speed scale, and AoA / sideslip edge bars.
 */
@Composable
fun HawkGauge(
    actualClimb: Double?,
    potentialClimb: Double?,
    confidence: Double,
    aoaDeg: Double?,
    sideslipDeg: Double?,
    modifier: Modifier = Modifier,
    gaugeSize: Dp = HawkGaugeDefaultSize
) {
    val actual = actualClimb ?: 0.0
    val potential = potentialClimb ?: 0.0
    val clampedConfidence = confidence.coerceIn(0.0, 1.0)

    val sweepRange = remember { GaugeRange(-5.0, 8.0) }
    val segments = remember {
        // Band thresholds derived from LXNAV SxHAWK manual Rev.6 (graphical display guidance).
        listOf(
            GaugeSegment(-5.0, -3.0, HawkGaugePalette.SinkSevere),
            GaugeSegment(-3.0, -1.0, HawkGaugePalette.SinkModerate),
            GaugeSegment(-1.0, 0.5, HawkGaugePalette.Neutral),
            GaugeSegment(0.5, 3.0, HawkGaugePalette.Climb),
            GaugeSegment(3.0, 8.0, HawkGaugePalette.ClimbStrong)
        )
    }

    val tickLabels = remember { listOf(-4, -2, 0, 2, 4, 6, 8) }
    val density = LocalDensity.current
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val themeSurface = MaterialTheme.colorScheme.surface
    val surfaceColor = if (themeSurface.luminance() > 0.45f) Color(0xFF101317) else themeSurface
    val baseSurface = if (themeSurface.luminance() > 0.45f) Color(0xFF1C232A) else MaterialTheme.colorScheme.surfaceVariant
    val baseOutline = if (themeSurface.luminance() > 0.45f) Color(0xFF2B323A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val actualNeedleColor = HawkGaugePalette.ActualNeedle
    val potentialNeedleColor = HawkGaugePalette.PotentialNeedle
    val confidenceColor = HawkGaugePalette.Confidence

    Box(
        modifier = modifier
            .size(gaugeSize)
            .background(surfaceColor, RoundedCornerShape(24.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        textPaint.color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f).toArgb()
        textPaint.textSize = with(density) { 12.sp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = min(size.width, size.height)
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            val outerRadius = radius * 0.95f
            val scaleRadius = radius * 0.92f
            val innerRadius = radius * 0.45f
            val confidenceRadius = radius * 0.58f

            // Base shadow ring
            drawArc(
                color = baseSurface,
                startAngle = 210f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = outerRadius * 0.1f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f)
            )

            // Colour segments that mirror the real gauge banding.
            segments.forEach { segment ->
                val startAngle = sweepRange.valueToAngle(segment.startValue)
                val endAngle = sweepRange.valueToAngle(segment.endValue)
                val sweepAngle = positiveSweep(startAngle, endAngle)
                drawArc(
                    color = segment.color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = outerRadius * 0.12f, cap = StrokeCap.Butt),
                    topLeft = Offset(center.x - scaleRadius, center.y - scaleRadius),
                    size = Size(scaleRadius * 2f, scaleRadius * 2f)
                )
            }

            // Major and minor tick marks.
            val majorTickLen = outerRadius * 0.16f
            val minorTickLen = outerRadius * 0.085f
            val tickStroke = outerRadius * 0.018f
            val labelRadius = scaleRadius * 0.66f

            for (step in 0..26) {
                val value = -5.0 + step * 0.5
                val angle = sweepRange.valueToAngle(value)
                val start = polarToCartesian(center, scaleRadius * 0.98f, angle)
                val isMajor = abs(value % 2.0) < 0.01
                val end = polarToCartesian(
                    center,
                    scaleRadius * 0.98f - if (isMajor) majorTickLen else minorTickLen,
                    angle
                )
                drawLine(
                    color = baseOutline,
                    start = start,
                    end = end,
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round
                )
            }

            tickLabels.forEach { value ->
                val angle = sweepRange.valueToAngle(value.toDouble())
                val labelPosition = polarToCartesian(center, labelRadius, angle)
                drawContext.canvas.nativeCanvas.drawText(
                    value.toString(),
                    labelPosition.x,
                    labelPosition.y + textPaint.textSize / 3f,
                    textPaint
                )
            }

            // Confidence arc inside the scale.
            drawArc(
                color = confidenceColor.copy(alpha = 0.18f + 0.55f * clampedConfidence.toFloat()),
                startAngle = 210f,
                sweepAngle = 300f * clampedConfidence.toFloat(),
                useCenter = false,
                style = Stroke(width = innerRadius * 0.25f, cap = StrokeCap.Round),
                topLeft = Offset(center.x - confidenceRadius, center.y - confidenceRadius),
                size = Size(confidenceRadius * 2f, confidenceRadius * 2f)
            )

            // Potential (airmass) needle - thinner inner needle.
            drawNeedle(
                center = center,
                radius = scaleRadius * 0.82f,
                value = potential,
                range = sweepRange,
                color = potentialNeedleColor,
                strokeWidth = outerRadius * 0.045f
            )

            // Actual climb needle - thicker foreground needle with tail.
            drawNeedle(
                center = center,
                radius = scaleRadius * 0.9f,
                value = actual,
                range = sweepRange,
                color = actualNeedleColor,
                strokeWidth = outerRadius * 0.06f,
                drawTail = true
            )

            // Central hub to mask needle origins.
            drawCircle(
                color = surfaceColor,
                radius = innerRadius * 0.68f,
                center = center
            )

            drawCircle(
                color = baseOutline.copy(alpha = 0.75f),
                radius = innerRadius * 0.74f,
                center = center,
                style = Stroke(width = outerRadius * 0.02f)
            )

            drawCircle(
                color = baseSurface,
                radius = innerRadius * 0.25f,
                center = center
            )

            // AoA tapered wedge on left.
            drawAoAIndicator(
                value = aoaDeg,
                center = center,
                gaugeRadius = scaleRadius
            )

            // Sideslip wedge on right.
            drawSideslipIndicator(
                value = sideslipDeg,
                center = center,
                gaugeRadius = scaleRadius
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatVario(actual),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = actualNeedleColor
            )
            Text(
                text = "m/s",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Potential",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatVario(potential),
                        style = MaterialTheme.typography.bodyLarge,
                        color = potentialNeedleColor
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Confidence",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(clampedConfidence * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = confidenceColor
                    )
                }
            }
        }

        // Edge indicators to mimic the real HAWK light bars.
        AoAEdgeBar(
            value = aoaDeg,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(gaugeSize * 0.7f)
                .width(32.dp)
        )

        SlipEdgeBar(
            value = sideslipDeg,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(gaugeSize * 0.7f)
                .width(32.dp)
        )
    }
}

@Composable
private fun AoAEdgeBar(
    value: Double?,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = HawkGaugePalette.AoA
    val cautionColor = Color(0xFFFFC947)
    val stallColor = Color(0xFFD50000)

    Surface(
        modifier = modifier,
        color = Color.Transparent
    ) {
        val aoa = value ?: 0.0
        val range = GaugeRangeLinear(-8.0, 18.0)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width
            val barHeight = size.height
            val radius = barWidth / 2f

            drawRoundRect(
                color = background,
                topLeft = Offset.Zero,
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )

            val normalized = range.normalize(aoa).toFloat()
            val levelHeight = barHeight * normalized
            val pathTop = barHeight - levelHeight
            val activeBrush = Brush.verticalGradient(
                colors = listOf(activeColor, cautionColor, stallColor),
                startY = 0f,
                endY = barHeight
            )
            drawRoundRect(
                brush = activeBrush,
                topLeft = Offset(0f, pathTop),
                size = Size(barWidth, levelHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            )

            drawLine(
                color = Color.Black.copy(alpha = 0.2f),
                start = Offset(barWidth / 2f, 0f),
                end = Offset(barWidth / 2f, barHeight),
                strokeWidth = barWidth * 0.08f
            )
        }
    }
}

@Composable
private fun SlipEdgeBar(
    value: Double?,
    modifier: Modifier = Modifier
) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val slipColor = HawkGaugePalette.Slip

    Surface(
        modifier = modifier,
        color = Color.Transparent
    ) {
        val slip = value ?: 0.0
        val range = GaugeRangeLinear(-12.0, 12.0)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width
            val barHeight = size.height
            val corner = barWidth / 2f

            drawRoundRect(
                color = background,
                topLeft = Offset.Zero,
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )

            // Center neutral marker.
            drawRoundRect(
                color = neutralColor,
                topLeft = Offset(0f, barHeight / 2f - barWidth / 2f),
                size = Size(barWidth, barWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )

            val normalized = range.normalize(slip).toFloat()
            val centerY = barHeight / 2f
            val displacement = (barHeight / 2f - barWidth) * normalized

            drawRoundRect(
                color = slipColor,
                topLeft = Offset(0f, centerY - barWidth / 2f - displacement),
                size = Size(barWidth, barWidth),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun DrawScope.drawAoAIndicator(
    value: Double?,
    center: Offset,
    gaugeRadius: Float
) {
    val aoa = value ?: 0.0
    val clamped = aoa.coerceIn(-8.0, 18.0)
    val normalized = ((clamped + 8.0) / (18.0 + 8.0)).toFloat()
    val baseAngle = 180f
    val spread = 55f
    val startAngle = baseAngle + spread * 0.5f * (1f - normalized)
    val sweepAngle = spread * normalized

    drawArc(
        color = HawkGaugePalette.AoA.copy(alpha = 0.32f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = gaugeRadius * 0.08f, cap = StrokeCap.Round),
        topLeft = Offset(center.x - gaugeRadius * 0.85f, center.y - gaugeRadius * 0.85f),
        size = Size(gaugeRadius * 1.7f, gaugeRadius * 1.7f)
    )
}

private fun DrawScope.drawSideslipIndicator(
    value: Double?,
    center: Offset,
    gaugeRadius: Float
) {
    val slip = value ?: 0.0
    val clamped = slip.coerceIn(-12.0, 12.0)
    val normalized = (clamped / 12.0).toFloat()
    val baseAngle = 0f
    val spread = 55f
    val startAngle = baseAngle - spread * 0.5f
    val sweepAngle = spread

    drawArc(
        color = HawkGaugePalette.Slip.copy(alpha = 0.32f),
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        style = Stroke(width = gaugeRadius * 0.08f, cap = StrokeCap.Round),
        topLeft = Offset(center.x - gaugeRadius * 0.85f, center.y - gaugeRadius * 0.85f),
        size = Size(gaugeRadius * 1.7f, gaugeRadius * 1.7f)
    )

    val slipAngle = baseAngle + spread * normalized
    val slipStart = polarToCartesian(center, gaugeRadius * 0.6f, slipAngle)
    val slipEnd = polarToCartesian(center, gaugeRadius * 0.85f, slipAngle)
    drawLine(
        color = HawkGaugePalette.Slip,
        start = slipStart,
        end = slipEnd,
        strokeWidth = gaugeRadius * 0.045f,
        cap = StrokeCap.Round
    )
}

@Composable
fun WindRibbon(
    windX: Double,
    windY: Double,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
) {
    val speed = sqrt(windX * windX + windY * windY)
    val directionRad = atan2(windY, windX)
    val ribbonColor = MaterialTheme.colorScheme.primary
    val neutral = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Wind",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val centerY = size.height / 2f
                val padding = 32f
                val usableWidth = size.width - padding * 2f
                val normalized = (speed / 40.0).coerceIn(0.0, 1.0)
                val baseLength = max(usableWidth * normalized.toFloat(), 60f)

                val start = Offset(padding, centerY)
                val end = Offset(
                    x = padding + baseLength * cos(directionRad).toFloat(),
                    y = centerY - baseLength * sin(directionRad).toFloat()
                )

                drawLine(
                    color = neutral,
                    start = Offset(padding, centerY),
                    end = Offset(size.width - padding, centerY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = ribbonColor,
                    start = start,
                    end = end,
                    strokeWidth = 16f,
                    cap = StrokeCap.Round
                )

                val headSize = 24f
                val arrowHead = Path().apply {
                    moveTo(end.x, end.y)
                    val left = directionRad + PI.toFloat() * 0.78f
                    val right = directionRad - PI.toFloat() * 0.78f
                    lineTo(
                        end.x + headSize * cos(left).toFloat(),
                        end.y - headSize * sin(left).toFloat()
                    )
                    lineTo(
                        end.x + headSize * cos(right).toFloat(),
                        end.y - headSize * sin(right).toFloat()
                    )
                    close()
                }
                drawPath(path = arrowHead, color = ribbonColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${speed.formatKnots()} kt",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class GaugeSegment(
    val startValue: Double,
    val endValue: Double,
    val color: Color
)

private fun positiveSweep(startAngle: Float, endAngle: Float): Float {
    var sweep = endAngle - startAngle
    while (sweep <= 0f) {
        sweep += 360f
    }
    return sweep
}

private fun formatVario(value: Double): String = String.format("%.1f", value)

private fun Double.formatKnots(): String = String.format("%.1f", this * 1.94384)

private fun DrawScope.drawNeedle(
    center: Offset,
    radius: Float,
    value: Double,
    range: GaugeRange,
    color: Color,
    strokeWidth: Float,
    drawTail: Boolean = false
) {
    val angle = range.valueToAngle(value)
    val end = polarToCartesian(center, radius, angle)
    drawLine(
        color = color,
        start = center,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    if (drawTail) {
        val tailStart = polarToCartesian(center, radius * 0.2f, angle + 180f)
        drawLine(
            color = color.copy(alpha = 0.35f),
            start = center,
            end = tailStart,
            strokeWidth = strokeWidth * 0.55f,
            cap = StrokeCap.Round
        )
    }
}

private fun polarToCartesian(center: Offset, radius: Float, angleDegrees: Float): Offset {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return Offset(
        x = center.x + (radius * cos(radians)).toFloat(),
        y = center.y + (radius * sin(radians)).toFloat()
    )
}

private class GaugeRange(
    private val min: Double,
    private val max: Double
) {
    private val startAngle = 210f
    private val endAngle = -90f

    fun valueToAngle(value: Double): Float {
        val clamped = value.coerceIn(min, max)
        val ratio = ((clamped - min) / (max - min)).toFloat()
        return startAngle + (endAngle - startAngle) * ratio
    }
}

private class GaugeRangeLinear(
    private val min: Double,
    private val max: Double
) {
    fun normalize(value: Double): Double {
        val clamped = value.coerceIn(min, max)
        return (clamped - min) / (max - min)
    }
}

private fun Color.toArgb(): Int =
    android.graphics.Color.argb(
        (this.alpha * 255).roundToInt(),
        (this.red * 255).roundToInt(),
        (this.green * 255).roundToInt(),
        (this.blue * 255).roundToInt()
    )
