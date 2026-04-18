package com.trust3.xcpro.tasks.racing

import android.util.Log
import com.trust3.xcpro.map.BuildConfig
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import com.trust3.xcpro.tasks.racing.models.*

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
            }
        }

        if (validMarkerFeatures.isEmpty()) {
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
        } catch (e: Exception) {
            logRenderFailure("drawWaypointMarkers", e)
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
        val geometryGeoJson = buildTurnpointGeometryGeoJson(waypoints, geometryGenerator) ?: return
        try {
            style.addSource(GeoJsonSource("racing-turnpoint-areas", geometryGeoJson))
            style.addLayer(createTurnpointGeometryFillLayer())
            style.addLayer(createTurnpointGeometryBorderLayer())
        } catch (e: Exception) {
            logRenderFailure("drawTurnpointGeometry", e)
        }
    }

    private fun buildTurnpointGeometryGeoJson(
        waypoints: List<RacingWaypoint>,
        geometryGenerator: RacingGeometryCoordinator
    ): String? {
        val geometryFeatures = mutableListOf<String>()
        for (index in waypoints.indices) {
            val waypoint = waypoints[index]
            val feature = generateTurnpointGeometryFeature(index, waypoint, waypoints, geometryGenerator)
                ?: continue
            if (RacingGeoJSONValidator.validateGeoJSON(feature, "Racing ${waypoint.role} geometry")) {
                geometryFeatures.add(feature)
            }
        }
        if (geometryFeatures.isEmpty()) return null
        return RacingGeoJSONValidator.validateFeatureCollection(
            features = geometryFeatures,
            context = "Racing turnpoint geometry FeatureCollection"
        )
    }

    private fun generateTurnpointGeometryFeature(
        index: Int,
        waypoint: RacingWaypoint,
        waypoints: List<RacingWaypoint>,
        geometryGenerator: RacingGeometryCoordinator
    ): String? {
        return try {
            when (waypoint.role) {
                RacingWaypointRole.START ->
                    geometryGenerator.generateStartGeometry(index, waypoint, waypoints)

                RacingWaypointRole.FINISH ->
                    geometryGenerator.generateFinishGeometry(index, waypoint, waypoints)

                RacingWaypointRole.TURNPOINT ->
                    geometryGenerator.generateTurnpointGeometry(index, waypoint, waypoints)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createTurnpointGeometryFillLayer(): FillLayer {
        return FillLayer("racing-turnpoint-areas-fill", "racing-turnpoint-areas")
            .withProperties(
                PropertyFactory.fillColor(roleColorExpression()),
                PropertyFactory.fillOpacity(0.2f)
            )
    }

    private fun createTurnpointGeometryBorderLayer(): LineLayer {
        return LineLayer("racing-turnpoint-areas-border", "racing-turnpoint-areas")
            .withProperties(
                PropertyFactory.lineColor(roleColorExpression()),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.9f)
            )
    }

    private fun roleColorExpression(): Expression {
        return Expression.switchCase(
            Expression.eq(Expression.get("role"), Expression.literal("start")),
            Expression.literal("#006400"),
            Expression.eq(Expression.get("role"), Expression.literal("finish")),
            Expression.literal("#FF0000"),
            Expression.literal("#0066FF")
        )
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
                }
            }

            if (validCoordinates.isEmpty()) {
                return
            }


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
        } catch (e: Exception) {
            logRenderFailure("drawRacingCourseLine", e)
        }
    }

    private fun logRenderFailure(stage: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val reason = throwable.message ?: throwable.javaClass.simpleName
        Log.w(TAG, "$stage failed ($reason)")
    }

    private companion object {
        const val TAG = "RacingMapRenderer"
    }
}
