package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

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
    val directiveRegex = Pattern.compile("^([A-Za-z]{2})\\s+(.*)$")

    var currentClass = ""
    var currentAirspaceLines = mutableListOf<String>()

    fun processCurrentAirspace() {
        if (currentClass.isNotEmpty() && (selectedClasses.isEmpty() || selectedClasses.contains(currentClass))) {
            val feature = parseSingleAirspace(currentAirspaceLines, currentClass)
            feature?.let { features.put(it) }
        }
        currentAirspaceLines = mutableListOf()
    }

    for (rawLine in lines) {
        val line = rawLine.trim()
        if (line.isBlank()) continue

        val matcher = directiveRegex.matcher(line)
        if (matcher.matches()) {
            val code = matcher.group(1)?.uppercase() ?: continue
            val content = matcher.group(2)?.trim().orEmpty()
            when (code) {
                "AC" -> {
                    processCurrentAirspace()
                    currentClass = content
                }
                else -> currentAirspaceLines.add("$code $content")
            }
        } else {
            currentAirspaceLines.add(line)
        }
    }
    processCurrentAirspace()

    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}

private fun parseSingleAirspace(lines: List<String>, airspaceClass: String): JSONObject? {
    val coordinates = mutableListOf<DoubleArray>()
    var lowerAlt: String? = null
    var upperAlt: String? = null
    var name: String? = null
    var arcCenter: DoubleArray? = null
    var arcDirection: String = "CW"

    lines.forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach
        val upper = line.uppercase()

        when {
            upper.startsWith("AL ") -> lowerAlt = line.substring(3).trim()
            upper.startsWith("AH ") -> upperAlt = line.substring(3).trim()
            upper.startsWith("AN ") -> name = line.substring(3).trim()
            upper.startsWith("V ") -> {
                val payload = line.substring(2).trim()
                when {
                    payload.startsWith("X=", ignoreCase = true) -> {
                        arcCenter = parseCoordinate(payload.substringAfter("=").trim()) ?: arcCenter
                    }
                    payload.startsWith("D=", ignoreCase = true) -> {
                        val directionToken = payload.substringAfter("=").trim().uppercase()
                        arcDirection = when (directionToken) {
                            "+", "CW" -> "CW"
                            "-", "CCW" -> "CCW"
                            else -> arcDirection
                        }
                    }
                }
            }
            upper.startsWith("DP ") -> {
                parseCoordinate(line.substring(3).trim())?.let { coordinates.add(it) }
            }
            upper.startsWith("DC ") -> {
                val payload = line.substring(3).trim()
                val firstSpace = payload.indexOf(' ')
                val radiusToken = if (firstSpace >= 0) payload.substring(0, firstSpace) else payload
                val radiusNm = radiusToken.toDoubleOrNull() ?: return@forEach
                val explicitCenter = if (firstSpace >= 0) {
                    parseCoordinate(payload.substring(firstSpace + 1).trim())
                } else {
                    null
                }
                val center = explicitCenter ?: arcCenter ?: return@forEach
                coordinates.addAll(generateCirclePoints(center, radiusNm))
            }
            upper.startsWith("DA ") -> {
                val payload = line.substring(3).trim()
                val arcPoints = parseArcFromEndpointLine(payload, arcCenter, arcDirection)
                    ?: parseLegacyDaPayload(payload)
                arcPoints?.let { coordinates.addAll(it) }
            }
            upper.startsWith("DB ") -> {
                val payload = line.substring(3).trim()
                val arcPoints = parseArcFromEndpointLine(payload, arcCenter, arcDirection)
                arcPoints?.let { coordinates.addAll(it) }
            }
        }
    }

    if (coordinates.isEmpty()) return null

    return JSONObject().apply {
        put("type", "Feature")
        put("properties", JSONObject().apply {
            put("class", airspaceClass)
            name?.let { put("name", it) }
            lowerAlt?.let { put("lower_alt", it) }
            upperAlt?.let { put("upper_alt", it) }
        })
        put("geometry", JSONObject().apply {
            put("type", "Polygon")
            put("coordinates", JSONArray().apply {
                put(JSONArray().apply {
                    coordinates.forEach { coord ->
                        put(JSONArray().apply {
                            put(coord[0])
                            put(coord[1])
                        })
                    }
                    if (!areCoordinatesClose(coordinates.first(), coordinates.last())) {
                        put(JSONArray().apply {
                            put(coordinates.first()[0])
                            put(coordinates.first()[1])
                        })
                    }
                })
            })
        })
    }
}

private fun parseArcFromEndpointLine(
    payload: String,
    centerFallback: DoubleArray?,
    directionFallback: String
): List<DoubleArray>? {
    val (start, end, explicitDirection) = parseArcEndpoints(payload) ?: return null
    val center = centerFallback ?: return null
    val direction = explicitDirection ?: directionFallback
    return generateArcPoints(start, end, center, direction)
}

private fun parseArcEndpoints(payload: String): Triple<DoubleArray, DoubleArray, String?>? {
    val commaIndex = payload.indexOf(',')
    if (commaIndex < 0) return null

    val first = payload.substring(0, commaIndex).trim()
    var second = payload.substring(commaIndex + 1).trim()
    var direction: String? = null

    val secondTokens = second.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (secondTokens.isNotEmpty()) {
        val trailing = secondTokens.last().uppercase()
        val trailingDirection = when (trailing) {
            "+", "CW" -> "CW"
            "-", "CCW" -> "CCW"
            else -> null
        }
        if (trailingDirection != null) {
            direction = trailingDirection
            second = secondTokens.dropLast(1).joinToString(" ")
        }
    }

    val start = parseCoordinate(first) ?: return null
    val end = parseCoordinate(second) ?: return null
    return Triple(start, end, direction)
}

private fun parseLegacyDaPayload(payload: String): List<DoubleArray>? {
    val parts = payload.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size < 4) return null

    val start = parseCoordinate(parts[0]) ?: return null
    val end = parseCoordinate(parts[1]) ?: return null
    val center = parseCoordinate(parts[2]) ?: return null
    val direction = parts[3]
    return generateArcPoints(start, end, center, direction)
}

fun validateOpenAirFile(fileContent: String): Pair<Boolean, String> {
    val hasClassDirective = Regex("(?m)^\\s*AC\\s+").containsMatchIn(fileContent)
    val hasGeometryDirective = Regex("(?m)^\\s*(DP|DC|DA|DB)\\s+").containsMatchIn(fileContent)
    val tooLarge = fileContent.length > 2_000_000

    return when {
        !hasClassDirective -> Pair(false, "Invalid OpenAir file: missing AC blocks")
        !hasGeometryDirective -> Pair(false, "Invalid OpenAir file: missing geometry blocks (DP/DC/DA/DB)")
        tooLarge -> Pair(false, "File too large to process")
        else -> Pair(true, "OK")
    }
}

private fun parseCoordinate(coord: String): DoubleArray? {
    // Accept compact DMS (DDMMSS.SN DDDMMSS.SE), compact decimal minutes
    // (DDMM.MMMN DDDMM.MMME), colon/space-separated D:M:S with optional
    // hemisphere prefix/suffix, or signed decimal degrees. Returns [lon, lat].
    val normalized = coord.replace(',', ' ').trim()

    fun parseComponent(raw: String): Double? {
        var token = raw.trim().uppercase()

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

        Regex("^([+-]?\\d{1,3})(?::(\\d{1,2}))?(?::(\\d{1,2}(?:\\.\\d+)?))?$")
            .matchEntire(token)
            ?.let { match ->
                val deg = match.groupValues[1].toDouble()
                val min = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val sec = match.groupValues[3].toDoubleOrNull() ?: 0.0
                val value = abs(deg) + min / 60.0 + sec / 3600.0
                val signValue = hemiSign ?: sign(deg).let { if (it == 0.0) 1.0 else it }
                return value * signValue
            }

        Regex("^([+-]?)(\\d{3,5})\\.(\\d+)$")
            .matchEntire(token)
            ?.let { match ->
                val signChar = match.groupValues[1]
                val digitPart = match.groupValues[2]
                val fracPart = match.groupValues[3]
                if (digitPart.length < 3) return null
                val degreePart = digitPart.dropLast(2)
                if (degreePart.isEmpty()) return null
                val degree = degreePart.toIntOrNull() ?: return null
                val minute = "${digitPart.takeLast(2)}.$fracPart".toDoubleOrNull() ?: return null
                val value = degree + minute / 60.0
                val signValue = hemiSign ?: if (signChar == "-") -1.0 else 1.0
                return value * signValue
            }

        Regex("^([+-]?)(\\d{4,7})(?:\\.(\\d+))?$")
            .matchEntire(token)
            ?.let { match ->
                val signChar = match.groupValues[1]
                val digits = match.groupValues[2]
                val frac = match.groupValues[3]
                if (digits.length < 4) return null
                val secPart = digits.takeLast(2) + if (frac.isNotEmpty()) ".${frac}" else ""
                val minPart = digits.dropLast(2).takeLast(2)
                val degPart = digits.dropLast(4)
                val deg = degPart.toIntOrNull() ?: return null
                val min = minPart.toIntOrNull() ?: 0
                val sec = secPart.toDoubleOrNull() ?: 0.0
                val value = deg + min / 60.0 + sec / 3600.0
                val signValue = hemiSign ?: if (signChar == "-") -1.0 else 1.0
                return value * signValue
            }

        token.toDoubleOrNull()?.let { dec ->
            val signValue = hemiSign ?: 1.0
            return dec * signValue
        }

        return null
    }

    val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null

    fun consume(start: Int): Pair<Double?, Int> {
        var index = start
        val buffer = mutableListOf<String>()
        var lastParsed: Pair<Double, Int>? = null

        while (index < tokens.size && buffer.size < 3) {
            buffer.add(tokens[index])
            val parsed = parseComponent(buffer.joinToString(" "))
            if (parsed != null) {
                lastParsed = parsed to (index + 1)
            }
            index += 1
        }

        return lastParsed ?: (null to start)
    }

    val (lat, nextIndex) = consume(0)
    if (lat == null) return null
    val (lon, _) = consume(nextIndex)
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
        val angle = 2 * PI * i / numPoints
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
        "CW" -> if (endAngle <= startAngle) endAngle - startAngle + 2 * PI else endAngle - startAngle
        "CCW" -> if (endAngle >= startAngle) endAngle - startAngle - 2 * PI else endAngle - startAngle
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
