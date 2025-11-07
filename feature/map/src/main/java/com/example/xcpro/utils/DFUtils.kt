package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.map.MapCameraManager
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

private const val TAG = "DFUtils"

fun parseCupToGeoJson(cupText: String): String {
    val features = JSONArray()
    val lines = cupText.lines()
    var isHeader = true

    fun dmsToDecimal(coord: String, isLat: Boolean): Double? {
        val pattern = if (isLat) {
            Regex("(\\d{2})(\\d{2}\\.\\d{3})([NS])")
        } else {
            Regex("(\\d{3})(\\d{2}\\.\\d{3})([EW])")
        }
        val match = pattern.find(coord)
        if (match != null) {
            val degrees = match.groups[1]!!.value.toInt()
            val minutes = match.groups[2]!!.value.toDouble() / 60.0
            val direction = match.groups[3]!!.value
            val decimal = degrees + minutes
            return if (direction == "S" || direction == "W") -decimal else decimal
        }
        Log.e(TAG, "Failed to parse coordinate: $coord")
        return null
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("*")) return@forEach
        if (isHeader) {
            if (trimmed.startsWith("name,code")) {
                isHeader = false
            }
            return@forEach
        }

        val parts = trimmed.split(",")
        if (parts.size >= 6) {
            try {
                val name = parts[0].trim('"')
                val lat = parts[3].trim('"')
                val lon = parts[4].trim('"')
                val elev = parts[5].trim('"')
                val latitude = dmsToDecimal(lat, true)
                val longitude = dmsToDecimal(lon, false)

                if (latitude != null && longitude != null) {
                    val feature = JSONObject().apply {
                        put("type", "Feature")
                        put("geometry", JSONObject().apply {
                            put("type", "Point")
                            put("coordinates", JSONArray().apply {
                                put(longitude)
                                put(latitude)
                            })
                        })
                        put("properties", JSONObject().apply {
                            put("name", name)
                            put("elevation", elev)
                        })
                    }
                    features.put(feature)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing waypoint line: $trimmed, ${e.message}")
            }
        }
    }

    return JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", features)
    }.toString()
}


fun loadAndApplyWaypoints(
    context: Context,
    map: MapLibreMap?,
    waypointFiles: List<Uri>,
    checkedStates: Map<String, Boolean>
) {
    map?.let { mapInstance ->
        try {
            val zoom = mapInstance.cameraPosition.zoom
            val latitude = mapInstance.cameraPosition.target?.latitude ?: MapCameraManager.INITIAL_LATITUDE
            val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom)
            val screenWidthPx = 1080.0 // Assume 1080px screen width
            val scaleKm = (metersPerPixel * screenWidthPx) / 1000.0
            if (scaleKm > 800) {
                Log.d(TAG, "Not rendering waypoints: scale ($scaleKm km) exceeds 800 km")
                mapInstance.getStyle()?.let { style ->
                    style.removeLayer("waypoint-layer")
                    style.removeSource("waypoint-source")
                }
                return
            }

            mapInstance.getStyle()?.let { style ->
                style.removeLayer("waypoint-layer")
                style.removeSource("waypoint-source")
            }
            val features = JSONArray()
            Log.d(TAG, "📍 Loading waypoints: ${waypointFiles.size} files found, checkedStates: $checkedStates")
            waypointFiles.forEach { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
                Log.d(TAG, "📍 Checking file: $fileName, enabled: ${checkedStates[fileName]}")
                if (checkedStates[fileName] == true) {
                    val file = File(context.filesDir, fileName)
                    if (file.exists()) {
                        val geoJsonData = parseCupToGeoJson(file.readText())
                        val geoJsonObject = JSONObject(geoJsonData)
                        val fileFeatures = geoJsonObject.optJSONArray("features") ?: JSONArray()
                        for (i in 0 until fileFeatures.length()) {
                            features.put(fileFeatures.getJSONObject(i))
                        }
                        Log.d(TAG, "✅ Processed waypoint file: $fileName (${fileFeatures.length()} waypoints)")
                    } else {
                        Log.e(TAG, "❌ Waypoint file $fileName does not exist at ${file.absolutePath}")
                    }
                } else {
                    Log.d(TAG, "⏭️ Skipping disabled waypoint file: $fileName")
                }
            }
            Log.d(TAG, "📍 Total waypoints to display: ${features.length()}")
            val geoJson = JSONObject().apply {
                put("type", "FeatureCollection")
                put("features", features)
            }.toString()
            mapInstance.getStyle()?.let { style ->
                style.addSource(GeoJsonSource("waypoint-source", geoJson))
                val layer = SymbolLayer("waypoint-layer", "waypoint-source").withProperties(
                    // ✅ FIX: Text-only waypoints (no icon required - was causing invisibility)
                    PropertyFactory.textField("{name}"),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor("#000000"), // Black text
                    PropertyFactory.textHaloColor("#FFFFFF"), // White halo for contrast
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAnchor("center"),
                    PropertyFactory.textAllowOverlap(false), // Prevent overlap clutter
                    PropertyFactory.textIgnorePlacement(false)
                )
                // ✅ FIX: Add waypoint layer on top of all other layers so it's visible
                try {
                    // Try to add above road-label if it exists
                    if (style.getLayer("road-label") != null) {
                        style.addLayerAbove(layer, "road-label")
                        Log.d(TAG, "✅ Waypoint text labels added above road-label (${features.length()} waypoints)")
                    } else {
                        // Fallback: just add the layer (will be on top by default if added last)
                        style.addLayer(layer)
                        Log.d(TAG, "✅ Waypoint text labels added to map (${features.length()} waypoints, road-label not found)")
                    }
                } catch (e: Exception) {
                    // Final fallback: try addLayer without position
                    style.addLayer(layer)
                    Log.e(TAG, "⚠️ Waypoint layer added with fallback method: ${e.message}")
                }
            } ?: Log.e(TAG, "Map style not loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading waypoint files: ${e.message}", e)
        }
    } ?: Log.e(TAG, "Map instance not available")
}
fun saveConfig(
    context: Context,
    style: String,
    cardStatesByMode: Map<FlightMode, List<MutableState<Pair<Offset, Pair<Float, Float>>>>>,
    profileExpanded: Boolean,
    mapStyleExpanded: Boolean
) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val jsonObject = if (file.exists()) {
            JSONObject(file.readText())
        } else {
            JSONObject()
        }
        val appObject = jsonObject.optJSONObject("app") ?: JSONObject()
        appObject.put("mapStyle", style)
        jsonObject.put("app", appObject)

        cardStatesByMode.forEach { (mode, cards) ->
            val modeObject = JSONObject()
            modeObject.put("card_count", cards.size)
            cards.forEachIndexed { index, state ->
                val cardObject = JSONObject()
                cardObject.put("x", state.value.first.x)
                cardObject.put("y", state.value.first.y)
                cardObject.put("width", state.value.second.first)
                cardObject.put("height", state.value.second.second)
                modeObject.put("card_$index", cardObject)
            }
            jsonObject.put(mode.name.lowercase(), modeObject)
        }

        val navDrawerObject = jsonObject.optJSONObject("navDrawer") ?: JSONObject()
        navDrawerObject.put("profileExpanded", profileExpanded)
        navDrawerObject.put("mapStyleExpanded", mapStyleExpanded)
        jsonObject.put("navDrawer", navDrawerObject)

        file.writeText(jsonObject.toString(2))
        Log.d(TAG, "Saved config to configuration.json: mapStyle=$style, cards=$cardStatesByMode, profileExpanded=$profileExpanded, mapStyleExpanded=$mapStyleExpanded")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving config to configuration.json: ${e.message}")
    }
}

fun loadConfig(context: Context): JSONObject? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (file.exists()) {
            val jsonString = file.readText()
            JSONObject(jsonString)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading config from configuration.json: ${e.message}")
        null
    }
}

fun loadWaypointFiles(context: Context): Pair<List<Uri>, MutableMap<String, Boolean>> {
    try {
        val file = File(context.filesDir, "configuration.json")
        if (!file.exists()) return Pair(emptyList(), mutableMapOf())
        val json = JSONObject(file.readText())
        val waypointFiles = json.optJSONObject("waypoint_files")?.optJSONObject("selected_files") ?: return Pair(emptyList(), mutableMapOf())
        val files = mutableListOf<Uri>()
        val checkedStates = mutableMapOf<String, Boolean>()
        waypointFiles.keys().forEach { fileName ->
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                files.add(Uri.fromFile(file))
                checkedStates[fileName] = waypointFiles.getBoolean(fileName)
            }
        }
        return Pair(files, checkedStates)
    } catch (e: Exception) {
        Log.e(TAG, "Error loading waypoint files: ${e.message}")
        return Pair(emptyList(), mutableMapOf())
    }
}

fun saveWaypointFiles(context: Context, files: List<Uri>, checkedStates: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val waypointFiles = JSONObject()
        val filesArray = JSONObject()
        files.forEach { uri ->
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: return@forEach
            filesArray.put(fileName, checkedStates[fileName] ?: false)
        }
        waypointFiles.put("selected_files", filesArray)
        json.put("waypoint_files", waypointFiles)
        file.writeText(json.toString(2))
        Log.d(TAG, "Saved waypoint files: $filesArray")
    } catch (e: Exception) {
        Log.e(TAG, "Error saving waypoint files: ${e.message}")
    }
}

// Add these functions to your existing DFUtils.kt file or any utility file

fun saveSelectedTemplates(context: Context, selectedTemplates: Map<String, Boolean>) {
    try {
        val file = File(context.filesDir, "configuration.json")
        val json = if (file.exists()) JSONObject(file.readText()) else JSONObject()
        val templatesJson = JSONObject()
        selectedTemplates.forEach { (key, value) -> templatesJson.put(key, value) }
        json.put("selected_templates", templatesJson)
        file.writeText(json.toString(2))
        Log.d("Templates", "Saved selected templates: $selectedTemplates")
    } catch (e: Exception) {
        Log.e("Templates", "Error saving selected templates: ${e.message}")
    }
}

fun loadSelectedTemplates(context: Context): MutableMap<String, Boolean>? {
    return try {
        val file = File(context.filesDir, "configuration.json")
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val templatesJson = json.optJSONObject("selected_templates") ?: return null
        val templates = mutableMapOf<String, Boolean>()
        templatesJson.keys().forEach { key ->
            templates[key] = templatesJson.getBoolean(key)
        }
        templates
    } catch (e: Exception) {
        Log.e("Templates", "Error loading selected templates: ${e.message}")
        null
    }
}
