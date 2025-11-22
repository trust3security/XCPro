package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern
import kotlin.math.*

private const val AIRSPACE_PARSE_TAG = "AirspaceParser"

fun parseAirspaceClasses(context: Context, files: List<Uri>): List<String> {
    val classes = mutableSetOf<String>()
    try {
        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.readText().lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("AC ")) {
                        val airspaceClass = trimmed.substring(3).trim()
                        if (airspaceClass.isNotEmpty()) {
                            classes.add(airspaceClass)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e(AIRSPACE_PARSE_TAG, "Error parsing airspace classes: ${e.message}")
    }
    return classes.toList().sorted()
}

fun parseOpenAirToGeoJson(openAirText: String, selectedClasses: Set<String>): String {
    val features = JSONArray()
    val lines = openAirText.lines()
    val regex = Pattern.compile("([A-Z]{2}) (.*)")

    var currentClass = ""
    var currentAirspaceLines = mutableListOf<String>()

    fun processCurrentAirspace() {
        if (currentClass.isNotEmpty() && (selectedClasses.isEmpty() || selectedClasses.contains(currentClass))) {
            val feature = parseSingleAirspace(currentAirspaceLines, currentClass)
            feature?.let { features.put(it) }
        }
        currentAirspaceLines = mutableListOf()
    }

    for (line in lines) {
        val matcher = regex.matcher(line)
        if (matcher.matches()) {
            val code = matcher.group(1)
            val content = matcher.group(2).trim()
            when (code) {
                "AC" -> {
                    processCurrentAirspace()
                    currentClass = content
                }
                else -> currentAirspaceLines.add(line)
            }
        } else if (line.isNotBlank()) {
            currentAirspaceLines.add(line)
        }
    }
    processCurrentAirspace()

    val geoJson = JSONObject()
    geoJson.put("type", "FeatureCollection")
    geoJson.put("features", features)
    return geoJson.toString()
}

private fun parseSingleAirspace(lines: List<String>, airspaceClass: String): JSONObject? {
    val coordinates = mutableListOf<DoubleArray>()
    var lowerAlt: String? = null
    var upperAlt: String? = null
    var name: String? = null

    lines.forEach { line ->
        when {
            line.startsWith("AL ") -> lowerAlt = line.substring(3).trim()
            line.startsWith("AH ") -> upperAlt = line.substring(3).trim()
            line.startsWith("AN ") -> name = line.substring(3).trim()
            line.startsWith("DP ") -> {
                parseCoordinate(line.substring(3).trim())?.let { coordinates.add(it) }
            }
            line.startsWith("DC ") -> {
                val parts = line.substring(3).trim().split(" ")
                if (parts.size >= 2) {
                    val radiusNm = parts[0].toDoubleOrNull() ?: return@forEach
                    val center = parseCoordinate(parts[1]) ?: return@forEach
                    val circlePoints = generateCirclePoints(center, radiusNm)
                    coordinates.addAll(circlePoints)
                }
            }
            line.startsWith("DA ") -> {
                val parts = line.substring(3).trim().split(" ")
                if (parts.size >= 4) {
                    val start = parseCoordinate(parts[0]) ?: return@forEach
                    val end = parseCoordinate(parts[1]) ?: return@forEach
                    val center = parseCoordinate(parts[2]) ?: return@forEach
                    val direction = parts[3]
                    val arcPoints = generateArcPoints(start, end, center, direction)
                    coordinates.addAll(arcPoints)
                }
            }
        }
    }

    if (coordinates.isEmpty()) return null

    val feature = JSONObject()
    feature.put("type", "Feature")
    feature.put("properties", JSONObject().apply {
        put("class", airspaceClass)
        name?.let { put("name", it) }
        lowerAlt?.let { put("lower_alt", it) }
        upperAlt?.let { put("upper_alt", it) }
    })
    feature.put("geometry", JSONObject().apply {
        put("type", "Polygon")
        put("coordinates", JSONArray().apply {
            put(JSONArray().apply {
                coordinates.forEach { coord ->
                    put(JSONArray().apply {
                        put(coord[0])
                        put(coord[1])
                    })
                }
                // close ring if needed
                if (!areCoordinatesClose(coordinates.first(), coordinates.last())) {
                    put(JSONArray().apply {
                        put(coordinates.first()[0])
                        put(coordinates.first()[1])
                    })
                }
            })
        })
    })

    return feature
}

fun validateOpenAirFile(fileContent: String): Pair<Boolean, String> {
    val requiredTokens = listOf("AC ", "DP ")
    val hasRequiredTokens = requiredTokens.all { token -> fileContent.contains(token) }
    val tooLarge = fileContent.length > 2_000_000

    return when {
        !hasRequiredTokens -> Pair(false, "Invalid OpenAir file: missing AC/DP blocks")
        tooLarge -> Pair(false, "File too large to process")
        else -> Pair(true, "OK")
    }
}

private fun dmsToDecimal(degrees: Int, minutes: Int, seconds: Double, direction: Char): Double {
    var decimal = degrees + minutes / 60.0 + seconds / 3600.0
    if (direction == 'S' || direction == 'W') decimal = -decimal
    return decimal
}

private fun parseCoordinate(coord: String): DoubleArray? {
    val pattern = Pattern.compile("(\\d{2,3})(\\d{2})(\\d{2}\\.\\d)([N|S]) (\\d{3})(\\d{2})(\\d{2}\\.\\d)([E|W])")
    val matcher = pattern.matcher(coord)
    return if (matcher.matches()) {
        val latDeg = matcher.group(1).toInt()
        val latMin = matcher.group(2).toInt()
        val latSec = matcher.group(3).toDouble()
        val latDir = matcher.group(4)[0]

        val lonDeg = matcher.group(5).toInt()
        val lonMin = matcher.group(6).toInt()
        val lonSec = matcher.group(7).toDouble()
        val lonDir = matcher.group(8)[0]

        val lat = dmsToDecimal(latDeg, latMin, latSec, latDir)
        val lon = dmsToDecimal(lonDeg, lonMin, lonSec, lonDir)
        doubleArrayOf(lon, lat)
    } else null
}

private fun areCoordinatesClose(coord1: DoubleArray, coord2: DoubleArray, threshold: Double = 0.0001): Boolean {
    return abs(coord1[0] - coord2[0]) < threshold && abs(coord1[1] - coord2[1]) < threshold
}

private fun generateCirclePoints(center: DoubleArray, radiusNm: Double, numPoints: Int = 120): List<DoubleArray> {
    val points = mutableListOf<DoubleArray>()
    val radiusDeg = radiusNm / 60.0
    for (i in 0..numPoints) {
        val angle = 2 * Math.PI * i / numPoints
        val dx = radiusDeg * cos(angle)
        val dy = radiusDeg * sin(angle)
        points.add(doubleArrayOf(center[0] + dx, center[1] + dy))
    }
    return points
}

private fun generateArcPoints(
    start: DoubleArray,
    end: DoubleArray,
    center: DoubleArray,
    direction: String,
    numPoints: Int = 120
): List<DoubleArray> {
    val startAngle = atan2(start[1] - center[1], start[0] - center[0])
    val endAngle = atan2(end[1] - center[1], end[0] - center[0])
    val deltaAngle = when (direction.uppercase()) {
        "CW" -> if (endAngle <= startAngle) endAngle - startAngle + 2 * Math.PI else endAngle - startAngle
        "CCW" -> if (endAngle >= startAngle) endAngle - startAngle - 2 * Math.PI else endAngle - startAngle
        else -> endAngle - startAngle
    }

    val points = mutableListOf<DoubleArray>()
    for (i in 0..numPoints) {
        val angle = startAngle + deltaAngle * i / numPoints
        val x = center[0] + (start[0] - center[0]) * cos(angle - startAngle) - (start[1] - center[1]) * sin(angle - startAngle)
        val y = center[1] + (start[0] - center[0]) * sin(angle - startAngle) + (start[1] - center[1]) * cos(angle - startAngle)
        points.add(doubleArrayOf(x, y))
    }
    return points
}
