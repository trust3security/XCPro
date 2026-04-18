package com.trust3.xcpro.map.trail

import android.graphics.Color
import org.maplibre.android.style.expressions.Expression

internal object SnailTrailPalette {
    const val NUM_COLORS = 19

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
            TrailType.VARIO_2,
            TrailType.VARIO_2_DOTS,
            TrailType.VARIO_DOTS_AND_LINES,
            TrailType.VARIO_EINK,
            TrailType.VARIO_1,
            TrailType.VARIO_1_DOTS -> varioColors[index.coerceIn(0, varioColors.lastIndex)]
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

    private val varioColors = intArrayOf(
        Color.rgb(0x0B, 0x10, 0x26), // deep navy (max sink)
        Color.rgb(0x12, 0x26, 0x4A),
        Color.rgb(0x15, 0x3C, 0x66),
        Color.rgb(0x1B, 0x53, 0x82),
        Color.rgb(0x20, 0x6A, 0x9E),
        Color.rgb(0x2A, 0x81, 0xB9),
        Color.rgb(0x3A, 0x96, 0xCB),
        Color.rgb(0x56, 0xA9, 0xD6),
        Color.rgb(0x7C, 0xC0, 0xE2),
        Color.rgb(0xFF, 0xF4, 0xB0), // zero lift
        Color.rgb(0xFF, 0xE7, 0x81),
        Color.rgb(0xFF, 0xD2, 0x4F),
        Color.rgb(0xFF, 0xB5, 0x32),
        Color.rgb(0xFF, 0x98, 0x28),
        Color.rgb(0xFF, 0x7A, 0x1E),
        Color.rgb(0xF8, 0x5B, 0x2B),
        Color.rgb(0xE3, 0x3B, 0x4E),
        Color.rgb(0xB1, 0x2C, 0x82),
        Color.rgb(0x6D, 0x1A, 0x9C) // strong lift (12+ kts)
    )

    private val altitudeRamp = listOf(
        ColorRamp(0, 0xFF, 0x00, 0x00),
        ColorRamp(50, 0xFF, 0xFF, 0x00),
        ColorRamp(100, 0x00, 0xFF, 0x00),
        ColorRamp(150, 0x00, 0xFF, 0xFF),
        ColorRamp(200, 0x00, 0x00, 0xFF)
    )
}
