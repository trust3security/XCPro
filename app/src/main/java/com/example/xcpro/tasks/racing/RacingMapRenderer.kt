package com.example.xcpro.tasks.racing

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import com.example.xcpro.tasks.racing.models.*

/**
 * Racing Map Renderer - MapLibre rendering operations for racing tasks
 *
 * Handles all MapLibre layer/source operations for racing task visualization.
 * Extracted from RacingTaskDisplay.kt for file size compliance.
 */
internal class RacingMapRenderer {

    /**
     * Draw racing waypoints with proper turnpoint geometry
     */
    fun drawRacingWaypoints(
        style: Style,
        map: MapLibreMap,
        waypoints: List<RacingWaypoint>,
        geometryGenerator: RacingGeometryCoordinator
    ) {
        // Remove existing racing elements (layers first, then sources)
        try {
            // Remove all racing layers first
            listOf("racing-waypoints", "racing-turnpoint-areas-fill", "racing-turnpoint-areas-border").forEach { layerId ->
                try { style.removeLayer(layerId) } catch (e: Exception) { }
            }
            // Then remove sources
            listOf("racing-waypoints", "racing-turnpoint-areas").forEach { sourceId ->
                try { style.removeSource(sourceId) } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            // Sources/layers don't exist yet
        }

        // Draw waypoint markers (simple red dots)
        drawWaypointMarkers(style, waypoints)

        // Draw turnpoint geometry (start lines, finish lines, cylinders, etc.)
        drawTurnpointGeometry(style, waypoints, geometryGenerator)
    }

    /**
     * Draw simple waypoint markers
     */
    private fun drawWaypointMarkers(style: Style, waypoints: List<RacingWaypoint>) {
        val markerFeatures = mutableListOf<String>()
        waypoints.forEachIndexed { index, waypoint ->
            val roleText = when (waypoint.role) {
                RacingWaypointRole.START -> "START"
                RacingWaypointRole.FINISH -> "FINISH"
                RacingWaypointRole.TURNPOINT -> "TP${index}"
            }

            markerFeatures.add("""
                {
                    "type": "Feature",
                    "geometry": {
                        "type": "Point",
                        "coordinates": [${waypoint.lon}, ${waypoint.lat}]
                    },
                    "properties": {
                        "title": "${waypoint.title}",
                        "role": "$roleText"
                    }
                }
            """.trimIndent())
        }

        // CRASH FIX: Validate marker features before creating GeoJSON
        val validMarkerFeatures = mutableListOf<String>()
        markerFeatures.forEach { feature ->
            if (RacingGeoJSONValidator.validateGeoJSON(feature, "Racing waypoint marker")) {
                validMarkerFeatures.add(feature)
            } else {
                println("⚠️ RACING MARKERS: Skipping invalid marker feature")
            }
        }

        if (validMarkerFeatures.isEmpty()) {
            println("❌ RACING MARKERS: No valid marker features, skipping marker display")
            return
        }

        val markerGeoJson = """
            {
                "type": "FeatureCollection",
                "features": [${validMarkerFeatures.joinToString(",")}]
            }
        """.trimIndent()

        // CRASH FIX: Validate complete FeatureCollection before adding to MapLibre
        if (!RacingGeoJSONValidator.validateGeoJSON(markerGeoJson, "Racing waypoint markers FeatureCollection")) {
            println("❌ RACING MARKERS: Invalid marker FeatureCollection, skipping marker display")
            return
        }

        try {
            style.addSource(GeoJsonSource("racing-waypoints", markerGeoJson))
            style.addLayer(
                CircleLayer("racing-waypoints", "racing-waypoints")
                    .withProperties(
                        PropertyFactory.circleRadius(0.5f), // 75% smaller (was 2f)
                        PropertyFactory.circleColor(
                            Expression.switchCase(
                                Expression.eq(Expression.get("role"), Expression.literal("start")),
                                Expression.literal("#006400"), // Dark green for start
                                Expression.eq(Expression.get("role"), Expression.literal("finish")),
                                Expression.literal("#FF0000"), // Red for finish
                                Expression.literal("#0066FF") // Blue for turnpoints
                            )
                        ),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f)
                    )
            )
            println("🏁 RACING TASK: Added ${validMarkerFeatures.size} validated waypoint markers")
        } catch (e: Exception) {
            println("❌ RACING TASK: MapLibre error drawing waypoint markers: ${e.message}")
            e.printStackTrace() // Include stack trace for debugging MapLibre native crashes
        }
    }

    /**
     * Draw turnpoint geometry (start lines, finish lines, cylinders, sectors)
     */
    private fun drawTurnpointGeometry(
        style: Style,
        waypoints: List<RacingWaypoint>,
        geometryGenerator: RacingGeometryCoordinator
    ) {
        val geometryFeatures = mutableListOf<String>()

        waypoints.forEachIndexed { index, waypoint ->
            try {
                val geometryFeature = when (waypoint.role) {
                    RacingWaypointRole.START -> {
                        geometryGenerator.generateStartGeometry(index, waypoint, waypoints)
                    }
                    RacingWaypointRole.FINISH -> {
                        geometryGenerator.generateFinishGeometry(index, waypoint, waypoints)
                    }
                    RacingWaypointRole.TURNPOINT -> {
                        geometryGenerator.generateTurnpointGeometry(index, waypoint, waypoints)
                    }
                }

                // CRASH FIX: Validate each geometry feature before adding
                geometryFeature?.let { feature ->
                    println("🗺️ MAP RENDER DEBUG: Generated feature for ${waypoint.role} waypoint")
                    if (RacingGeoJSONValidator.validateGeoJSON(feature, "Racing ${waypoint.role} geometry")) {
                        geometryFeatures.add(feature)
                        println("🗺️ MAP RENDER DEBUG: ✅ Added ${waypoint.role} feature to geometry list")
                    } else {
                        println("⚠️ RACING GEOMETRY: Skipping invalid geometry for ${waypoint.title}")
                    }
                }
            } catch (e: Exception) {
                println("❌ RACING GEOMETRY: Exception generating geometry for ${waypoint.title}: ${e.message}")
            }
        }

        if (geometryFeatures.isNotEmpty()) {
            val geometryGeoJson = """
                {
                    "type": "FeatureCollection",
                    "features": [${geometryFeatures.joinToString(",")}]
                }
            """.trimIndent()

            // CRASH FIX: Validate complete geometry FeatureCollection before adding to MapLibre
            if (!RacingGeoJSONValidator.validateGeoJSON(geometryGeoJson, "Racing turnpoint geometry FeatureCollection")) {
                println("❌ RACING GEOMETRY: Invalid geometry FeatureCollection, skipping geometry display")
                return
            }

            try {
                style.addSource(GeoJsonSource("racing-turnpoint-areas", geometryGeoJson))

                // Add fill layer for areas (cylinders, sectors)
                style.addLayer(
                    FillLayer("racing-turnpoint-areas-fill", "racing-turnpoint-areas")
                        .withProperties(
                            PropertyFactory.fillColor(
                                Expression.switchCase(
                                    Expression.eq(Expression.get("role"), Expression.literal("start")),
                                    Expression.literal("#006400"), // Green for ALL start types
                                    Expression.eq(Expression.get("role"), Expression.literal("finish")),
                                    Expression.literal("#FF0000"), // Red for ALL finish types
                                    Expression.literal("#0066FF") // Blue for ALL turnpoint types
                                )
                            ),
                            PropertyFactory.fillOpacity(0.2f)
                        )
                )

                // Add line layer for borders (start/finish lines, cylinder edges)
                style.addLayer(
                    LineLayer("racing-turnpoint-areas-border", "racing-turnpoint-areas")
                        .withProperties(
                            PropertyFactory.lineColor(
                                Expression.switchCase(
                                    Expression.eq(Expression.get("role"), Expression.literal("start")),
                                    Expression.literal("#006400"), // Green for ALL start types
                                    Expression.eq(Expression.get("role"), Expression.literal("finish")),
                                    Expression.literal("#FF0000"), // Red for ALL finish types
                                    Expression.literal("#0066FF") // Blue for ALL turnpoint types
                                )
                            ),
                            PropertyFactory.lineWidth(1.5f),
                            PropertyFactory.lineOpacity(0.9f)
                        )
                )

                println("🏁 RACING TASK: Added turnpoint geometry")
            } catch (e: Exception) {
                println("🏁 RACING TASK: Error drawing turnpoint geometry: ${e.message}")
            }
        }
    }

    /**
     * Draw racing course line
     */
    fun drawRacingCourseLine(
        style: Style,
        waypoints: List<RacingWaypoint>,
        racingTaskCalculator: RacingTaskCalculatorInterface
    ) {
        // Remove existing course line
        try {
            style.removeLayer("racing-course-line")
            style.removeSource("racing-course-line")
        } catch (e: Exception) {
            // Source/layer doesn't exist yet
        }

        try {
            // CRASH FIX: Create line coordinates using optimal FAI path with validation
            val optimalPath = racingTaskCalculator.findOptimalFAIPath(waypoints)

            // CRASH FIX: Validate optimal path coordinates
            val validCoordinates = mutableListOf<String>()
            optimalPath.forEach { (lat, lon) ->
                if (lat.isFinite() && lon.isFinite() &&
                    lat >= -90.0 && lat <= 90.0 &&
                    lon >= -180.0 && lon <= 180.0) {
                    validCoordinates.add("[${lon}, ${lat}]")
                } else {
                    println("⚠️ RACING COURSE LINE: Skipping invalid coordinate: lat=$lat, lon=$lon")
                }
            }

            if (validCoordinates.isEmpty()) {
                println("❌ RACING COURSE LINE: No valid coordinates in optimal path, skipping course line")
                return
            }

            println("🏁 RACING COURSE LINE: Drawing optimal path with ${validCoordinates.size}/${optimalPath.size} valid points")
            println("🏁 RACING COURSE LINE: Waypoint types: ${waypoints.map { it.turnPointType }}")

            val geoJson = """
                {
                    "type": "Feature",
                    "geometry": {
                        "type": "LineString",
                        "coordinates": [${validCoordinates.joinToString(",")}]
                    }
                }
            """.trimIndent()

            // CRASH FIX: Validate course line GeoJSON before adding to MapLibre
            if (!RacingGeoJSONValidator.validateGeoJSON(geoJson, "Racing course line")) {
                println("❌ RACING COURSE LINE: Invalid course line GeoJSON, skipping course line display")
                return
            }

            style.addSource(GeoJsonSource("racing-course-line", geoJson))
            style.addLayer(
                LineLayer("racing-course-line", "racing-course-line")
                    .withProperties(
                        PropertyFactory.lineColor("#0066FF"),
                        PropertyFactory.lineWidth(1.5f), // THINNER: Matched to AAT line thickness
                        PropertyFactory.lineOpacity(0.8f)
                    )
            )
            println("🏁 RACING TASK: Successfully added validated course line with ${validCoordinates.size} coordinates")
        } catch (e: Exception) {
            println("❌ RACING TASK: MapLibre error drawing course line: ${e.message}")
            e.printStackTrace() // Include stack trace for debugging MapLibre native crashes
        } catch (e: Exception) {
            println("❌ RACING COURSE LINE: Fatal exception in drawRacingCourseLine: ${e.message}")
            e.printStackTrace()
        }
    }
}
