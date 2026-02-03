package com.example.xcpro

import android.content.Context
import android.util.Log
import com.example.xcpro.common.documents.DocumentRef
import com.example.xcpro.map.BlueLocationOverlay
import com.example.xcpro.map.MapCameraManager
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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


suspend fun loadAndApplyWaypoints(
    context: Context,
    map: MapLibreMap?,
    waypointFiles: List<DocumentRef>,
    checkedStates: Map<String, Boolean>,
    repository: WaypointOverlayRepository = WaypointOverlayRepository(context)
) {
    val mapInstance = map ?: run {
        Log.e(TAG, "Map instance not available")
        return
    }

    try {
        val scaleKm = withContext(Dispatchers.Main.immediate) {
            val zoom = mapInstance.cameraPosition.zoom
            val latitude = mapInstance.cameraPosition.target?.latitude ?: MapCameraManager.INITIAL_LATITUDE
            val metersPerPixel = 156543.03392 * Math.cos(Math.toRadians(latitude)) / Math.pow(2.0, zoom)
            val screenWidthPx = 1080.0 // Assume 1080px screen width
            (metersPerPixel * screenWidthPx) / 1000.0
        }

        if (scaleKm > 800) {
            Log.d(TAG, "Not rendering waypoints: scale ($scaleKm km) exceeds 800 km")
            withContext(Dispatchers.Main.immediate) {
                mapInstance.getStyle()?.let { style ->
                    style.removeLayer("waypoint-layer")
                    style.removeSource("waypoint-source")
                }
            }
            return
        }

        Log.d(TAG, "Loading waypoints: ${waypointFiles.size} files found, checkedStates: $checkedStates")
        val geoJson = repository.buildGeoJson(waypointFiles, checkedStates)

        withContext(Dispatchers.Main.immediate) {
            mapInstance.getStyle()?.let { style ->
                style.removeLayer("waypoint-layer")
                style.removeSource("waypoint-source")
                style.addSource(GeoJsonSource("waypoint-source", geoJson))
                val layer = SymbolLayer("waypoint-layer", "waypoint-source").withProperties(
                    // Text-only waypoints (no icon required - was causing invisibility)
                    PropertyFactory.textField("{name}"),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor("#000000"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAnchor("center"),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(false)
                )
                val aircraftLayerExists = style.getLayer(BlueLocationOverlay.LAYER_ID) != null
                if (aircraftLayerExists) {
                    style.addLayerBelow(layer, BlueLocationOverlay.LAYER_ID)
                    Log.d(TAG, "Waypoint text labels added below aircraft overlay")
                } else {
                    try {
                        if (style.getLayer("road-label") != null) {
                            style.addLayerAbove(layer, "road-label")
                            Log.d(TAG, "Waypoint text labels added above road-label")
                        } else {
                            style.addLayer(layer)
                            Log.d(TAG, "Waypoint text labels added to map (road-label not found)")
                        }
                    } catch (e: Exception) {
                        style.addLayer(layer)
                        Log.e(TAG, "Waypoint layer added with fallback method: ${e.message}")
                    }
                }
            } ?: Log.e(TAG, "Map style not loaded")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading waypoint files: ${e.message}", e)
    }
}
