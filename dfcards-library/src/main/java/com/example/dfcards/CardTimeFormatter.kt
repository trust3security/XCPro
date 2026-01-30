package com.example.dfcards

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats wall-clock times for card display (UI-only).
 */
interface CardTimeFormatter {
    fun formatLocalTime(epochMillis: Long): Pair<String, String>
}

/**
 * System-backed formatter with injectable locale and zone for testability.
 */
class SystemCardTimeFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault()
) : CardTimeFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val secondsFormatter = DateTimeFormatter.ofPattern("ss", locale)

    override fun formatLocalTime(epochMillis: Long): Pair<String, String> {
        if (epochMillis <= 0L) return "--:--" to "--"
        val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
        return timeFormatter.format(zoned) to secondsFormatter.format(zoned)
    }
}
