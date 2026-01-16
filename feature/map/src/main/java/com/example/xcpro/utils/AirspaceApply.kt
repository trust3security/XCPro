package com.example.xcpro

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.io.File

private const val AIRSPACE_APPLY_TAG = "AirspaceApply"

suspend fun loadAndApplyAirspace(
    context: Context,
    map: MapLibreMap?,
    repository: AirspaceRepository = AirspaceRepository(context)
) {
    val config = loadConfig(context)
    val airspaceFilesJson = config?.optJSONObject("airspace_files")?.optJSONObject("selected_files")

    // Load selected classes, or create defaults if none exist
    var selectedClasses = repository.loadSelectedClasses()?.filter { it.value }?.keys?.toSet() ?: emptySet()

    // Auto-enable default classes if none are selected and we have airspace files
    if (selectedClasses.isEmpty() && airspaceFilesJson != null) {
        val airspaceFiles = mutableListOf<Uri>()
        airspaceFilesJson.keys().forEach { fileName ->
            if (airspaceFilesJson.optBoolean(fileName, false)) {
                airspaceFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
            }
        }

        if (airspaceFiles.isNotEmpty()) {
            val availableClasses = repository.parseClasses(airspaceFiles)
            val defaultClasses = availableClasses.associateWith {
                it == "R" || it == "D" || it == "C" || it == "CTR"
            }
            repository.saveSelectedClasses(defaultClasses)
            selectedClasses = defaultClasses.filter { it.value }.keys.toSet()
            Log.d(AIRSPACE_APPLY_TAG, "Auto-enabled default airspace classes: $selectedClasses")
        }
    }
    if (airspaceFilesJson != null) {
        map?.let { mapInstance ->
            try {
                val enabledFiles = mutableListOf<Uri>()
                airspaceFilesJson.keys().forEach { fileName ->
                    if (airspaceFilesJson.optBoolean(fileName, false)) {
                        enabledFiles.add(Uri.fromFile(File(context.filesDir, fileName)))
                    }
                }

                val geoJson = repository.buildGeoJson(enabledFiles, selectedClasses)

                withContext(Dispatchers.Main.immediate) {
                    mapInstance.getStyle()?.let { style ->
                        style.removeLayer("airspace-layer")
                        style.removeSource("airspace-source")
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
                }
            } catch (e: Exception) {
                Log.e(AIRSPACE_APPLY_TAG, "Error loading airspace files: ${e.message}", e)
            }
        } ?: Log.e(AIRSPACE_APPLY_TAG, "Map instance not available")
    } else {
        Log.d(AIRSPACE_APPLY_TAG, "No airspace files selected")
    }
}
