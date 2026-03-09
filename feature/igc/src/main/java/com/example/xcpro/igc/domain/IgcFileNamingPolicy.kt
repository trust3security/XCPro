package com.example.xcpro.igc.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IgcFileNamingPolicy @Inject constructor() {

    data class Request(
        val firstValidFixWallTimeMs: Long?,
        val sessionStartWallTimeMs: Long,
        val manufacturerId: String,
        val sessionSerial: String,
        val existingFileNames: Set<String>
    )

    sealed interface Result {
        data class Success(
            val fileName: String,
            val utcDate: LocalDate,
            val dayFlightIndex: Int,
            val usedFallbackDate: Boolean
        ) : Result

        data class Failure(
            val code: FailureCode,
            val message: String
        ) : Result
    }

    enum class FailureCode {
        NAME_SPACE_EXHAUSTED
    }

    fun resolve(request: Request): Result {
        val (utcDate, usedFallbackDate) = resolveUtcDate(
            firstValidFixWallTimeMs = request.firstValidFixWallTimeMs,
            sessionStartWallTimeMs = request.sessionStartWallTimeMs
        )
        val datePrefix = DATE_FORMATTER.format(utcDate)
        val normalizedManufacturer = normalizeManufacturer(request.manufacturerId)
        val normalizedSerial = normalizeSerial(request.sessionSerial)
        val usedIndices = request.existingFileNames
            .asSequence()
            .mapNotNull { parseFlightIndex(it, datePrefix) }
            .toSet()

        val nextIndex = (1..99).firstOrNull { candidate -> !usedIndices.contains(candidate) }
            ?: return Result.Failure(
                code = FailureCode.NAME_SPACE_EXHAUSTED,
                message = "IGC_NAME_SPACE_EXHAUSTED for date=$datePrefix"
            )

        val fileName = buildString {
            append(datePrefix)
            append('-')
            append(normalizedManufacturer)
            append('-')
            append(normalizedSerial)
            append('-')
            append(nextIndex.toString().padStart(2, '0'))
            append(".IGC")
        }
        return Result.Success(
            fileName = fileName,
            utcDate = utcDate,
            dayFlightIndex = nextIndex,
            usedFallbackDate = usedFallbackDate
        )
    }

    private fun resolveUtcDate(
        firstValidFixWallTimeMs: Long?,
        sessionStartWallTimeMs: Long
    ): Pair<LocalDate, Boolean> {
        if (firstValidFixWallTimeMs != null && firstValidFixWallTimeMs >= 0L) {
            return Pair(firstValidFixWallTimeMs.toUtcDate(), false)
        }
        return Pair(sessionStartWallTimeMs.toUtcDate(), true)
    }

    private fun normalizeManufacturer(raw: String): String {
        val normalized = raw.trim().uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
        return when {
            normalized.length >= 3 -> normalized.take(3)
            normalized.isBlank() -> "XCP"
            else -> normalized.padEnd(3, 'X')
        }
    }

    private fun normalizeSerial(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length >= 6 -> digits.takeLast(6)
            digits.isBlank() -> "000000"
            else -> digits.padStart(6, '0')
        }
    }

    private fun parseFlightIndex(fileName: String, expectedDatePrefix: String): Int? {
        val match = FILE_NAME_REGEX.matchEntire(fileName) ?: return null
        val datePart = match.groupValues[1]
        if (datePart != expectedDatePrefix) return null
        return match.groupValues[4].toIntOrNull()
    }

    private fun Long.toUtcDate(): LocalDate =
        Instant.ofEpochMilli(this)
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val FILE_NAME_REGEX =
            Regex("""^(\d{4}-\d{2}-\d{2})-([A-Z0-9]{3})-([0-9]{6})-(\d{2})\.IGC$""")
    }
}
