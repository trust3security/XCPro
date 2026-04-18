package com.example.ui1.screens.flightmgmt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.trust3.xcpro.common.waypoint.WaypointData
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "WaypointParser"

object WaypointParser {

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
        return null
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && (i == 0 || line[i-1] != '\\') -> {
                    insideQuotes = !insideQuotes
                    current.append(char)
                }
                char == ',' && !insideQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun parseWaypointLine(line: String): WaypointData? {
        return try {
            val parts = parseCsvLine(line)
            if (parts.size < 6) return null

            val name = parts[0].trim('"').trim()
            val code = parts[1].trim('"').trim()
            val country = parts[2].trim('"').trim()
            val latStr = parts[3].trim('"').trim()
            val lonStr = parts[4].trim('"').trim()
            val elevation = parts[5].trim('"').trim()

            val latitude = parseCoordinate(latStr, true) ?: return null
            val longitude = parseCoordinate(lonStr, false) ?: return null

            val description = if (parts.size > 9) parts[9].trim('"').trim() else ""

            WaypointData(
                name = name,
                code = code,
                country = country,
                latitude = latitude,
                longitude = longitude,
                elevation = elevation,
                style = 1,
                runwayDirection = null,
                runwayLength = null,
                frequency = null,
                description = description
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse waypoint line: $line", e)
            null
        }
    }

    fun parseWaypointsFromUri(context: Context, uri: Uri): List<WaypointData> {
        val waypoints = mutableListOf<WaypointData>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine()
                    var isFirstLine = true

                    while (line != null) {
                        if (isFirstLine) {
                            isFirstLine = false
                            line = reader.readLine()
                            continue
                        }

                        if (line.isNotBlank()) {
                            parseWaypointLine(line)?.let { waypoint ->
                                waypoints.add(waypoint)
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing waypoints from URI: $uri", e)
        }
        return waypoints
    }

    fun getWaypointCount(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.lineSequence().drop(1).count { it.isNotBlank() }
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error counting waypoints in URI: $uri", e)
            0
        }
    }
}
