package com.trust3.xcpro.igc.data

import com.trust3.xcpro.igc.domain.IgcRecoveryMetadata
import com.trust3.xcpro.igc.domain.IgcSecuritySignatureProfile
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

internal object IgcStagedRecoveryMetadataParser {

    fun parse(lines: List<String>): IgcRecoveryMetadata? {
        if (lines.isEmpty()) return null
        val aLine = lines.firstOrNull { it.startsWith("A") } ?: return null
        if (aLine.length < 10) return null
        val manufacturerId = aLine.substring(1, 4)
        val sessionSerial = aLine.substring(4, 10)
        val startDate = lines.firstOrNull(::isSessionHeaderDateLine)
            ?.let(::parseSessionHeaderDate)
        val firstB = lines.firstOrNull { it.startsWith("B") }
        val firstFixMs = startDate?.let { date ->
            parseFirstBWallTime(firstB, date)
        }
        return IgcRecoveryMetadata(
            manufacturerId = manufacturerId,
            sessionSerial = sessionSerial,
            sessionStartWallTimeMs = startDate?.toEpochMillisAtUtcStartOfDay() ?: 0L,
            firstValidFixWallTimeMs = firstFixMs,
            signatureProfile = signatureProfileForManufacturer(manufacturerId)
        )
    }

    private fun parseSessionHeaderDate(line: String): LocalDate? {
        val raw = when {
            line.startsWith("HFDTEDATE:") -> line.removePrefix("HFDTEDATE:")
            line.startsWith("HFDTE") -> line.removePrefix("HFDTE")
            else -> return null
        }
        val digits = raw.takeWhile { it.isDigit() }.take(6)
        if (digits.length != 6) return null
        val day = digits.substring(0, 2).toIntOrNull() ?: return null
        val month = digits.substring(2, 4).toIntOrNull() ?: return null
        val year = 2000 + (digits.substring(4, 6).toIntOrNull() ?: return null)
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun parseFirstBWallTime(line: String?, date: LocalDate): Long? {
        if (line == null || line.length < 7) return null
        val hour = line.substring(1, 3).toIntOrNull() ?: return null
        val minute = line.substring(3, 5).toIntOrNull() ?: return null
        val second = line.substring(5, 7).toIntOrNull() ?: return null
        return runCatching {
            date.atTime(LocalTime.of(hour, minute, second))
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        }.getOrNull()
    }

    private fun isSessionHeaderDateLine(line: String): Boolean {
        return line.startsWith("HFDTEDATE:") || line.startsWith("HFDTE")
    }

    private fun signatureProfileForManufacturer(manufacturerId: String): IgcSecuritySignatureProfile {
        return if (manufacturerId.equals("XCS", ignoreCase = true)) {
            IgcSecuritySignatureProfile.XCS
        } else {
            IgcSecuritySignatureProfile.NONE
        }
    }

    private fun LocalDate.toEpochMillisAtUtcStartOfDay(): Long =
        atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}
