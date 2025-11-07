package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONObject
import org.json.JSONArray
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.regex.Pattern
import kotlin.math.*

private const val TAG = "AirspaceUtils"

fun saveSelectedClasses(context: Context, selectedClasses: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val airspaceJson = json.optJSONObject("airspace") ?: JSONObject()
        val selectedClassesJson = JSONObject()
        selectedClasses.forEach { (key, value) -> selectedClassesJson.put(key, value) }
        airspaceJson.put("selectedClasses", selectedClassesJson)
        json.put("airspace", airspaceJson)
        file.writeText(json.toString(2))
        Log.d(TAG, "Saved selected classes: $selectedClasses")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving selected classes: ${e.message}")
    }
}

fun loadAirspaceFiles(context: Context): Pair<List<Uri>, MutableMap<String, Boolean>> {
    try {
        val file = File(context.filesDir, "configuration.json")
        if (!file.exists()) return Pair(emptyList(), mutableMapOf())
        val json = JSONObject(file.readText())
        val airspaceFiles = json.optJSONObject("airspace_files")?.optJSONObject("selected_files") ?: return Pair(emptyList(), mutableMapOf())
        val files = mutableListOf<Uri>()
        val checkedStates = mutableMapOf<String, Boolean>()
        airspaceFiles.keys().forEach { fileName ->
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                files.add(Uri.fromFile(file))
                checkedStates[fileName] = airspaceFiles.getBoolean(fileName)
            }
        }
        return Pair(files, checkedStates)
    } catch (e: Exception) {
        Log.e(TAG, "Error loading airspace files: ${e.message}")
        return Pair(emptyList(), mutableMapOf())
    }
}


fun copyFileToInternalStorage(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        cursor.getString(nameIndex) ?: "file_${System.currentTimeMillis()}.txt"
    } ?: "file_${System.currentTimeMillis()}.txt"

    val outputFile = File(context.filesDir, fileName)
    contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    } ?: throw IOException("Failed to open input stream for URI: $uri")

    return fileName
}

fun loadAndApplyAirspace(context: Context, map: MapLibreMap?) {
    val config = loadConfig(context)
    val airspaceFilesJson = config?.optJSONObject("airspace_files")?.optJSONObject("selected_files")

    // Load selected classes, or create defaults if none exist
    var selectedClasses = loadSelectedClasses(context)?.filter { it.value }?.keys?.toSet() ?: emptySet()

    // Auto-enable default classes if none are selected and we have airspace files
    if (selectedClasses.isEmpty() && airspaceFilesJson != null) {
        val airspaceFiles = mutableListOf<Uri>()
        airspaceFilesJson.keys().forEach { fileName ->
            if (airspaceFilesJson.optBoolean(fileName, false)) {
                airspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
            }
        }

        if (airspaceFiles.isNotEmpty()) {
            val availableClasses = parseAirspaceClasses(context, airspaceFiles)
            val defaultClasses = availableClasses.associateWith {
                it == "R" || it == "D" || it == "C" || it == "CTR"
            }
            saveSelectedClasses(context, defaultClasses)
            selectedClasses = defaultClasses.filter { it.value }.keys.toSet()
            Log.d(TAG, "Auto-enabled default airspace classes: $selectedClasses")
        }
    }
    if (airspaceFilesJson != null) {
        map?.let { mapInstance ->
            try {
                mapInstance.getStyle()?.let { style ->
                    style.removeLayer("airspace-layer")
                    style.removeSource("airspace-source")
                }
                val features = JSONArray()
                airspaceFilesJson.keys().forEach { fileName ->
                    if (airspaceFilesJson.optBoolean(fileName, false)) {
                        val airspaceFile = File(context.filesDir, fileName)
                        if (airspaceFile.exists()) {
                            val geoJsonData = parseOpenAirToGeoJson(airspaceFile.readText(), selectedClasses)
                            val geoJsonObject = JSONObject(geoJsonData)
                            val fileFeatures = geoJsonObject.optJSONArray("features") ?: JSONArray()
                            for (i in 0 until fileFeatures.length()) {
                                features.put(fileFeatures.getJSONObject(i))
                            }
                            Log.d(TAG, "Processed airspace file: $fileName")
                        } else {
                            Log.e(TAG, "Airspace file $fileName does not exist")
                        }
                    }
                }
                val geoJson = JSONObject().apply {
                    put("type", "FeatureCollection")
                    put("features", features)
                }.toString()
                mapInstance.getStyle()?.let { style ->
                    style.addSource(GeoJsonSource("airspace-source", geoJson))
                    val layer = LineLayer("airspace-layer", "airspace-source").withProperties(
                        PropertyFactory.lineColor(
                            Expression.match(
                                Expression.get("class"),
                                Expression.literal(android.graphics.Color.BLUE),
                                Expression.stop("R", Expression.literal(android.graphics.Color.BLUE)),
                                Expression.stop("A", Expression.literal(android.graphics.Color.RED)),
                                Expression.stop("C", Expression.literal(android.graphics.Color.GREEN)),
                                Expression.stop("D", Expression.literal(android.graphics.Color.YELLOW)),
                                Expression.stop("GP", Expression.literal(android.graphics.Color.MAGENTA))
                            )
                        ),
                        PropertyFactory.lineWidth(2f),
                        PropertyFactory.lineOpacity(0.7f)
                    )
                    style.addLayer(layer)
                    Log.d(TAG, "Airspace data loaded and added to map, filtered classes: $selectedClasses")
                } ?: Log.e(TAG, "Map style not loaded")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading airspace files: ${e.message}", e)
            }
        } ?: Log.e(TAG, "Map instance not available")
    } else {
        Log.d(TAG, "No airspace files selected")
    }
}


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
        Log.e(TAG, "Error parsing airspace classes: ${e.message}")
    }
    return classes.toList().sorted()
}




fun saveAirspaceFiles(context: Context, files: List<Uri>, checkedStates: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val airspaceFiles = JSONObject()
        val filesArray = JSONObject()
        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
            filesArray.put(fileName, checkedStates[fileName] ?: false)
        }
        airspaceFiles.put("selected_files", filesArray)
        json.put("airspace_files", airspaceFiles)
        file.writeText(json.toString(2))
        Log.d(TAG, "Saved airspace files: $filesArray")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving airspace files: ${e.message}")
    }
}


fun parseOpenAirToGeoJson(openAirText: String, selectedClasses: Set<String>): String {
    val features = JSONArray()
    var currentAirspace: JSONObject? = null
    val coordinates = mutableListOf<DoubleArray>()
    var airspaceName = ""
    var airspaceClass = ""
    var lowerAltitude = ""
    var upperAltitude = ""
    var airspaceType = ""
    var center: DoubleArray? = null
    var radiusNm: Double? = null
    var direction: String? = null
    var lastCoordinate: DoubleArray? = null

    fun dmsToDecimal(degrees: Int, minutes: Int, seconds: Double, direction: Char): Double {
        val decimal = degrees + minutes / 60.0 + seconds / 3600.0
        return if (direction == 'S' || direction == 'W') -decimal else decimal
    }

    fun parseCoordinate(coord: String): DoubleArray? {
        val pattern = Pattern.compile("(\\d+):(\\d+):(\\d+\\.?\\d*)\\s*([NS])\\s*(\\d+):(\\d+):(\\d+\\.?\\d*)\\s*([EW])")
        val matcher = pattern.matcher(coord)
        if (matcher.matches()) {
            val latDeg = matcher.group(1)!!.toInt()
            val latMin = matcher.group(2)!!.toInt()
            val latSec = matcher.group(3)!!.toDouble()
            val latDir = matcher.group(4)!![0]
            val lonDeg = matcher.group(5)!!.toInt()
            val lonMin = matcher.group(6)!!.toInt()
            val lonSec = matcher.group(7)!!.toDouble()
            val lonDir = matcher.group(8)!![0]
            val latitude = dmsToDecimal(latDeg, latMin, latSec, latDir)
            val longitude = dmsToDecimal(lonDeg, lonMin, lonSec, lonDir)
            return doubleArrayOf(longitude, latitude)
        }
        Log.e(TAG, "Failed to parse coordinate: $coord")
        return null
    }

    fun areCoordinatesClose(coord1: DoubleArray, coord2: DoubleArray, threshold: Double = 0.0001): Boolean {
        return abs(coord1[0] - coord2[0]) < threshold && abs(coord1[1] - coord2[1]) < threshold
    }

    fun generateCirclePoints(center: DoubleArray, radiusNm: Double, numPoints: Int = 120): List<DoubleArray> {
        val points = mutableListOf<DoubleArray>()
        val earthRadiusKm = 6371.0
        val radiusKm = radiusNm * 1.852
        val centerLat = center[1] * PI / 180
        val centerLon = center[0] * PI / 180
        for (i in 0 until numPoints) {
            val bearing = (i * 360.0 / numPoints) * PI / 180
            val lat = asin(sin(centerLat) * cos(radiusKm / earthRadiusKm) +
                    cos(centerLat) * sin(radiusKm / earthRadiusKm) * cos(bearing))
            val lon = centerLon + atan2(
                sin(bearing) * sin(radiusKm / earthRadiusKm) * cos(centerLat),
                cos(radiusKm / earthRadiusKm) - sin(centerLat) * sin(lat)
            )
            points.add(doubleArrayOf(lon * 180 / PI, lat * 180 / PI))
        }
        points.add(points[0]) // Close the polygon
        return points
    }

    fun generateArcPoints(start: DoubleArray, end: DoubleArray, center: DoubleArray, direction: String, numPoints: Int = 120): List<DoubleArray> {
        val points = mutableListOf<DoubleArray>()
        val earthRadiusKm = 6371.0
        val centerLat = center[1] * PI / 180
        val centerLon = center[0] * PI / 180
        val startLat = start[1] * PI / 180
        val startLon = start[0] * PI / 180
        val endLat = end[1] * PI / 180
        val endLon = end[0] * PI / 180

        val startBearing = atan2(
            sin(startLon - centerLon) * cos(startLat),
            cos(centerLat) * sin(startLat) - sin(centerLat) * cos(startLat) * cos(startLon - centerLon)
        ) * 180 / PI
        val endBearing = atan2(
            sin(endLon - centerLon) * cos(endLat),
            cos(centerLat) * sin(endLat) - sin(centerLat) * cos(endLat) * cos(endLon - centerLon)
        ) * 180 / PI

        val radiusKm = acos(sin(centerLat) * sin(startLat) +
                cos(centerLat) * cos(startLat) * cos(startLon - centerLon)) * earthRadiusKm

        val startAngle = if (startBearing < 0) startBearing + 360 else startBearing
        val endAngle = if (endBearing < 0) endBearing + 360 else endBearing
        val delta = if (direction == "+") {
            if (endAngle >= startAngle) endAngle - startAngle else endAngle + 360 - startAngle
        } else {
            if (startAngle >= endAngle) startAngle - endAngle else startAngle + 360 - endAngle
        }
        val step = delta / (numPoints - 1)

        for (i in 0 until numPoints) {
            val bearing = if (direction == "+") (startAngle + i * step) % 360 else (startAngle - i * step + 360) % 360
            val bearingRad = bearing * PI / 180
            val lat = asin(sin(centerLat) * cos(radiusKm / earthRadiusKm) +
                    cos(centerLat) * sin(radiusKm / earthRadiusKm) * cos(bearingRad))
            val lon = centerLon + atan2(
                sin(bearingRad) * sin(radiusKm / earthRadiusKm) * cos(centerLat),
                cos(radiusKm / earthRadiusKm) - sin(centerLat) * sin(lat)
            )
            points.add(doubleArrayOf(lon * 180 / PI, lat * 180 / PI))
        }
        return points
    }

    fun addAirspaceFeature() {
        if (currentAirspace != null && coordinates.isNotEmpty() && selectedClasses.contains(airspaceClass)) {
            val simplifiedCoordinates = mutableListOf<DoubleArray>()
            coordinates.forEach { coord ->
                if (simplifiedCoordinates.isEmpty() || !areCoordinatesClose(coord, simplifiedCoordinates.last())) {
                    simplifiedCoordinates.add(coord)
                } else {
                    Log.d(TAG, "Skipped duplicate coordinate for $airspaceName: $coord")
                }
            }
            if (simplifiedCoordinates.size > 2 && !areCoordinatesClose(simplifiedCoordinates.first(), simplifiedCoordinates.last())) {
                simplifiedCoordinates.add(simplifiedCoordinates.first())
            }
            if (simplifiedCoordinates.size > 2) {
                val geometry = JSONObject().apply {
                    put("type", "Polygon")
                    put("coordinates", JSONArray().apply {
                        put(JSONArray(simplifiedCoordinates.map { JSONArray(it) }))
                    })
                }
                currentAirspace!!.put("geometry", geometry)
                features.put(currentAirspace)
                Log.d(TAG, "Added airspace: $airspaceName, class: $airspaceClass, coordinates: ${simplifiedCoordinates.size}")
            } else {
                Log.w(TAG, "Skipped airspace $airspaceName: insufficient valid coordinates (${simplifiedCoordinates.size})")
            }
        }
        coordinates.clear()
        currentAirspace = null
        airspaceName = ""
        airspaceClass = ""
        lowerAltitude = ""
        upperAltitude = ""
        airspaceType = ""
        center = null
        radiusNm = null
        direction = null
        lastCoordinate = null
    }

    openAirText.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("*")) return@forEach

        when {
            trimmed.startsWith("AC ") -> {
                addAirspaceFeature()
                airspaceClass = trimmed.substring(3).trim()
                if (airspaceClass.isNotEmpty()) {
                    currentAirspace = JSONObject().apply {
                        put("type", "Feature")
                        put("properties", JSONObject().apply {
                            put("class", airspaceClass)
                            put("name", airspaceName)
                            put("lowerAltitude", lowerAltitude)
                            put("upperAltitude", upperAltitude)
                            put("type", airspaceType)
                        })
                    }
                    Log.d(TAG, "Started parsing airspace class: $airspaceClass")
                }
            }
            trimmed.startsWith("AN ") -> {
                airspaceName = trimmed.substring(3).trim()
                currentAirspace?.getJSONObject("properties")?.put("name", airspaceName)
            }
            trimmed.startsWith("AL ") -> {
                lowerAltitude = trimmed.substring(3).trim()
                currentAirspace?.getJSONObject("properties")?.put("lowerAltitude", lowerAltitude)
            }
            trimmed.startsWith("AH ") -> {
                upperAltitude = trimmed.substring(3).trim()
                currentAirspace?.getJSONObject("properties")?.put("upperAltitude", upperAltitude)
            }
            trimmed.startsWith("AY ") -> {
                airspaceType = trimmed.substring(3).trim()
                currentAirspace?.getJSONObject("properties")?.put("type", airspaceType)
            }
            trimmed.startsWith("DP ") -> {
                parseCoordinate(trimmed.substring(3).trim())?.let { coord ->
                    if (lastCoordinate == null || !areCoordinatesClose(coord, lastCoordinate!!)) {
                        coordinates.add(coord)
                        lastCoordinate = coord
                    } else {
                        Log.d(TAG, "Skipped duplicate DP coordinate for $airspaceName: $coord")
                    }
                }
            }
            trimmed.startsWith("DC ") -> {
                val radiusMatch = Pattern.compile("(\\d+\\.?\\d*)").matcher(trimmed.substring(3).trim())
                if (radiusMatch.find()) {
                    radiusNm = radiusMatch.group(1)!!.toDouble()
                    if (center != null) {
                        coordinates.addAll(generateCirclePoints(center!!, radiusNm!!))
                        lastCoordinate = coordinates.last()
                        Log.d(TAG, "Generated circle for $airspaceName, radius: $radiusNm nm, center: ${center!![0]},${center!![1]}")
                    }
                }
            }
            trimmed.startsWith("DB ") -> {
                val parts = trimmed.substring(3).trim().split(",")
                if (parts.size == 2 && center != null && direction != null) {
                    val start = parseCoordinate(parts[0].trim())
                    val end = parseCoordinate(parts[1].trim())
                    if (start != null && end != null) {
                        val arcPoints = generateArcPoints(start, end, center!!, direction!!)
                        coordinates.addAll(arcPoints.filter { coord ->
                            lastCoordinate == null || !areCoordinatesClose(coord, lastCoordinate!!)
                        })
                        lastCoordinate = coordinates.last()
                        Log.d(TAG, "Generated arc for $airspaceName, start: ${start[0]},${start[1]}, end: ${end[0]},${end[1]}, direction: $direction")
                    }
                }
            }
            trimmed.startsWith("V ") -> {
                val parts = trimmed.substring(2).trim().split("=")
                if (parts.size == 2) {
                    when (parts[0].trim()) {
                        "X" -> {
                            center = parseCoordinate(parts[1].trim())
                        }
                        "D" -> {
                            direction = parts[1].trim()
                        }
                    }
                }
            }
            trimmed == "DY " -> {
                addAirspaceFeature()
            }
        }
    }

    addAirspaceFeature()

    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}


fun loadSelectedClasses(context: Context): MutableMap<String, Boolean>? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (file.exists()) {
            val json = JSONObject(file.readText())
            val airspaceJson = json.optJSONObject("airspace") ?: return null
            val selectedClassesJson = airspaceJson.optJSONObject("selectedClasses") ?: return null
            val classes = mutableMapOf<String, Boolean>()
            selectedClassesJson.keys().forEach { key ->
                classes[key] = selectedClassesJson.optBoolean(key, false)
            }
            return classes
        } else {
            return null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading selected classes: ${e.message}")
        return null
    }
}

// Add this function to AirspaceUtils.kt
fun validateOpenAirFile(fileContent: String): Pair<Boolean, String> {
    val lines = fileContent.lines().take(50) // Check first 50 lines
    val trimmedLines = lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("*") }

    // Must have at least one airspace class definition
    val hasAirspaceClass = trimmedLines.any { line ->
        line.startsWith("AC ") && line.length > 3
    }

    if (!hasAirspaceClass) {
        return Pair(false, "Not a valid OpenAir file: No airspace class (AC) definitions found")
    }

    // Should have airspace name
    val hasAirspaceName = trimmedLines.any { it.startsWith("AN ") }

    // Should have altitude definitions
    val hasAltitudes = trimmedLines.any { it.startsWith("AL ") || it.startsWith("AH ") }

    // Should have coordinate definitions
    val hasCoordinates = trimmedLines.any {
        it.startsWith("DP ") || it.startsWith("DC ") || it.startsWith("DA ") || it.startsWith("DB ")
    }

    return when {
        !hasAirspaceName -> Pair(false, "Invalid OpenAir file: Missing airspace names (AN)")
        !hasAltitudes -> Pair(false, "Invalid OpenAir file: Missing altitude definitions (AL/AH)")
        !hasCoordinates -> Pair(false, "Invalid OpenAir file: Missing coordinate definitions (DP/DC/DA/DB)")
        else -> Pair(true, "Valid OpenAir airspace file")
    }
}
