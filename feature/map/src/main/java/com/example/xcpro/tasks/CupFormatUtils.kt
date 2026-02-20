package com.example.xcpro.tasks

import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.WaypointRole
import java.util.Locale
import kotlin.math.abs

internal data class CupWaypointRecord(
    val name: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val style: String
)

internal object CupFormatUtils {
    private val nonAlphaNumRegex = Regex("[^a-z0-9]+")

    fun parseCupWaypoints(cupContent: String): List<CupWaypointRecord> {
        val rows = cupContent
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") }
            .map(::parseCsvLine)
            .toList()

        return rows.mapNotNull { row ->
            if (isHeaderRow(row) || isMetadataRow(row)) {
                return@mapNotNull null
            }
            val lat = parseLatitude(row.getOrNull(3).orEmpty()) ?: return@mapNotNull null
            val lon = parseLongitude(row.getOrNull(4).orEmpty()) ?: return@mapNotNull null
            CupWaypointRecord(
                name = row.getOrNull(0).orEmpty().ifBlank { "Waypoint" },
                code = row.getOrNull(1).orEmpty(),
                latitude = lat,
                longitude = lon,
                description = row.getOrNull(10).orEmpty(),
                style = row.getOrNull(6).orEmpty()
            )
        }
    }

    fun inferTaskType(waypoints: List<CupWaypointRecord>): TaskType {
        return if (waypoints.any { it.code.trim().uppercase(Locale.US).startsWith("AAT") }) {
            TaskType.AAT
        } else {
            TaskType.RACING
        }
    }

    fun inferWaypointRole(index: Int, total: Int, code: String): WaypointRole {
        val normalized = code.trim().uppercase(Locale.US)
        return when {
            normalized.startsWith("START") || normalized == "S" -> WaypointRole.START
            normalized.startsWith("FINISH") || normalized == "F" -> WaypointRole.FINISH
            index == 0 -> WaypointRole.START
            index == total - 1 -> WaypointRole.FINISH
            else -> WaypointRole.TURNPOINT
        }
    }

    fun exportCode(role: WaypointRole, index: Int, taskType: TaskType): String {
        return when (role) {
            WaypointRole.START -> "START"
            WaypointRole.FINISH -> "FINISH"
            WaypointRole.TURNPOINT, WaypointRole.OPTIONAL ->
                if (taskType == TaskType.AAT) "AAT$index" else "TP$index"
        }
    }

    fun formatLatitude(lat: Double): String = formatCoordinate(
        value = lat,
        degreeWidth = 2,
        positiveHemisphere = 'N',
        negativeHemisphere = 'S'
    )

    fun formatLongitude(lon: Double): String = formatCoordinate(
        value = lon,
        degreeWidth = 3,
        positiveHemisphere = 'E',
        negativeHemisphere = 'W'
    )

    fun parseLatitude(raw: String): Double? = parseCoordinate(raw, positiveHemisphere = 'N', negativeHemisphere = 'S')

    fun parseLongitude(raw: String): Double? = parseCoordinate(raw, positiveHemisphere = 'E', negativeHemisphere = 'W')

    fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    cells += current.toString().trim()
                    current.setLength(0)
                }
                else -> current.append(ch)
            }
            index++
        }
        cells += current.toString().trim()
        return cells
    }

    fun stableWaypointId(prefix: String, index: Int, code: String, name: String): String {
        val fallback = "${prefix.lowercase(Locale.US)}_${index + 1}"
        val raw = listOf(prefix, code, name).filter { it.isNotBlank() }.joinToString("_")
        val normalized = raw
            .lowercase(Locale.US)
            .replace(nonAlphaNumRegex, "_")
            .trim('_')
        return normalized.ifBlank { fallback }.take(64)
    }

    fun stableTaskId(prefix: String, fileNameHint: String): String {
        val normalized = fileNameHint
            .lowercase(Locale.US)
            .replace(nonAlphaNumRegex, "_")
            .trim('_')
        return "${prefix.lowercase(Locale.US)}_${normalized.ifBlank { "imported" }}".take(64)
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        return row.getOrNull(0)?.equals("name", ignoreCase = true) == true &&
            row.getOrNull(1)?.equals("code", ignoreCase = true) == true
    }

    private fun isMetadataRow(row: List<String>): Boolean {
        return row.getOrNull(1)?.equals("TASK", ignoreCase = true) == true
    }

    private fun formatCoordinate(
        value: Double,
        degreeWidth: Int,
        positiveHemisphere: Char,
        negativeHemisphere: Char
    ): String {
        val absoluteValue = abs(value)
        val degrees = absoluteValue.toInt()
        val minutes = (absoluteValue - degrees) * 60.0
        val hemisphere = if (value >= 0.0) positiveHemisphere else negativeHemisphere
        return String.format(Locale.US, "%0${degreeWidth}d%06.3f%c", degrees, minutes, hemisphere)
    }

    private fun parseCoordinate(
        raw: String,
        positiveHemisphere: Char,
        negativeHemisphere: Char
    ): Double? {
        val token = raw.trim().trim('"').uppercase(Locale.US)
        if (token.length < 4) return null

        val hemisphere = token.last()
        if (hemisphere != positiveHemisphere && hemisphere != negativeHemisphere) return null

        val numberPart = token.dropLast(1)
        val dotIndex = numberPart.indexOf('.').let { if (it >= 0) it else numberPart.length }
        val degreeDigits = dotIndex - 2
        if (degreeDigits <= 0 || degreeDigits >= numberPart.length) return null

        val degrees = numberPart.substring(0, degreeDigits).toIntOrNull() ?: return null
        val minutes = numberPart.substring(degreeDigits).toDoubleOrNull() ?: return null
        if (minutes < 0.0 || minutes >= 60.0) return null

        val decimalDegrees = degrees + (minutes / 60.0)
        return if (hemisphere == negativeHemisphere) -decimalDegrees else decimalDegrees
    }
}
