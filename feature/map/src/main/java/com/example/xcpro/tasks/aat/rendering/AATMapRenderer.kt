package com.example.xcpro.tasks.aat.rendering

import androidx.core.graphics.toColorInt
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
                        println(" AAT RENDERER: Removed layer: $layerId")
                    } catch (e: Exception) {
                        // Layer doesn't exist, ignore
                    }
                }

                // STEP 2: Remove all sources after layers are removed
                aatSources.forEach { sourceId ->
                    try {
                        s.removeSource(sourceId)
                        println(" AAT RENDERER: Removed source: $sourceId")
                    } catch (e: Exception) {
                        // Source doesn't exist, ignore
                    }
                }

                println(" AAT RENDERER: Cleared all AAT map layers and sources in correct order")
            } catch (e: Exception) {
                println(" AAT RENDERER: Error clearing AAT layers: ${e.message}")
                e.printStackTrace()
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
                        println(" AAT RENDERER: Added aat-waypoints source")
                    } else {
                        // Source exists, update its data instead
                        val existingSource = style.getSourceAs<GeoJsonSource>("aat-waypoints")
                        existingSource?.setGeoJson(geoJson)
                        println(" AAT RENDERER: Updated existing aat-waypoints source")
                    }
                } catch (e: Exception) {
                    println(" AAT RENDERER: Error adding waypoints source: ${e.message}")
                    return
                }
            } else {
                println(" AAT RENDERER: Skipping invalid waypoints GeoJSON")
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
                    println(" AAT RENDERER: Added aat-waypoints layer")
                } else {
                    println(" AAT RENDERER: aat-waypoints layer already exists")
                }
            } catch (e: Exception) {
                println(" AAT RENDERER: Error adding waypoints layer: ${e.message}")
            }

            println(" AAT RENDERER: Added ${features.size} waypoint markers")
        } catch (e: Exception) {
            println(" AAT RENDERER: Error plotting waypoints: ${e.message}")
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
                    println(" AAT RENDERER: Added aat-areas source")
                } else {
                    val existingSource = style.getSourceAs<GeoJsonSource>("aat-areas")
                    existingSource?.setGeoJson(areasGeoJson)
                    println(" AAT RENDERER: Updated existing aat-areas source")
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
                        println(" AAT RENDERER: Added aat-borders-layer")
                    }
                    println(" AAT RENDERER: Added aat-areas-layer")
                }
                println(" AAT RENDERER: Processed ${areaFeatures.size} area features")
            } catch (e: Exception) {
                println(" AAT RENDERER: Error adding area features: ${e.message}")
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
                    println(" AAT RENDERER: Added aat-lines source")
                } else {
                    val existingSource = style.getSourceAs<GeoJsonSource>("aat-lines")
                    existingSource?.setGeoJson(linesGeoJson)
                    println(" AAT RENDERER: Updated existing aat-lines source")
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
                    println(" AAT RENDERER: Added aat-lines-layer")
                }
                println(" AAT RENDERER: Processed ${lineFeatures.size} line features")
            } catch (e: Exception) {
                println(" AAT RENDERER: Error adding line features: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Plot AAT task line connecting target points
     */
    fun plotTaskLine(style: Style, geometryGenerator: AATGeometryGenerator, waypoints: List<AATWaypoint>) {
        try {
            if (waypoints.size < 2) return

            val coordinates = geometryGenerator.calculateOptimalAATPath(waypoints)

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

            if (geoJsonString.contains("\"coordinates\"") && coordinates.isNotEmpty()) {
                style.addSource(GeoJsonSource("aat-task-line", geoJsonString))
            } else {
                println(" AAT RENDERER: Skipping invalid task line GeoJSON")
                return
            }

            val layer = LineLayer("aat-task-line", "aat-task-line")
            layer.withProperties(
                PropertyFactory.lineColor(0xFF388E3C.toInt()),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.8f)
            )
            style.addLayer(layer)

            println(" AAT RENDERER: Added task line connecting ${coordinates.size} target points")
        } catch (e: Exception) {
            println(" AAT RENDERER: Error plotting task line: ${e.message}")
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
        try {
            val features = waypoints.mapIndexed { index, waypoint ->
                val isEditMode = editModeWaypointIndex == index
                """
                {
                    "type": "Feature",
                    "properties": {
                        "title": "${waypoint.title} Target Point",
                        "role": "${waypoint.role.name}",
                        "index": $index,
                        "draggable": true,
                        "type": "target_point",
                        "editMode": $isEditMode
                    },
                    "geometry": {
                        "type": "Point",
                        "coordinates": [${waypoint.targetPoint.longitude}, ${waypoint.targetPoint.latitude}]
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

            if (style.getSource("aat-target-points") == null) {
                style.addSource(GeoJsonSource("aat-target-points", geoJson))
                println(" AAT RENDERER: Added target points source")
            } else {
                val existingSource = style.getSourceAs<GeoJsonSource>("aat-target-points")
                existingSource?.setGeoJson(geoJson)
                println(" AAT RENDERER: Updated target points source")
            }

            // Always recreate layer to apply edit mode color and size changes
            try {
                if (style.getLayer("aat-target-points-layer") != null) {
                    style.removeLayer("aat-target-points-layer")
                }
            } catch (e: Exception) {
                // Layer doesn't exist, ignore
            }

            // Color logic: START always green, edit mode red, finish red, turnpoint blue
            val colorExpression = Expression.switchCase(
                // FIRST: Check if START - always show GREEN
                Expression.eq(Expression.get("role"), Expression.literal("START")),
                Expression.color("#388E3C".toColorInt()), // GREEN for start
                // SECOND: Check if in edit mode - show RED
                Expression.eq(Expression.get("editMode"), Expression.literal(true)),
                Expression.color("#FF0000".toColorInt()), // RED for edit mode
                // THIRD: Check role for finish - show red
                Expression.eq(Expression.get("role"), Expression.literal("FINISH")),
                Expression.color("#F44336".toColorInt()), // Red for finish
                // DEFAULT: BLUE for turnpoint
                Expression.color("#2196F3".toColorInt())  // Blue for turnpoint
            )

            // Size logic: 4f in edit mode for easier dragging, 2f otherwise
            val radiusExpression = Expression.switchCase(
                Expression.eq(Expression.get("editMode"), Expression.literal(true)),
                Expression.literal(4f), // Larger in edit mode
                Expression.literal(2f)  // Normal size otherwise
            )

            style.addLayer(
                CircleLayer("aat-target-points-layer", "aat-target-points")
                    .withProperties(
                        PropertyFactory.circleRadius(radiusExpression), // Dynamic size based on edit mode
                        PropertyFactory.circleColor(colorExpression),
                        PropertyFactory.circleStrokeWidth(1f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleOpacity(0.9f)
                    )
            )
            println(" AAT RENDERER: Added target points layer with ${features.size} draggable pins (edit mode aware)")

        } catch (e: Exception) {
            println(" AAT RENDERER: Error plotting target point pins: ${e.message}")
        }
    }
}
