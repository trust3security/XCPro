package com.example.xcpro.tasks.aat.rendering

import android.util.Log
import androidx.core.graphics.toColorInt
import com.example.xcpro.map.BuildConfig
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression

/**
 * AAT Map Renderer - MapLibre rendering operations for AAT tasks
 *
 * Handles all MapLibre layer/source operations for AAT task visualization.
 * Extracted from AATTaskRenderer.kt for file size compliance.
 *
 * SSOT COMPLIANT: Uses AATGeometryGenerator for all geometry operations.
 */
internal class AATMapRenderer {

    /**
     * Clear AAT-specific layers from the map
     *
     * CRASH FIX: Proper removal order (layers before sources)
     */
    fun clearLayers(style: Style?) {
        style?.let { s ->
            try {
                val aatLayers = listOf(
                    "aat-waypoints",
                    "aat-areas-layer",
                    "aat-borders-layer",
                    "aat-lines-layer",
                    "aat-task-line",
                    "aat-target-points-layer"
                )

                val aatSources = listOf(
                    "aat-waypoints",
                    "aat-areas",
                    "aat-lines",
                    "aat-task-line",
                    "aat-target-points"
                )

                // STEP 1: Remove all layers first (MapLibre requirement)
                aatLayers.forEach { layerId ->
                    try {
                        s.removeLayer(layerId)
                    } catch (e: Exception) {
                        // Layer doesn't exist, ignore
                    }
                }

                // STEP 2: Remove all sources after layers are removed
                aatSources.forEach { sourceId ->
                    try {
                        s.removeSource(sourceId)
                    } catch (e: Exception) {
                        // Source doesn't exist, ignore
                    }
                }

            } catch (e: Exception) {
                logRenderFailure("clearLayers", e)
            }
        }
    }

    /**
     * Plot AAT waypoints with role-based colors
     *
     * Green for start/turnpoint, red for finish
     */
    fun plotWaypoints(style: Style, waypoints: List<AATWaypoint>) {
        try {
            // Create GeoJSON for waypoints manually
            val features = waypoints.mapIndexed { index, waypoint ->
                """
                {
                    "type": "Feature",
                    "properties": {
                        "title": "${waypoint.title}",
                        "role": "${waypoint.role.name}",
                        "index": $index
                    },
                    "geometry": {
                        "type": "Point",
                        "coordinates": [${waypoint.lon}, ${waypoint.lat}]
                    }
                }
                """.trimIndent()
            }

            val geoJson = """
            {
                "type": "FeatureCollection",
                "features": [${features.joinToString(",")}]
            }
            """.trimIndent()

            // CRASH FIX: Check if source exists before adding + validate GeoJSON
            if (geoJson.contains("\"coordinates\"") && !geoJson.contains("\"coordinates\": []")) {
                try {
                    // Only add source if it doesn't already exist
                    if (style.getSource("aat-waypoints") == null) {
                        style.addSource(GeoJsonSource("aat-waypoints", geoJson))
                    } else {
                        // Source exists, update its data instead
                        val existingSource = style.getSourceAs<GeoJsonSource>("aat-waypoints")
                        existingSource?.setGeoJson(geoJson)
                    }
                } catch (e: Exception) {
                    return
                }
            } else {
                return
            }

            // Add AAT waypoint markers with role-based colors - check if layer exists first
            try {
                if (style.getLayer("aat-waypoints") == null) {
                    // Create role-based color expression: green for start/turnpoint, red for finish
                    val colorExpression = Expression.switchCase(
                        Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                        Expression.color("#F44336".toColorInt()), // Red for finish
                        Expression.color("#388E3C".toColorInt())  // Green for start/turnpoint
                    )

                    style.addLayer(
                        CircleLayer("aat-waypoints", "aat-waypoints")
                            .withProperties(
                                PropertyFactory.circleRadius(2.4f), // REDUCED: 8f * 0.3 = 2.4f (70% reduction)
                                PropertyFactory.circleColor(colorExpression), // Role-based colors
                                PropertyFactory.circleStrokeWidth(0.6f), // REDUCED: 2f * 0.3 = 0.6f (70% reduction)
                                PropertyFactory.circleStrokeColor("#FFFFFF")
                            )
                    )
                } else {
                }
            } catch (e: Exception) {
            }

        } catch (e: Exception) {
        }
    }

    /**
     * Add area features (circles, sectors) to map
     */
    fun addAreaFeatures(style: Style, areaFeatures: List<String>) {
        val areasGeoJson = """
        {
            "type": "FeatureCollection",
            "features": [${areaFeatures.joinToString(",")}]
        }
        """.trimIndent()

        if (areasGeoJson.contains("\"coordinates\"") && !areasGeoJson.contains("\"coordinates\": []")) {
            try {
                if (style.getSource("aat-areas") == null) {
                    style.addSource(GeoJsonSource("aat-areas", areasGeoJson))
                } else {
                    val existingSource = style.getSourceAs<GeoJsonSource>("aat-areas")
                    existingSource?.setGeoJson(areasGeoJson)
                }

                if (style.getLayer("aat-areas-layer") == null) {
                    val fillColorExpression = Expression.switchCase(
                        Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                        Expression.color("#F44336".toColorInt()),
                        Expression.color("#388E3C".toColorInt())
                    )

                    style.addLayer(
                        FillLayer("aat-areas-layer", "aat-areas")
                            .withProperties(
                                PropertyFactory.fillColor(fillColorExpression),
                                PropertyFactory.fillOpacity(0.2f),
                                PropertyFactory.fillOutlineColor(fillColorExpression)
                            )
                    )

                    if (style.getLayer("aat-borders-layer") == null) {
                        val borderColorExpression = Expression.switchCase(
                            Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                            Expression.color("#F44336".toColorInt()),
                            Expression.color("#388E3C".toColorInt())
                        )

                        style.addLayer(
                            LineLayer("aat-borders-layer", "aat-areas")
                                .withProperties(
                                    PropertyFactory.lineColor(borderColorExpression),
                                    PropertyFactory.lineWidth(1.25f),
                                    PropertyFactory.lineOpacity(0.9f)
                                )
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Add line features (start/finish lines) to map
     */
    fun addLineFeatures(style: Style, lineFeatures: List<String>) {
        val linesGeoJson = """
        {
            "type": "FeatureCollection",
            "features": [${lineFeatures.joinToString(",")}]
        }
        """.trimIndent()

        if (linesGeoJson.contains("\"coordinates\"") && !linesGeoJson.contains("\"coordinates\": []")) {
            try {
                if (style.getSource("aat-lines") == null) {
                    style.addSource(GeoJsonSource("aat-lines", linesGeoJson))
                } else {
                    val existingSource = style.getSourceAs<GeoJsonSource>("aat-lines")
                    existingSource?.setGeoJson(linesGeoJson)
                }

                if (style.getLayer("aat-lines-layer") == null) {
                    val lineColorExpression = Expression.switchCase(
                        Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                        Expression.color("#F44336".toColorInt()),
                        Expression.color("#388E3C".toColorInt())
                    )

                    style.addLayer(
                        LineLayer("aat-lines-layer", "aat-lines")
                            .withProperties(
                                PropertyFactory.lineColor(lineColorExpression),
                                PropertyFactory.lineWidth(2f),
                                PropertyFactory.lineOpacity(0.8f)
                            )
                    )
                }
            } catch (e: Exception) {
                logRenderFailure("addLineFeatures", e)
            }
        }
    }

    /**
     * Plot AAT task line connecting target points
     */
    fun plotTaskLine(style: Style, geometryGenerator: AATGeometryGenerator, waypoints: List<AATWaypoint>) {
        if (waypoints.size < 2) return
        val coordinates = geometryGenerator.calculateOptimalAATPath(waypoints)
        upsertTaskLine(style, coordinates)
    }

    /**
     * Upsert-only task-line update used by drag preview.
     * Avoids full layer/source teardown while target points move.
     */
    fun upsertTaskLine(style: Style, coordinates: List<List<Double>>) {
        if (coordinates.size < 2) return
        try {
            val geoJsonString = """
            {
                "type": "FeatureCollection",
                "features": [
                    {
                        "type": "Feature",
                        "geometry": {
                            "type": "LineString",
                            "coordinates": [${coordinates.map { "[${it[0]}, ${it[1]}]" }.joinToString(", ")}]
                        },
                        "properties": {}
                    }
                ]
            }
            """.trimIndent()

            val taskLineSourceId = "aat-task-line"
            if (style.getSource(taskLineSourceId) == null) {
                style.addSource(GeoJsonSource(taskLineSourceId, geoJsonString))
            } else {
                style.getSourceAs<GeoJsonSource>(taskLineSourceId)?.setGeoJson(geoJsonString)
            }

            val taskLineLayerId = "aat-task-line"
            if (style.getLayer(taskLineLayerId) == null) {
                style.addLayer(
                    LineLayer(taskLineLayerId, taskLineSourceId).withProperties(
                        PropertyFactory.lineColor(0xFF388E3C.toInt()),
                        PropertyFactory.lineWidth(1.5f),
                        PropertyFactory.lineOpacity(0.8f)
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Plot movable target point pins at line intersection points
     *
     * @param style Map style
     * @param waypoints List of AAT waypoints
     * @param editModeWaypointIndex Optional index of waypoint in edit mode (shows red dot)
     */
    fun plotTargetPointPins(style: Style, waypoints: List<AATWaypoint>, editModeWaypointIndex: Int? = null) {
        AATTargetPointPinRenderer.plotTargetPointPins(
            style = style,
            waypoints = waypoints,
            editModeWaypointIndex = editModeWaypointIndex
        )
    }

    private fun logRenderFailure(stage: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val reason = throwable.message ?: throwable.javaClass.simpleName
        Log.w(TAG, "$stage failed ($reason)")
    }

    private companion object {
        const val TAG = "AATMapRenderer"
    }
}
