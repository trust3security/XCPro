package com.example.xcpro.replay

import java.io.BufferedReader
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

data class IgcPoint(
    val timestampMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val gpsAltitude: Double,
    val pressureAltitude: Double?
)

data class IgcMetadata(
    val qnhHpa: Double?
)

data class IgcLog(
    val metadata: IgcMetadata,
    val points: List<IgcPoint>
)

object IgcParser {

    fun parse(stream: InputStream): IgcLog {
        stream.bufferedReader().use { reader ->
            return parse(reader)
        }
    }

    private fun parse(reader: BufferedReader): IgcLog {
        var currentDate = LocalDate.now(ZoneOffset.UTC)
        val points = mutableListOf<IgcPoint>()
        var qnhHpa: Double? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("HFDTE") && line.length >= 11 -> {
                    currentDate = parseDate(line.substring(5, 11)) ?: currentDate
                }
                line.startsWith("HFQNH") -> {
                    parseNumericValue(line.substring(5))?.let { qnhHpa = it }
                }
                line.startsWith("B") && line.length >= 35 -> {
                    parseBRecord(line, currentDate)?.let { points += it }
                }
            }
        }

        return IgcLog(
            metadata = IgcMetadata(qnhHpa = qnhHpa),
            points = points
        )
    }

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

    private fun parseBRecord(line: String, date: LocalDate): IgcPoint? {
        return try {
            val time = LocalTime.of(
                line.substring(1, 3).toInt(),
                line.substring(3, 5).toInt(),
                line.substring(5, 7).toInt()
            )
            val timestampMillis = date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()

            val lat = parseCoordinate(line.substring(7, 14), line[14] == 'N', isLatitude = true)
            val lon = parseCoordinate(line.substring(15, 23), line[23] == 'E', isLatitude = false)
            val pressureAltitude = line.substring(25, 30).toIntOrNull()
            val gpsAltitude = line.substring(30, 35).toIntOrNull()

            IgcPoint(
                timestampMillis = timestampMillis,
                latitude = lat,
                longitude = lon,
                gpsAltitude = gpsAltitude?.toDouble() ?: 0.0,
                pressureAltitude = pressureAltitude?.toDouble()
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
}
