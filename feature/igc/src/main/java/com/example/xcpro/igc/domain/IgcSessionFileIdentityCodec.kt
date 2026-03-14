package com.example.xcpro.igc.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object IgcSessionFileIdentityCodec {

    data class ResolvedUtcDate(
        val utcDate: LocalDate,
        val usedFallbackDate: Boolean
    )

    fun resolveUtcDate(
        firstValidFixWallTimeMs: Long?,
        sessionStartWallTimeMs: Long
    ): ResolvedUtcDate {
        if (firstValidFixWallTimeMs != null && firstValidFixWallTimeMs >= 0L) {
            return ResolvedUtcDate(
                utcDate = utcDateFromWallTime(firstValidFixWallTimeMs),
                usedFallbackDate = false
            )
        }
        return ResolvedUtcDate(
            utcDate = utcDateFromWallTime(sessionStartWallTimeMs),
            usedFallbackDate = true
        )
    }

    fun buildSessionPrefix(
        utcDate: LocalDate,
        manufacturerId: String,
        sessionSerial: String
    ): String {
        return buildString {
            append(DATE_FORMATTER.format(utcDate))
            append('-')
            append(normalizeManufacturer(manufacturerId))
            append('-')
            append(normalizeSerial(sessionSerial))
        }
    }

    fun normalizeManufacturer(raw: String): String {
        val normalized = raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return when {
            normalized.length >= 3 -> normalized.take(3)
            normalized.isBlank() -> "XCP"
            else -> normalized.padEnd(3, 'X')
        }
    }

    fun normalizeSerial(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length >= 6 -> digits.takeLast(6)
            digits.isBlank() -> "000000"
            else -> digits.padStart(6, '0')
        }
    }

    fun utcDateFromWallTime(wallTimeMs: Long): LocalDate {
        return Instant.ofEpochMilli(wallTimeMs)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
    }

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
}
