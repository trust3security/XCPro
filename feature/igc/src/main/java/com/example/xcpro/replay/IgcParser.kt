package com.example.xcpro.replay

import com.example.xcpro.core.time.Clock
import java.io.BufferedReader
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject

data class IgcPoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val gpsAltitude: Double,
    val pressureAltitude: Double?,
    val indicatedAirspeedKmh: Double? = null,
    val trueAirspeedKmh: Double? = null
)

data class IgcMetadata(
    val qnhHpa: Double?
)

data class IgcLog(
    val metadata: IgcMetadata,
    val points: List<IgcPoint>
)

class IgcParser @Inject constructor(
    private val clock: Clock
) {

    fun parse(stream: InputStream): IgcLog =
        stream.bufferedReader().use { reader -> parse(reader) }

    private fun parse(reader: BufferedReader): IgcLog {
        var currentDate = defaultDateUtc()
        val points = mutableListOf<IgcPoint>()
        var qnhHpa: Double? = null
        var lastTimestampMillis: Long? = null
        var extensions: List<IgcExtension> = emptyList()

        reader.forEachLine { rawLine ->
            val line = rawLine
            when {
                line.startsWith("HFDTE") && line.length >= 11 -> {
                    currentDate = parseDate(line.substring(5, 11)) ?: currentDate
                    lastTimestampMillis = null
                }
                line.startsWith("HFQNH") -> {
                    parseNumericValue(line.substring(5))?.let { qnhHpa = it }
                }
                line.startsWith("I") -> {
                    parseExtensions(line)?.let { parsed ->
                        extensions = parsed
                    }
                }
                line.startsWith("B") && line.length >= 35 -> {
                    val time = parseBTime(line) ?: return@forEachLine
                    val candidateMillis = currentDate.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
                    val previousMillis = lastTimestampMillis
                    val effectiveMillis = if (previousMillis != null && candidateMillis < previousMillis) {
                        val delta = previousMillis - candidateMillis
                        // IGC B-record timestamps are hhmmss with 1s granularity. Flights can span UTC
                        // midnight; many loggers keep emitting B records with time-of-day reset but
                        // do not repeat HFDTE. Detect a large backwards jump and roll the date forward.
                        if (delta >= MIDNIGHT_ROLLOVER_THRESHOLD.toMillis()) {
                            currentDate = currentDate.plusDays(1)
                            currentDate.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
                        } else {
                            // Small backwards jumps are likely bad/out-of-order samples; skip them
                            // to keep downstream estimators monotonic.
                            return@forEachLine
                        }
                    } else {
                        candidateMillis
                    }

                    parseBRecord(line, effectiveMillis, extensions)?.let { point ->
                        points += point
                        lastTimestampMillis = effectiveMillis
                    }
                }
            }
        }

        return IgcLog(
            metadata = IgcMetadata(qnhHpa = qnhHpa),
            points = points
        )
    }

    private fun defaultDateUtc(): LocalDate =
        Instant.ofEpochMilli(clock.nowWallMs())
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

    private fun parseDate(value: String): LocalDate? {
        return try {
            val day = value.substring(0, 2).toInt()
            val month = value.substring(2, 4).toInt()
            val year = 2000 + value.substring(4, 6).toInt()
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseBTime(line: String): LocalTime? {
        return try {
            LocalTime.of(
                line.substring(1, 3).toInt(),
                line.substring(3, 5).toInt(),
                line.substring(5, 7).toInt()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseBRecord(
        line: String,
        timestampMillis: Long,
        extensions: List<IgcExtension>
    ): IgcPoint? {
        return try {
            val lat = parseCoordinate(line.substring(7, 14), line[14] == 'N', isLatitude = true)
            val lon = parseCoordinate(line.substring(15, 23), line[23] == 'E', isLatitude = false)
            val pressureAltitude = line.substring(25, 30).toIntOrNull()
            val gpsAltitude = line.substring(30, 35).toIntOrNull()
            val extensionValues = parseExtensionValues(line, extensions)

            IgcPoint(
                timestampMillis = timestampMillis,
                latitude = lat,
                longitude = lon,
                gpsAltitude = gpsAltitude?.toDouble() ?: 0.0,
                pressureAltitude = pressureAltitude?.toDouble(),
                indicatedAirspeedKmh = extensionValues.indicatedAirspeedKmh,
                trueAirspeedKmh = extensionValues.trueAirspeedKmh
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCoordinate(value: String, positiveHemisphere: Boolean, isLatitude: Boolean): Double {
        val degreesLength = if (isLatitude) 2 else 3
        val degrees = value.substring(0, degreesLength).toInt()
        val minutes = value.substring(degreesLength, degreesLength + 2).toInt()
        val thousandths = value.substring(degreesLength + 2).toInt()

        val totalMinutes = minutes + thousandths / 1000.0
        var coordinate = degrees + totalMinutes / 60.0
        if (!positiveHemisphere) {
            coordinate = -coordinate
        }
        return coordinate
    }

    private fun parseNumericValue(raw: String): Double? {
        val match = Regex("(-?\\d+(\\.\\d+)?)").find(raw) ?: return null
        return match.value.toDoubleOrNull()
    }

    private val MIDNIGHT_ROLLOVER_THRESHOLD: Duration = Duration.ofHours(6)

    private data class IgcExtension(
        val start: Int,
        val end: Int,
        val code: String
    )

    private data class IgcExtensionValues(
        val indicatedAirspeedKmh: Double?,
        val trueAirspeedKmh: Double?
    )

    private fun parseExtensions(line: String): List<IgcExtension>? {
        if (!line.startsWith("I") || line.length < 3) return null
        val count = line.substring(1, 3).toIntOrNull() ?: return null
        var index = 3
        val parsed = mutableListOf<IgcExtension>()
        var expectedStart = MIN_EXTENSION_START
        repeat(count) {
            if (index + 7 > line.length) return null
            val start = line.substring(index, index + 2).toIntOrNull() ?: return null
            index += 2
            val end = line.substring(index, index + 2).toIntOrNull() ?: return null
            index += 2
            val code = line.substring(index, index + 3)
            index += 3
            if (start < MIN_EXTENSION_START || end < start || start != expectedStart) return null
            if (!code.all { it.isLetterOrDigit() }) return null
            parsed += IgcExtension(start = start, end = end, code = code)
            expectedStart = end + 1
        }
        if (index != line.length) return null
        return parsed
    }

    private fun parseExtensionValues(line: String, extensions: List<IgcExtension>): IgcExtensionValues {
        var iasKmh: Double? = null
        var tasKmh: Double? = null
        extensions.forEach { extension ->
            if (extension.end > line.length) return@forEach
            when (extension.code) {
                "IAS" -> parseExtensionValueN(line, extension, EXTENSION_VALUE_DIGITS)?.let {
                    iasKmh = it.toDouble()
                }
                "TAS" -> parseExtensionValueN(line, extension, EXTENSION_VALUE_DIGITS)?.let {
                    tasKmh = it.toDouble()
                }
            }
        }
        return IgcExtensionValues(
            indicatedAirspeedKmh = iasKmh,
            trueAirspeedKmh = tasKmh
        )
    }

    private fun parseExtensionValueN(line: String, extension: IgcExtension, digits: Int): Int? {
        val startIndex = extension.start - 1
        if (startIndex < 0) return null
        val endIndex = extension.end.coerceAtMost(line.length)
        if (startIndex >= endIndex) return null
        val length = digits.coerceAtMost(endIndex - startIndex)
        val raw = line.substring(startIndex, startIndex + length)
        if (raw.any { !it.isDigit() }) return null
        return raw.toIntOrNull()
    }

    private companion object {
        private const val MIN_EXTENSION_START = 36
        private const val EXTENSION_VALUE_DIGITS = 3
    }
}
