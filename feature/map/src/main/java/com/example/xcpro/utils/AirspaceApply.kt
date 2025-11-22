package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

private const val AIRSPACE_APPLY_TAG = "AirspaceApply"

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
            Log.d(AIRSPACE_APPLY_TAG, "Auto-enabled default airspace classes: $selectedClasses")
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
                            Log.d(AIRSPACE_APPLY_TAG, "Processed airspace file: $fileName")
                        } else {
                            Log.e(AIRSPACE_APPLY_TAG, "Airspace file $fileName does not exist")
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
                    Log.d(AIRSPACE_APPLY_TAG, "Airspace data loaded and added to map, filtered classes: $selectedClasses")
                } ?: Log.e(AIRSPACE_APPLY_TAG, "Map style not loaded")
            } catch (e: Exception) {
                Log.e(AIRSPACE_APPLY_TAG, "Error loading airspace files: ${e.message}", e)
            }
        } ?: Log.e(AIRSPACE_APPLY_TAG, "Map instance not available")
    } else {
        Log.d(AIRSPACE_APPLY_TAG, "No airspace files selected")
    }
}
