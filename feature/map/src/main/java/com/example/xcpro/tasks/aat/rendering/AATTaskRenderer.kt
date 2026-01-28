package com.example.xcpro.tasks.aat.rendering

import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.getAuthorityRadius  //  COMPETITION-CRITICAL import
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator
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
                println(" AAT RENDERER: Plotting ${task.waypoints.size} AAT waypoints on map")

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

                    println(" AAT RENDERER: Successfully plotted AAT task on map")
                } catch (e: Exception) {
                    println(" AAT RENDERER: Error plotting AAT task: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                println(" AAT RENDERER: No waypoints to plot")
                // Clear AAT layers when no waypoints
                mapRenderer.clearLayers(map.style)
            }
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
            println(" AAT RENDERER: Removing AAT-specific map layers")

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
                        println(" AAT RENDERER: Removed layer: $layerId")
                    }
                } catch (e: Exception) {
                    println(" AAT RENDERER: Could not remove layer $layerId: ${e.message}")
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
                        println(" AAT RENDERER: Removed source: $sourceId")
                    }
                } catch (e: Exception) {
                    println(" AAT RENDERER: Could not remove source $sourceId: ${e.message}")
                }
            }

            println(" AAT RENDERER: AAT map cleanup completed")
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

            println(" AAT RENDERER: Plotting areas and start/finish lines for ${waypoints.size} waypoints")

            // Separate areas and lines
            val areaFeatures = mutableListOf<String>()
            val lineFeatures = mutableListOf<String>()

            waypoints.forEachIndexed { index, waypoint ->
                when (waypoint.role) {
                    AATWaypointRole.START -> {
                        when (waypoint.startPointType) {
                            com.example.xcpro.tasks.aat.models.AATStartPointType.AAT_START_LINE -> {
                                val nextWaypoint = if (index + 1 < waypoints.size) waypoints[index + 1] else null
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val lineWidth = waypoint.getAuthorityRadius()
                                val lineCoordinates = geometryGenerator.generateStartLine(waypoint, nextWaypoint, lineWidth)
                                lineFeatures.add(featureFactory.createLineFeature(waypoint, lineCoordinates, "aat_start_line", "START"))
                                println(" AAT RENDERER: Generated start line for ${waypoint.title} (${lineWidth}km wide)")
                            }
                            com.example.xcpro.tasks.aat.models.AATStartPointType.AAT_START_CYLINDER -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusKm = waypoint.getAuthorityRadius() / 2.0
                                val circleCoordinates = geometryGenerator.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusKm, "aat_start_cylinder", "START"))
                                println(" AAT RENDERER: Generated start cylinder for ${waypoint.title} (${radiusKm}km radius)")
                            }
                            else -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusKm = waypoint.getAuthorityRadius() / 2.0
                                val circleCoordinates = geometryGenerator.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusKm, "aat_start_area", null))
                            }
                        }
                    }
                    AATWaypointRole.FINISH -> {
                        when (waypoint.finishPointType) {
                            com.example.xcpro.tasks.aat.models.AATFinishPointType.AAT_FINISH_LINE -> {
                                val prevWaypoint = if (index > 0) waypoints[index - 1] else null
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val lineWidth = waypoint.getAuthorityRadius()
                                val lineCoordinates = geometryGenerator.generateFinishLine(waypoint, prevWaypoint, lineWidth)
                                lineFeatures.add(featureFactory.createLineFeature(waypoint, lineCoordinates, "aat_finish_line", "FINISH"))
                                println(" AAT RENDERER: Generated finish line for ${waypoint.title} (${lineWidth}km wide)")
                            }
                            else -> {
                                //  SSOT FIX: Use authority instead of removed gateWidth property
                                val radiusKm = waypoint.getAuthorityRadius() / 2.0
                                val circleCoordinates = geometryGenerator.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusKm, "aat_finish_cylinder", "FINISH"))
                            }
                        }
                    }
                    else -> {
                        //  COMPETITION-CRITICAL: Use AUTHORITATIVE radius from AATRadiusAuthority
                        when (waypoint.assignedArea.shape) {
                            com.example.xcpro.tasks.aat.models.AATAreaShape.CIRCLE -> {
                                //  CRITICAL: Use waypoint.getAuthorityRadius() to guarantee UI/map consistency
                                val radiusKm = waypoint.getAuthorityRadius()
                                val circleCoordinates = geometryGenerator.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusKm, "aat_area", "TURNPOINT"))
                                println(" AAT RENDERER: Generated CIRCLE for ${waypoint.title} (${radiusKm}km radius - AUTHORITY)")
                            }
                            com.example.xcpro.tasks.aat.models.AATAreaShape.SECTOR -> {
                                // Sector/Keyhole: Generate sector boundary points
                                val sectorCoordinates = featureFactory.generateSectorCoordinates(
                                    waypoint.lat, waypoint.lon,
                                    waypoint.assignedArea.innerRadiusMeters / 1000.0, // Convert to km
                                    waypoint.assignedArea.outerRadiusMeters / 1000.0,
                                    waypoint.assignedArea.startAngleDegrees,
                                    waypoint.assignedArea.endAngleDegrees
                                )
                                areaFeatures.add(featureFactory.createSectorFeature(waypoint, sectorCoordinates, "aat_sector", "TURNPOINT"))
                                println(" AAT RENDERER: Generated SECTOR for ${waypoint.title}")
                                println("   - Inner: ${waypoint.assignedArea.innerRadiusMeters/1000.0}km, Outer: ${waypoint.assignedArea.outerRadiusMeters/1000.0}km")
                                println("   - Angles: ${waypoint.assignedArea.startAngleDegrees} to ${waypoint.assignedArea.endAngleDegrees}")
                            }
                            com.example.xcpro.tasks.aat.models.AATAreaShape.LINE -> {
                                // Line (shouldn't happen for turnpoints, but handle gracefully)
                                val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                                val circleCoordinates = geometryGenerator.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusKm)
                                areaFeatures.add(featureFactory.createCircleFeature(waypoint, circleCoordinates, radiusKm, "aat_area", "TURNPOINT"))
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
            println(" AAT RENDERER: Error plotting areas: ${e.message}")
        }
    }
}
