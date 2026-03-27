package com.example.ui1

import com.example.xcpro.common.units.UnitsFormatter
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.common.units.VerticalSpeedUnit

fun stripUnit(formatted: UnitsFormatter.FormattedValue): String =
    formatted.text.replace(formatted.unitLabel, "").trim()

fun buildVarioDialConfig(unitsPreferences: UnitsPreferences): VarioDialConfig {
    val maxSi = 5f
    val unit = unitsPreferences.verticalSpeed
    val stepUser = when (unit) {
        VerticalSpeedUnit.METERS_PER_SECOND -> 1.0
        VerticalSpeedUnit.KNOTS -> 2.0
        VerticalSpeedUnit.FEET_PER_MINUTE -> 200.0
    }
    val maxUserRaw = unit.fromSi(VerticalSpeedMs(maxSi.toDouble()))
    val maxUserRounded = when (unit) {
        VerticalSpeedUnit.METERS_PER_SECOND -> maxUserRaw
        else -> kotlin.math.round(maxUserRaw / stepUser) * stepUser
    }.coerceAtLeast(stepUser)
    val labels = buildList {
        var value = -maxUserRounded
        while (value <= maxUserRounded + 1e-6) {
            val valueSi = unit.toSi(value).value.toFloat().coerceIn(-maxSi, maxSi)
            add(VarioDialLabel(valueSi, formatVarioLabel(value)))
            value += stepUser
        }
    }
    return VarioDialConfig(
        maxValueSi = maxSi,
        labelValues = labels
    )
}

private fun formatVarioLabel(value: Double): String =
    kotlin.math.round(value).toInt().toString()
