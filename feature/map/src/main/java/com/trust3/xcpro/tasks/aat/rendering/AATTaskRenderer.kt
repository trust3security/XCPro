package com.trust3.xcpro.tasks.aat.rendering

import android.util.Log
import com.trust3.xcpro.map.BuildConfig
import com.trust3.xcpro.tasks.aat.SimpleAATTask
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole
import com.trust3.xcpro.tasks.aat.models.getAuthorityRadiusMeters
import com.trust3.xcpro.tasks.aat.geometry.AATGeometryGenerator
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * AAT Task Renderer - Main coordinator for AAT task visualization
 *
 * Delegates rendering and feature generation to specialized helper classes.
 * Ensures visual representation uses the SAME algorithms as mathematical calculations.
 *
 * Refactored for file size compliance (<500 lines) - see:
 * - AATMapRenderer.kt: MapLibre rendering operations
 * - AATFeatureFactory.kt: GeoJSON feature creation
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 6 - Renderer Extraction)
 * DEPENDENCIES: SimpleAATTask, AATGeometryGenerator, MapLibre
 *
 * SSOT COMPLIANT: Uses AATGeometryGenerator for all geometry calculations.
 * ZERO RACING IMPORTS: Complete AAT/Racing separation maintained.
 */
class AATTaskRenderer(private val geometryGenerator: AATGeometryGenerator = AATGeometryGenerator()) {

    // Helper classes for rendering and feature creation
    private val mapRenderer = AATMapRenderer()
    private val featureFactory = AATFeatureFactory(geometryGenerator)

    /**
     * Plot AAT task on map
     *
     * Main entry point for rendering a complete AAT task with all
     * visual elements: waypoints, areas, lines, and target points.
     *
     * @param map The MapLibre map instance
     * @param task The task to render
     * @param editModeWaypointIndex Optional index of waypoint in edit mode (for red target point)
     */
    fun plotTaskOnMap(map: MapLibreMap?, task: SimpleAATTask, editModeWaypointIndex: Int? = null) {
        map?.getStyle { style ->
            if (task.waypoints.isNotEmpty()) {

                try {
                    // Clear existing AAT sources and layers
                    mapRenderer.clearLayers(style)

                    // Plot AAT waypoints as green circles
                    mapRenderer.plotWaypoints(style, task.waypoints)

                    // Plot AAT assigned areas (circles/sectors)
                    plotAreas(style, task.waypoints)

                    // Plot AAT task line through target points
                    mapRenderer.plotTaskLine(style, geometryGenerator, task.waypoints)

                    // Plot movable target point pins at line intersections (with edit mode coloring)
                    mapRenderer.plotTargetPointPins(style, task.waypoints, editModeWaypointIndex)

                } catch (e: Exception) {
                    logRenderFailure("plotTaskOnMap", e)
                }
            } else {
                // Clear AAT layers when no waypoints
                mapRenderer.clearLayers(map.style)
            }
        }
    }

    /**
     * Preview-only update for AAT drag sessions.
     * Updates target pin + task line without clearing/rebuilding all AAT layers.
     */
    fun previewTargetPointAndTaskLine(
        map: MapLibreMap?,
        task: SimpleAATTask,
        waypointIndex: Int,
        latitude: Double,
        longitude: Double,
        editModeWaypointIndex: Int? = null
    ) {
        if (waypointIndex !in task.waypoints.indices) return
        map?.getStyle { style ->
            val previewWaypoints = task.waypoints.toMutableList()
            val waypoint = previewWaypoints[waypointIndex]
            previewWaypoints[waypointIndex] = waypoint.copy(
                targetPoint = waypoint.targetPoint.copy(
                    latitude = latitude,
                    longitude = longitude
                ),
                isTargetPointCustomized = true
            )
            val previewPath = geometryGenerator.calculateOptimalAATPath(previewWaypoints)
            mapRenderer.upsertTaskLine(style, previewPath)
            mapRenderer.plotTargetPointPins(style, previewWaypoints, editModeWaypointIndex)
        }
    }

    /**
     * Clear AAT task visuals from map
     *
     * Removes all AAT-specific layers and sources from the map.
     * Maintains ZERO cross-contamination with Racing/DHT task types.
     *
     * @param map The MapLibre map instance
     */
    fun clearTaskFromMap(map: MapLibreMap?) {
        map?.getStyle { style ->

            // Remove AAT-specific layers only - NO shared layer names
            val aatLayers = listOf(
                "aat-waypoints",
                "aat-areas",
                "aat-areas-layer",
                "aat-borders-layer",
                "aat-task-line",
                "aat-target-points",
                "aat-target-points-layer",
                "aat-assigned-areas",
                "aat-sectors",
                "aat-circles",
                "aat-lines-layer"
            )

            aatLayers.forEach { layerId ->
                try {
                    if (style.getLayer(layerId) != null) {
                        style.removeLayer(layerId)
                    }
                } catch (e: Exception) {
                }
            }

            // Remove AAT-specific sources only
            val aatSources = listOf(
                "aat-waypoints",
                "aat-areas",
                "aat-task-line",
                "aat-target-points",
                "aat-assigned-areas-source",
                "aat-sectors-source",
                "aat-lines"
            )

            aatSources.forEach { sourceId ->
                try {
                    if (style.getSource(sourceId) != null) {
                        style.removeSource(sourceId)
                    }
                } catch (e: Exception) {
                }
            }

        }
    }

    // ==================== Private Rendering Methods ====================

    /**
     * Plot AAT areas and start/finish lines
     *
     * Delegates feature creation to AATFeatureFactory and rendering to AATMapRenderer.
     */
    private fun plotAreas(style: Style, waypoints: List<AATWaypoint>) {
        try {
            if (waypoints.isEmpty()) return


            // Separate areas and lines
            val areaFeatures = mutableListOf<String>()
            val lineFeatures = mutableListOf<String>()

            waypoints.forEachIndexed { index, waypoint ->
                when (waypoint.role) {
                    AATWaypointRole.START -> {
                        when (waypoint.startPointType) {
                            com.trust3.xcpro.tasks.aat.models.AATStartPointType.AAT_START_LINE -> {
                                val nextWaypoint = if (index + 1 < waypoints.size) waypoints[index + 1] else null
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val lineWidthMeters = waypoint.getAuthorityRadiusMeters()
                                val lineCoordinates =
                                    geometryGenerator.generateStartLineMeters(waypoint, nextWaypoint, lineWidthMeters)
                                lineFeatures.add(featureFactory.createLineFeature(waypoint, lineCoordinates, "aat_start_line", "START"))
                            }
                            com.trust3.xcpro.tasks.aat.models.AATStartPointType.AAT_START_CYLINDER -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusMeters = waypoint.getAuthorityRadiusMeters()
                                val circleCoordinates =
                                    geometryGenerator.generateCircleCoordinatesMeters(
                                        waypoint.lat,
                                        waypoint.lon,
                                        radiusMeters
                                    )
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusMeters, "aat_start_cylinder", "START"))
                            }
                            else -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusMeters = waypoint.getAuthorityRadiusMeters()
                                val circleCoordinates =
                                    geometryGenerator.generateCircleCoordinatesMeters(
                                        waypoint.lat,
                                        waypoint.lon,
                                        radiusMeters
                                    )
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusMeters, "aat_start_area", null))
                            }
                        }
                    }
                    AATWaypointRole.FINISH -> {
                        when (waypoint.finishPointType) {
                            com.trust3.xcpro.tasks.aat.models.AATFinishPointType.AAT_FINISH_LINE -> {
                                val prevWaypoint = if (index > 0) waypoints[index - 1] else null
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val lineWidthMeters = waypoint.getAuthorityRadiusMeters()
                                val lineCoordinates =
                                    geometryGenerator.generateFinishLineMeters(waypoint, prevWaypoint, lineWidthMeters)
                                lineFeatures.add(featureFactory.createLineFeature(waypoint, lineCoordinates, "aat_finish_line", "FINISH"))
                            }
                            else -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusMeters = waypoint.getAuthorityRadiusMeters()
                                val circleCoordinates =
                                    geometryGenerator.generateCircleCoordinatesMeters(
                                        waypoint.lat,
                                        waypoint.lon,
                                        radiusMeters
                                    )
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusMeters, "aat_finish_cylinder", "FINISH"))
                            }
                        }
                    }
                    else -> {
                        //  COMPETITION-CRITICAL: Use AUTHORITATIVE radius from AATRadiusAuthority
                        when (waypoint.assignedArea.shape) {
                            com.trust3.xcpro.tasks.aat.models.AATAreaShape.CIRCLE -> {
                                // Use waypoint.getAuthorityRadiusMeters() to guarantee UI/map consistency.
                                val radiusMeters = waypoint.getAuthorityRadiusMeters()
                                val circleCoordinates =
                                    geometryGenerator.generateCircleCoordinatesMeters(
                                        waypoint.lat,
                                        waypoint.lon,
                                        radiusMeters
                                    )
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusMeters, "aat_area", "TURNPOINT"))
                            }
                            com.trust3.xcpro.tasks.aat.models.AATAreaShape.SECTOR -> {
                                // Sector/Keyhole: Generate sector boundary points
                                val sectorCoordinates = featureFactory.generateSectorCoordinates(
                                    waypoint.lat, waypoint.lon,
                                    waypoint.assignedArea.innerRadiusMeters,
                                    waypoint.assignedArea.outerRadiusMeters,
                                    waypoint.assignedArea.startAngleDegrees,
                                    waypoint.assignedArea.endAngleDegrees
                                )
                                areaFeatures.add(featureFactory.createSectorFeature(waypoint, sectorCoordinates, "aat_sector", "TURNPOINT"))
                            }
                            com.trust3.xcpro.tasks.aat.models.AATAreaShape.LINE -> {
                                // Line (shouldn't happen for turnpoints, but handle gracefully)
                                val radiusMeters = waypoint.assignedArea.radiusMeters
                                val circleCoordinates =
                                    geometryGenerator.generateCircleCoordinatesMeters(
                                        waypoint.lat,
                                        waypoint.lon,
                                        waypoint.assignedArea.radiusMeters
                                    )
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusMeters, "aat_area", "TURNPOINT"))
                            }
                        }
                    }
                }
            }

            // Add area features if any exist
            if (areaFeatures.isNotEmpty()) {
                mapRenderer.addAreaFeatures(style, areaFeatures)
            }

            // Add line features if any exist
            if (lineFeatures.isNotEmpty()) {
                mapRenderer.addLineFeatures(style, lineFeatures)
            }
        } catch (e: Exception) {
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
        const val TAG = "AATTaskRenderer"
    }
}
