package com.trust3.xcpro.igc.domain

import java.time.Instant
import java.time.ZoneOffset

/**
 * Maps a snapshot task declaration into deterministic C-record lines.
 */
class IgcTaskDeclarationMapper(
    private val formatter: IgcRecordFormatter = IgcRecordFormatter()
) {

    fun map(snapshot: IgcTaskDeclarationSnapshot?): List<String> {
        if (snapshot == null) return emptyList()
        if (snapshot.waypoints.size < 2) return emptyList()

        val cleanWaypoints = snapshot.waypoints.filter { waypoint ->
            waypoint.latitudeDegrees.isFinite() &&
                waypoint.longitudeDegrees.isFinite() &&
                waypoint.latitudeDegrees in -90.0..90.0 &&
                waypoint.longitudeDegrees in -180.0..180.0
        }
        if (cleanWaypoints.size < 2) return emptyList()

        val declarationTime = Instant.ofEpochMilli(snapshot.capturedAtUtcMs)
            .atOffset(ZoneOffset.UTC)
        val header = buildString {
            append("C")
            append(declarationTime.dayOfMonth.toString().padStart(2, '0'))
            append(declarationTime.monthValue.toString().padStart(2, '0'))
            append((declarationTime.year % 100).toString().padStart(2, '0'))
            append(declarationTime.hour.toString().padStart(2, '0'))
            append(declarationTime.minute.toString().padStart(2, '0'))
            append(declarationTime.second.toString().padStart(2, '0'))
            append(sanitize(snapshot.taskId).ifBlank { "TASK" })
        }

        val waypointLines = cleanWaypoints.map { waypoint ->
            val latitude = formatter.decimalDegreesToIgcLatitude(waypoint.latitudeDegrees)
            val longitude = formatter.decimalDegreesToIgcLongitude(waypoint.longitudeDegrees)
            "C$latitude$longitude${sanitize(waypoint.name).ifBlank { "WP" }}"
        }

        return listOf(header) + waypointLines
    }

    private fun sanitize(value: String): String {
        return value
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .take(MAX_NAME_LENGTH)
    }

    companion object {
        private const val MAX_NAME_LENGTH = 32
    }
}
