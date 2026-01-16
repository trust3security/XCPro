package com.example.xcpro.map.trail

import android.graphics.Color
import org.maplibre.android.style.expressions.Expression

internal object SnailTrailPalette {
    const val NUM_COLORS = 15

    fun colorExpression(type: TrailType): Expression {
        val stops = ArrayList<Expression.Stop>(NUM_COLORS)
        for (i in 0 until NUM_COLORS) {
            val color = colorFor(type, i)
            stops.add(Expression.stop(i, Expression.color(color)))
        }
        return Expression.match(
            Expression.get(SnailTrailStyle.PROP_COLOR_INDEX),
            Expression.color(Color.GRAY),
            *stops.toTypedArray()
        )
    }

    fun colorFor(type: TrailType, index: Int): Int {
        val rampValue = index * 200 / (NUM_COLORS - 1)
        return when (type) {
            TrailType.ALTITUDE -> colorRampLookup(rampValue, altitudeRamp)
            TrailType.VARIO_2, TrailType.VARIO_2_DOTS, TrailType.VARIO_DOTS_AND_LINES -> {
                colorRampLookup(rampValue, vario2Ramp)
            }
            TrailType.VARIO_EINK -> colorRampLookup(rampValue, varioEinkRamp)
            else -> colorRampLookup(rampValue, vario1Ramp)
        }
    }

    fun colorHex(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    private fun colorRampLookup(value: Int, ramp: List<ColorRamp>): Int {
        if (ramp.isEmpty()) return Color.GRAY
        val clamped = value.coerceIn(ramp.first().position, ramp.last().position)
        var lower = ramp.first()
        var upper = ramp.last()
        for (i in 1 until ramp.size) {
            if (clamped <= ramp[i].position) {
                lower = ramp[i - 1]
                upper = ramp[i]
                break
            }
        }
        val range = kotlin.math.max(1, upper.position - lower.position)
        val t = (clamped - lower.position).toDouble() / range.toDouble()
        val r = (lower.r + (upper.r - lower.r) * t).toInt()
        val g = (lower.g + (upper.g - lower.g) * t).toInt()
        val b = (lower.b + (upper.b - lower.b) * t).toInt()
        return Color.rgb(r, g, b)
    }

    private val vario1Ramp = listOf(
        ColorRamp(0, 0xC4, 0x80, 0x1E),
        ColorRamp(100, 0xA0, 0xA0, 0xA0),
        ColorRamp(200, 0x1E, 0xF1, 0x73)
    )

    private val vario2Ramp = listOf(
        ColorRamp(0, 0x00, 0x00, 0x80),   // navy (largest sink)
        ColorRamp(50, 0x00, 0x00, 0xFF),  // blue
        ColorRamp(85, 0x00, 0xFF, 0xFF),  // cyan
        ColorRamp(100, 0xFF, 0xFF, 0x00), // yellow (zero)
        ColorRamp(130, 0xFF, 0xA5, 0x00), // orange
        ColorRamp(160, 0xFF, 0x00, 0x00), // red
        ColorRamp(200, 0x80, 0x00, 0x80)  // purple (largest lift)
    )

    private val varioEinkRamp = listOf(
        ColorRamp(0, 0x00, 0x00, 0x00),
        ColorRamp(200, 0x80, 0x80, 0x80)
    )

    private val altitudeRamp = listOf(
        ColorRamp(0, 0xFF, 0x00, 0x00),
        ColorRamp(50, 0xFF, 0xFF, 0x00),
        ColorRamp(100, 0x00, 0xFF, 0x00),
        ColorRamp(150, 0x00, 0xFF, 0xFF),
        ColorRamp(200, 0x00, 0x00, 0xFF)
    )
}
