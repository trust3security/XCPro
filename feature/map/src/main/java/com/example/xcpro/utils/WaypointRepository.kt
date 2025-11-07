package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.common.waypoint.WaypointData
import com.example.xcpro.common.waypoint.toSearchWaypoint
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

interface WaypointRepository {
    suspend fun search(query: String, limit: Int = 30): List<SearchWaypoint>
}

class FileWaypointRepo(
    private val all: List<WaypointData>
) : WaypointRepository {
    override suspend fun search(query: String, limit: Int): List<SearchWaypoint> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase(Locale.getDefault())
        return all.filter {
            it.name.lowercase(Locale.getDefault()).contains(q) ||
                it.code.lowercase(Locale.getDefault()).contains(q)
        }
            .take(limit)
            .map { it.toSearchWaypoint() }
    }
}

object WaypointParser {
    private const val TAG = "WaypointParser"

    // 🔹 Convert lat/long in DDMM.mmmN/S/E/W format to decimal degrees
    fun parseCoordinate(coord: String, isLat: Boolean): Double? {
        val cleanCoord = coord.trim('"').trim()
        val pattern = if (isLat) {
            Regex("(\\d{2})(\\d{2}\\.\\d{3})([NS])")
        } else {
            Regex("(\\d{3})(\\d{2}\\.\\d{3})([EW])")
        }
        val match = pattern.find(cleanCoord)
        if (match != null) {
            val degrees = match.groups[1]!!.value.toInt()
            val minutes = match.groups[2]!!.value.toDouble() / 60.0
            val direction = match.groups[3]!!.value
            val decimal = degrees + minutes
            return if (direction == "S" || direction == "W") -decimal else decimal
        }
        Log.w(TAG, "Failed to parse coordinate: $cleanCoord")
        return null
    }

    // 🔹 Handles CSV lines with quoted fields properly
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && (i == 0 || line[i - 1] != '\\') -> {
                    inQuotes = !inQuotes
                    current.append(char)
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    // 🔹 Entry point: parse full CUP file into list of WaypointData
    fun parseWaypointFile(context: Context, uri: Uri): List<WaypointData> {
        val waypoints = mutableListOf<WaypointData>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    var lineNumber = 0
                    var foundHeader = false

                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        val currentLine = line!!.trim()

                        // Skip empty lines and comments
                        if (currentLine.isEmpty() || currentLine.startsWith("*")) continue

                        // Find header
                        if (!foundHeader) {
                            if (currentLine.startsWith("name,code") || currentLine.startsWith("\"")) {
                                foundHeader = true
                                if (currentLine.startsWith("name,code")) continue
                            } else continue
                        }

                        // Stop at tasks section
                        if (currentLine.startsWith("-----Related Tasks-----") ||
                            currentLine.startsWith("Options,") ||
                            currentLine.startsWith("ObsZone=")) break

                        try {
                            val waypoint = parseWaypointLine(currentLine)
                            if (waypoint != null) waypoints.add(waypoint)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing line $lineNumber: $currentLine", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading waypoint file", e)
        }
        Log.d(TAG, "Parsed ${waypoints.size} waypoints")
        return waypoints
    }

    // 🔹 Parse one CSV line into a WaypointData object
    private fun parseWaypointLine(line: String): WaypointData? {
        if (line.isEmpty()) return null
        val fields = parseCsvLine(line)
        if (fields.size < 7) return null

        return try {
            val name = fields[0].removeSurrounding("\"")
            val code = fields.getOrNull(1)?.removeSurrounding("\"") ?: ""
            val country = fields.getOrNull(2)?.removeSurrounding("\"") ?: ""
            val latStr = fields.getOrNull(3)?.removeSurrounding("\"") ?: ""
            val lonStr = fields.getOrNull(4)?.removeSurrounding("\"") ?: ""
            val elevation = fields.getOrNull(5)?.removeSurrounding("\"") ?: ""
            val style = fields.getOrNull(6)?.toIntOrNull() ?: 1

            val latitude = parseCoordinate(latStr, true)
            val longitude = parseCoordinate(lonStr, false)

            if (latitude != null && longitude != null && name.isNotBlank()) {
                WaypointData(
                    name = name,
                    code = code,
                    country = country,
                    latitude = latitude,
                    longitude = longitude,
                    elevation = elevation,
                    style = style,
                    runwayDirection = fields.getOrNull(7)?.removeSurrounding("\"")?.takeIf { it.isNotBlank() },
                    runwayLength = fields.getOrNull(8)?.removeSurrounding("\"")?.takeIf { it.isNotBlank() },
                    frequency = fields.getOrNull(9)?.removeSurrounding("\"")?.takeIf { it.isNotBlank() },
                    description = fields.getOrNull(10)?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
                )
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Error creating waypoint from line: $line", e)
            null
        }
    }
}
