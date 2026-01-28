package com.example.xcpro.tasks.racing

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.camera.CameraUpdateFactory
import com.example.xcpro.tasks.racing.models.*
import com.example.xcpro.tasks.racing.turnpoints.*

// Interface for racing task calculator needed by display operations
interface RacingTaskCalculatorInterface {
    fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>>
}

/**
 * Racing Task Display - Main coordinator for racing task visualization
 *
 * Delegates rendering and geometry generation to specialized helper classes.
 * Ensures visual representation uses the SAME algorithms as mathematical calculations.
 *
 * Refactored for file size compliance (<500 lines) - see:
 * - RacingMapRenderer.kt: MapLibre rendering operations
 * - RacingGeometryCoordinator.kt: Geometry generation routing
 */
class RacingTaskDisplay {

    // Turnpoint display classes - each type has its own specialized implementation
    private val faiQuadrantDisplay = FAIQuadrantDisplay()
    private val cylinderDisplay = CylinderDisplay()
    private val keyholeDisplay = KeyholeDisplay()
    private val startLineDisplay = StartLineDisplay()
    private val finishLineDisplay = FinishLineDisplay()
    private val faiStartSectorDisplay = FAIStartSectorDisplay()

    // Helper classes for rendering and geometry generation
    private val geometryCoordinator = RacingGeometryCoordinator(
        faiQuadrantDisplay,
        cylinderDisplay,
        keyholeDisplay,
        startLineDisplay,
        finishLineDisplay,
        faiStartSectorDisplay
    )
    private val mapRenderer = RacingMapRenderer()

    /**
     * Generate turnpoint visual geometry for racing tasks
     * Delegates to appropriate specialized display class
     */
    fun generateTurnPointFeature(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String? {
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = if (index > 0) allWaypoints[index - 1] else null,
            nextWaypoint = if (index < allWaypoints.size - 1) allWaypoints[index + 1] else null
        )

        return when (waypoint.turnPointType) {
            RacingTurnPointType.FAI_QUADRANT -> {
                faiQuadrantDisplay.generateVisualGeometry(waypoint, context)
            }
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                cylinderDisplay.generateVisualGeometry(waypoint, context)
            }
            RacingTurnPointType.KEYHOLE -> {
                // SPECIAL HANDLING: Keyhole returns FeatureCollection, extract features
                val keyholeResult = keyholeDisplay.generateVisualGeometry(waypoint, context)
                // For now, return the FeatureCollection as-is and let TaskManager handle parsing
                keyholeResult
            }
        }
    }

    /**
     * Generate finish point visual geometry for racing tasks
     * Currently handles finish cylinders and FAI finish quadrants
     */
    fun generateFinishPointFeature(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String? {
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = if (index > 0) allWaypoints[index - 1] else null,
            nextWaypoint = null // Finish point has no next waypoint
        )

        return when (waypoint.finishPointType) {
            RacingFinishPointType.FINISH_CYLINDER -> {
                cylinderDisplay.generateVisualGeometry(waypoint, context)
            }
            RacingFinishPointType.FINISH_LINE -> {
                finishLineDisplay.generateVisualGeometry(waypoint, context) // Use proper FinishLineDisplay
            }
        }
    }

    /**
     * Get display radius for a turnpoint (for map bounds calculation)
     */
    fun getTurnPointDisplayRadius(waypoint: RacingWaypoint): Double {
        return when (waypoint.turnPointType) {
            RacingTurnPointType.FAI_QUADRANT -> {
                faiQuadrantDisplay.getDisplayRadius(waypoint)
            }
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                cylinderDisplay.getDisplayRadius(waypoint)
            }
            RacingTurnPointType.KEYHOLE -> {
                keyholeDisplay.getDisplayRadius(waypoint)
            }
        }
    }

    /**
     * Get observation zone type string for a turnpoint
     */
    fun getTurnPointObservationZoneType(waypoint: RacingWaypoint): String {
        return when (waypoint.turnPointType) {
            RacingTurnPointType.FAI_QUADRANT -> {
                faiQuadrantDisplay.getObservationZoneType()
            }
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                cylinderDisplay.getObservationZoneType()
            }
            RacingTurnPointType.KEYHOLE -> {
                keyholeDisplay.getObservationZoneType()
            }
        }
    }

    /**
     * Plot Racing task on map - main map display entry point
     */
    fun plotRacingOnMap(map: MapLibreMap?, waypoints: List<RacingWaypoint>, racingTaskCalculator: RacingTaskCalculatorInterface) {
        map?.getStyle { style ->
            if (waypoints.isNotEmpty()) {
                println(" RACING TASK: Plotting ${waypoints.size} racing waypoints on map")

                // Draw waypoints and geometry using mapRenderer
                mapRenderer.drawRacingWaypoints(style, map, waypoints, geometryCoordinator)

                // Draw course line if multiple waypoints
                if (waypoints.size > 1) {
                    mapRenderer.drawRacingCourseLine(style, waypoints, racingTaskCalculator)
                }

                // Center map on task
                centerMapOnRacingTask(map, waypoints)
            }
        }
    }

    /**
     * Center map on Racing task
     */
    fun centerMapOnRacingTask(map: MapLibreMap, waypoints: List<RacingWaypoint>) {
        if (waypoints.isNotEmpty()) {
            if (waypoints.size == 1) {
                // Single waypoint - just center on it
                val waypoint = waypoints[0]
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(waypoint.lat, waypoint.lon), 12.0),
                    1000
                )
            } else {
                // Multiple waypoints - use bounds
                val bounds = LatLngBounds.Builder()
                waypoints.forEach { waypoint ->
                    bounds.include(LatLng(waypoint.lat, waypoint.lon))
                }

                val padding = 100
                map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds.build(), padding),
                    1000
                )
            }
        }
    }

    /**
     * Clear Racing task visuals from map - Racing-specific cleanup only
     * Maintains ZERO cross-contamination with AAT/other task types
     */
    fun clearRacingFromMap(map: MapLibreMap?) {
        map?.getStyle { style ->
            println(" RACING CLEANUP: Removing Racing-specific map layers")

            // Remove Racing-specific layers only - NO shared layer names
            val racingLayers = listOf(
                "racing-course-line",
                "racing-waypoint-markers",
                "racing-cylinders",
                "racing-start-lines",
                "racing-finish-cylinders",
                "racing-turnpoint-sectors",
                "racing-keyhole-sectors",
                "racing-fai-quadrants"
            )

            racingLayers.forEach { layerId ->
                try {
                    if (style.getLayer(layerId) != null) {
                        style.removeLayer(layerId)
                        println(" RACING CLEANUP: Removed layer: $layerId")
                    }
                } catch (e: Exception) {
                    println(" RACING CLEANUP: Could not remove layer $layerId: ${e.message}")
                }
            }

            // Remove Racing-specific sources only
            val racingSources = listOf(
                "racing-course-source",
                "racing-waypoints-source",
                "racing-cylinders-source",
                "racing-sectors-source"
            )

            racingSources.forEach { sourceId ->
                try {
                    if (style.getSource(sourceId) != null) {
                        style.removeSource(sourceId)
                        println(" RACING CLEANUP: Removed source: $sourceId")
                    }
                } catch (e: Exception) {
                    println(" RACING CLEANUP: Could not remove source $sourceId: ${e.message}")
                }
            }

            println(" RACING CLEANUP: Racing map cleanup completed")
        }
    }
}
