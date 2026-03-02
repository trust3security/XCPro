package com.example.xcpro

import android.util.Log
import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.common.documents.DocumentRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val AIRSPACE_APPLY_TAG = "AirspaceApply"

suspend fun loadAndApplyAirspace(
    map: MapLibreMap?,
    useCase: AirspaceUseCase
) {
    val (files, checks) = useCase.loadAirspaceFiles()
    val enabledFiles = files.filter { document ->
        val fileName = document.fileName()
        checks[fileName] == true
    }

    val selectedClassStates = useCase.loadSelectedClasses()
    val selectedClasses = resolveSelectedClasses(
        useCase = useCase,
        enabledFiles = enabledFiles,
        selectedClassStates = selectedClassStates
    )

    if (enabledFiles.isEmpty()) {
        Log.d(AIRSPACE_APPLY_TAG, "No airspace files selected")
        clearAirspaceOverlay(map)
        return
    }
    if (selectedClasses.isEmpty()) {
        Log.d(AIRSPACE_APPLY_TAG, "No airspace classes selected; clearing overlay")
        clearAirspaceOverlay(map)
        return
    }

    map?.let { mapInstance ->
        try {
            val geoJson = useCase.buildGeoJson(enabledFiles, selectedClasses)

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
}

private suspend fun clearAirspaceOverlay(map: MapLibreMap?) {
    map ?: return
    withContext(Dispatchers.Main.immediate) {
        map.getStyle()?.let { style ->
            style.removeLayer("airspace-layer")
            style.removeSource("airspace-source")
        }
    }
}

private suspend fun resolveSelectedClasses(
    useCase: AirspaceUseCase,
    enabledFiles: List<DocumentRef>,
    selectedClassStates: Map<String, Boolean>?
): Set<String> {
    if (selectedClassStates == null || selectedClassStates.isEmpty()) {
        val availableClasses = useCase.parseClasses(enabledFiles)
        return availableClasses.filter(::defaultClassEnabled).toSet()
    }
    return selectedClassStates.filter { it.value }.keys.toSet()
}

private fun defaultClassEnabled(className: String): Boolean = when (className.uppercase()) {
    "R", "D", "C", "CTR" -> true
    else -> false
}
