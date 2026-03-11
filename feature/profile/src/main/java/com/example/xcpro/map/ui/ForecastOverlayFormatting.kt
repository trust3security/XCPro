package com.example.xcpro.map.ui

import com.example.xcpro.forecast.forecastRegionZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

fun formatForecastTime(timeUtcMs: Long, regionCode: String): String {
    val formatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    return formatter.format(
        Instant.ofEpochMilli(timeUtcMs).atZone(forecastRegionZoneId(regionCode))
    )
}

fun formatFollowTimeOffsetLabel(offsetMinutes: Int): String =
    when {
        offsetMinutes > 0 -> "Now +${offsetMinutes}m"
        offsetMinutes < 0 -> "Now ${offsetMinutes}m"
        else -> "Now"
    }
