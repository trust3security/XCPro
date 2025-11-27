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
    // Accept compact DMS (DDMMSS.SN DDDMMSS.SE), colon/space-separated D:M:S with optional
    // hemisphere prefix/suffix, or signed decimal degrees. Returns [lon, lat].
    val normalized = coord.replace(',', ' ').trim()

    fun parseComponent(raw: String): Double? {
        var token = raw.trim().uppercase()

        // Hemisphere can be prefix or suffix
        var hemisphere: Char? = null
        if (token.isNotEmpty() && token[0] in "NSEW") {
            hemisphere = token[0]
            token = token.substring(1).trim()
        }
        if (token.isNotEmpty() && token[token.length - 1] in "NSEW") {
            hemisphere = token[token.length - 1]
            token = token.substring(0, token.length - 1).trim()
        }

        val hemiSign = when (hemisphere) {
            'S', 'W' -> -1.0
            'N', 'E' -> 1.0
            else -> null
        }

        // Colon/space separated D:M:S (seconds optional)
        Regex("^([+-]?\\d{1,3})(?::(\\d{1,2}))?(?::(\\d{1,2}(?:\\.\\d+)?))?").matchEntire(token)?.let { m ->
            val deg = m.groupValues[1].toDouble()
            val min = m.groupValues[2].toDoubleOrNull() ?: 0.0
            val sec = m.groupValues[3].toDoubleOrNull() ?: 0.0
            val value = kotlin.math.abs(deg) + min / 60.0 + sec / 3600.0
            val sign = hemiSign ?: kotlin.math.sign(deg).let { if (it == 0.0) 1.0 else it }
            return value * sign
        }

        // Compact digits DDMMSS[.s]
        Regex("^([+-]?)(\\d{4,7})(?:\\.(\\d+))?").matchEntire(token)?.let { m ->
            val signChar = m.groupValues[1]
            val digits = m.groupValues[2]
            val frac = m.groupValues[3]
            if (digits.length < 4) return null
            val secPart = digits.takeLast(2) + if (frac.isNotEmpty()) ".${frac}" else ""
            val minPart = digits.dropLast(2).takeLast(2)
            val degPart = digits.dropLast(4)
            val deg = degPart.toIntOrNull() ?: return null
            val min = minPart.toIntOrNull() ?: 0
            val sec = secPart.toDoubleOrNull() ?: 0.0
            val value = deg + min / 60.0 + sec / 3600.0
            val sign = hemiSign ?: if (signChar == "-") -1.0 else 1.0
            return value * sign
        }

        // Signed decimal degrees
        token.toDoubleOrNull()?.let { dec ->
            val sign = hemiSign ?: 1.0
            return dec * sign
        }

        return null
    }

    val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    fun consume(start: Int): Pair<Double?, Int> {
        var idx = start
        val buffer = mutableListOf<String>()
        var lastParsed: Pair<Double, Int>? = null
        while (idx < tokens.size && buffer.size < 3) {
            buffer.add(tokens[idx])
            val candidate = buffer.joinToString(" ")
            val parsed = parseComponent(candidate)
            if (parsed != null) {
                lastParsed = parsed to (idx + 1)
            }
            idx += 1
        }
        return lastParsed ?: (null to start)
    }

    val (lat, nextIdx) = consume(0)
    if (lat == null) return null
    val (lon, _) = consume(nextIdx)
    if (lon == null) return null

    return doubleArrayOf(lon, lat)
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
