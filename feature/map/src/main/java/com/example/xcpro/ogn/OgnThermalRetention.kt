package com.example.xcpro.ogn

const val OGN_THERMAL_RETENTION_MIN_HOURS = 1
const val OGN_THERMAL_RETENTION_ALL_DAY_HOURS = 24
const val OGN_THERMAL_RETENTION_DEFAULT_HOURS = OGN_THERMAL_RETENTION_ALL_DAY_HOURS

fun clampOgnThermalRetentionHours(hours: Int): Int =
    hours.coerceIn(OGN_THERMAL_RETENTION_MIN_HOURS, OGN_THERMAL_RETENTION_ALL_DAY_HOURS)

fun isOgnThermalRetentionAllDay(hours: Int): Boolean =
    clampOgnThermalRetentionHours(hours) == OGN_THERMAL_RETENTION_ALL_DAY_HOURS
