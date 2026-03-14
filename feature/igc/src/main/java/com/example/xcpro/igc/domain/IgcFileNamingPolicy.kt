package com.example.xcpro.igc.domain

import java.time.LocalDate
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
        val resolvedUtcDate = IgcSessionFileIdentityCodec.resolveUtcDate(
            firstValidFixWallTimeMs = request.firstValidFixWallTimeMs,
            sessionStartWallTimeMs = request.sessionStartWallTimeMs
        )
        val utcDate = resolvedUtcDate.utcDate
        val datePrefix = DATE_FORMATTER.format(utcDate)
        val filePrefix = IgcSessionFileIdentityCodec.buildSessionPrefix(
            utcDate = utcDate,
            manufacturerId = request.manufacturerId,
            sessionSerial = request.sessionSerial
        )
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
            append(filePrefix)
            append('-')
            append(nextIndex.toString().padStart(2, '0'))
            append(".IGC")
        }
        return Result.Success(
            fileName = fileName,
            utcDate = utcDate,
            dayFlightIndex = nextIndex,
            usedFallbackDate = resolvedUtcDate.usedFallbackDate
        )
    }

    private fun parseFlightIndex(fileName: String, expectedDatePrefix: String): Int? {
        val match = FILE_NAME_REGEX.matchEntire(fileName) ?: return null
        val datePart = match.groupValues[1]
        if (datePart != expectedDatePrefix) return null
        return match.groupValues[4].toIntOrNull()
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val FILE_NAME_REGEX =
            Regex("""^(\d{4}-\d{2}-\d{2})-([A-Z0-9]{3})-([0-9]{6})-(\d{2})\.IGC$""")
    }
}
