package com.example.xcpro.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Color Picker Drawing Functions
 *
 * Extracted from ColorPicker.kt for file size compliance.
 * Contains all Canvas drawing logic for color wheels and gradients.
 */

/**
 * Draw compact hue ring with saturation/value square in center
 */
internal fun DrawScope.drawCompactHueRing(hue: Float, saturation: Float, value: Float) {
    val center = size.center
    val radius = size.minDimension / 2f
    val strokeWidth = radius * 0.15f // Thinner stroke for compact version

    // Draw hue ring
    for (i in 0..360 step 2) { // Skip every other degree for performance
        val color = Color.hsv(i.toFloat(), 1f, 1f)
        val startAngle = i.toFloat() - 1f

        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = 2f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
    }

    // Draw saturation/value square in center
    val squareSize = radius * 0.5f
    val squareTopLeft = Offset(center.x - squareSize / 2f, center.y - squareSize / 2f)

    val horizontalGradient = Brush.horizontalGradient(
        colors = listOf(Color.White, Color.hsv(hue, 1f, 1f)),
        startX = squareTopLeft.x,
        endX = squareTopLeft.x + squareSize
    )
    val verticalGradient = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black),
        startY = squareTopLeft.y,
        endY = squareTopLeft.y + squareSize
    )

    // Draw saturation gradient
    drawRect(
        brush = horizontalGradient,
        topLeft = squareTopLeft,
        size = Size(squareSize, squareSize)
    )

    // Draw value gradient
    drawRect(
        brush = verticalGradient,
        topLeft = squareTopLeft,
        size = Size(squareSize, squareSize),
        blendMode = BlendMode.Multiply
    )

    // Draw hue indicator
    val hueAngle = hue * PI / 180
    val indicatorRadius = radius - strokeWidth / 2
    val indicatorCenter = Offset(
        center.x + indicatorRadius * cos(hueAngle).toFloat(),
        center.y + indicatorRadius * sin(hueAngle).toFloat()
    )

    drawCircle(
        color = Color.White,
        radius = 4.dp.toPx(),
        center = indicatorCenter,
        style = Stroke(width = 2.dp.toPx())
    )

    // Draw saturation/value indicator
    val satIndicatorX = squareTopLeft.x + saturation * squareSize
    val valIndicatorY = squareTopLeft.y + (1f - value) * squareSize

    drawCircle(
        color = Color.White,
        radius = 3.dp.toPx(),
        center = Offset(satIndicatorX, valIndicatorY),
        style = Stroke(width = 1.5.dp.toPx())
    )
    drawCircle(
        color = Color.Black,
        radius = 2.dp.toPx(),
        center = Offset(satIndicatorX, valIndicatorY),
        style = Stroke(width = 1.dp.toPx())
    )
}

/**
 * Draw full-size hue ring (used in non-compact mode)
 */
internal fun DrawScope.drawHueRing(hue: Float, saturation: Float, value: Float) {
    val center = size.center
    val radius = size.minDimension / 2f
    val strokeWidth = radius * 0.2f

    // Draw hue ring
    for (i in 0..360) {
        val angle = i * PI / 180
        val color = Color.hsv(i.toFloat(), 1f, 1f)
        val startAngle = i.toFloat() - 0.5f

        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = 1f,
            useCenter = false,
            style = Stroke(width = strokeWidth)
        )
    }

    // Draw hue indicator
    val hueAngle = hue * PI / 180
    val indicatorRadius = radius - strokeWidth / 2
    val indicatorCenter = Offset(
        center.x + indicatorRadius * cos(hueAngle).toFloat(),
        center.y + indicatorRadius * sin(hueAngle).toFloat()
    )

    drawCircle(
        color = Color.White,
        radius = 8.dp.toPx(),
        center = indicatorCenter,
        style = Stroke(width = 3.dp.toPx())
    )
}

/**
 * Draw saturation/value selection square
 */
internal fun DrawScope.drawSaturationValueSquare(hue: Float, saturation: Float, value: Float) {
    val width = size.width
    val height = size.height

    // Create gradient brush
    val horizontalGradient = Brush.horizontalGradient(
        colors = listOf(Color.White, Color.hsv(hue, 1f, 1f))
    )
    val verticalGradient = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black)
    )

    // Draw saturation gradient (left to right)
    drawRect(brush = horizontalGradient)

    // Draw value gradient (top to bottom)
    drawRect(brush = verticalGradient, blendMode = BlendMode.Multiply)

    // Draw selection indicator
    val indicatorX = saturation * width
    val indicatorY = (1f - value) * height

    drawCircle(
        color = Color.White,
        radius = 6.dp.toPx(),
        center = Offset(indicatorX, indicatorY),
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = Color.Black,
        radius = 4.dp.toPx(),
        center = Offset(indicatorX, indicatorY),
        style = Stroke(width = 1.dp.toPx())
    )
}
