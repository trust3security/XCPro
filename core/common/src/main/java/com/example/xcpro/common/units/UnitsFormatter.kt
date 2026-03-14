package com.example.xcpro.common.units

import kotlin.math.round

object UnitsFormatter {

    data class FormattedValue(
        val value: Double,
        val unitLabel: String,
        val text: String
    )

    fun altitude(
        altitude: AltitudeM,
        preferences: UnitsPreferences,
        decimals: Int = 0
    ): FormattedValue {
        val unit = preferences.altitude
        val converted = unit.fromSi(altitude)
        val rounded = converted.round(decimals)
        return FormattedValue(
            value = rounded,
            unitLabel = unit.abbreviation,
            text = "${rounded.format(decimals)} ${unit.abbreviation}"
        )
    }

    fun verticalSpeed(
        verticalSpeed: VerticalSpeedMs,
        preferences: UnitsPreferences,
        decimals: Int = 1,
        showSign: Boolean = true
    ): FormattedValue {
        val unit = preferences.verticalSpeed
        val converted = unit.fromSi(verticalSpeed)
        val rounded = converted.round(decimals)
        val sanitized = if (rounded == 0.0) 0.0 else rounded
        val sign = if (showSign && sanitized >= 0) "+" else ""
        return FormattedValue(
            value = sanitized,
            unitLabel = unit.abbreviation,
            text = "$sign${sanitized.format(decimals)} ${unit.abbreviation}"
        )
    }

    fun speed(
        speed: SpeedMs,
        preferences: UnitsPreferences,
        decimals: Int = 0
    ): FormattedValue {
        val unit = preferences.speed
        val converted = unit.fromSi(speed)
        val rounded = converted.round(decimals)
        return FormattedValue(
            value = rounded,
            unitLabel = unit.abbreviation,
            text = "${rounded.format(decimals)} ${unit.abbreviation}"
        )
    }

    fun distance(
        distance: DistanceM,
        preferences: UnitsPreferences,
        decimals: Int = 1
    ): FormattedValue {
        val unit = preferences.distance
        val converted = unit.fromSi(distance)
        val rounded = converted.round(decimals)
        return FormattedValue(
            value = rounded,
            unitLabel = unit.abbreviation,
            text = "${rounded.format(decimals)} ${unit.abbreviation}"
        )
    }

    fun pressure(
        pressure: PressureHpa,
        preferences: UnitsPreferences,
        decimals: Int = 0
    ): FormattedValue {
        val unit = preferences.pressure
        val converted = unit.fromSi(pressure)
        val rounded = converted.round(decimals)
        return FormattedValue(
            value = rounded,
            unitLabel = unit.abbreviation,
            text = "${rounded.format(decimals)} ${unit.abbreviation}"
        )
    }

    fun temperature(
        temperature: TemperatureC,
        preferences: UnitsPreferences,
        decimals: Int = 0
    ): FormattedValue {
        val unit = preferences.temperature
        val converted = unit.fromSi(temperature)
        val rounded = converted.round(decimals)
        val sign = if (rounded > 0) "+" else ""
        return FormattedValue(
            value = rounded,
            unitLabel = unit.abbreviation,
            text = "$sign${rounded.format(decimals)} ${unit.abbreviation}"
        )
    }

    private fun Double.round(decimals: Int): Double {
        if (decimals <= 0) return round(this).toDouble()
        val factor = Math.pow(10.0, decimals.toDouble())
        return round(this * factor) / factor
    }

    private fun Double.format(decimals: Int): String {
        return if (decimals <= 0) {
            this.toInt().toString()
        } else {
            "%.${decimals}f".format(this)
        }
    }
}
