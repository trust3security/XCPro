package com.example.xcpro.tasks.aat

import com.example.xcpro.core.time.Clock
import com.example.xcpro.tasks.aat.calculations.AATDistanceCalculator
import com.example.xcpro.tasks.aat.calculations.AATInteractiveTaskDistance
import com.example.xcpro.tasks.aat.map.AATInteractionCallbacks
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverter
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverterFactory
import com.example.xcpro.tasks.aat.map.AATMapInteractionHandler
import com.example.xcpro.tasks.aat.map.AATMovablePointManager
import com.example.xcpro.tasks.aat.models.*
import org.maplibre.android.maps.MapLibreMap

/**
 * AAT Interactive Turnpoint Manager - Phase 4 Integration
 *
 * Main controller that integrates all AAT interactive turnpoint components.
 * This is the primary interface between the MapScreen and AAT interactive features.
 *
 * Features:
 * - Centralized state management for AAT interactions
 * - Real-time distance calculation and updates
 * - Coordinate conversion and map integration
 * - Edit mode lifecycle management
 * - Callback system for UI updates
 *
 * Usage in MapScreen:
 * ```kotlin
 * val aatManager = rememberAATInteractiveTurnpointManager(
 *     aatWaypoints = aatWaypoints,
 *     clock = clock,
 *     onWaypointUpdated = { index, updatedWaypoint ->
 *         // Update AAT task with new waypoint
 *     }
 * )
 *
 * LaunchedEffect(mapLibreMap) {
 *     aatManager.attachToMap(mapLibreMap)
 * }
 * ```
 */

data class AATManagerCallbacks(
    val onWaypointUpdated: (Int, AATWaypoint) -> Unit = { _, _ -> },
    val onDistanceUpdated: (AATInteractiveTaskDistance) -> Unit = {},
    val onEditModeChanged: (Boolean, Int) -> Unit = { _, _ -> },
    val onMapCameraUpdate: (lat: Double, lon: Double, zoom: Float) -> Unit = { _, _, _ -> },
    val onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null }
)

class AATInteractiveTurnpointManager(
    private val callbacks: AATManagerCallbacks,
    private val clock: Clock
) {
    // Core components
    private val distanceCalculator = AATDistanceCalculator()
    private val movablePointManager = AATMovablePointManager()
    private var mapInteractionHandler: AATMapInteractionHandler? = null
    private var coordinateConverter: AATMapCoordinateConverter? = null

    // State management
    private var currentWaypoints: List<AATWaypoint> = emptyList()
    private var currentDistance: AATInteractiveTaskDistance? = null
    private var isEditMode: Boolean = false
    private var focusedAreaIndex: Int = -1

    // Map reference
    private var mapLibreMap: MapLibreMap? = null

    /**
     * Attach to MapLibre map instance
     */
    fun attachToMap(map: MapLibreMap?) {
        mapLibreMap = map
        coordinateConverter = map?.let { AATMapCoordinateConverterFactory.create(it) }

        // Initialize map interaction handler
        if (map != null) {
            mapInteractionHandler = AATMapInteractionHandler(
                aatWaypoints = currentWaypoints,
                callbacks = AATInteractionCallbacks(
                    onAreaTapped = { index, waypoint ->
                        handleAreaTapped(index, waypoint)
                    },
                    onEditModeEntered = { index, waypoint ->
                        handleEditModeEntered(index, waypoint)
                    },
                    onEditModeExited = {
                        handleEditModeExited()
                    },
                    onTargetPointMoved = { index, newPoint ->
                        handleTargetPointMoved(index, newPoint)
                    },
                    onZoomToArea = { waypoint, zoom ->
                        handleZoomToArea(waypoint, zoom)
                    },
                    onZoomToOverview = { zoom ->
                        handleZoomToOverview(zoom)
                    }
                ),
                clock = clock
            )
            mapInteractionHandler?.attachToMap(map)

        }
    }

    /**
     * Update waypoints list
     */
    fun updateWaypoints(newWaypoints: List<AATWaypoint>) {
        currentWaypoints = newWaypoints
        mapInteractionHandler?.updateWaypoints(newWaypoints)

        // Recalculate distance
        if (newWaypoints.isNotEmpty()) {
            updateDistance()
        }

    }

    /**
     * Get current edit mode state
     */
    fun isInEditMode(): Boolean = isEditMode

    fun getCurrentDistance(): AATInteractiveTaskDistance? = currentDistance

    fun getFocusedAreaIndex(): Int = focusedAreaIndex

    /**
     * Get map interaction handler for UI integration
     */
    fun getMapInteractionHandler(): AATMapInteractionHandler? = mapInteractionHandler

    /**
     * Get coordinate converter for UI components
     */
    fun getCoordinateConverter(): AATMapCoordinateConverter? = coordinateConverter

    /**
     * Manually exit edit mode
     */
    fun exitEditMode() {
        mapInteractionHandler?.exitEditMode()
    }

    internal fun getCurrentWaypoints(): List<AATWaypoint> = currentWaypoints

    /**
     * Move target point to strategic position
     */
    fun moveToStrategicPosition(areaIndex: Int) {
        if (areaIndex < 0 || areaIndex >= currentWaypoints.size) return

        val waypoint = currentWaypoints[areaIndex]
        val optimizedWaypoints = distanceCalculator.optimizeTargetPointsForMaxDistance(
            listOf(waypoint)
        )

        if (optimizedWaypoints.isNotEmpty()) {
            val newTargetPoint = optimizedWaypoints[0].targetPoint
            updateWaypointTargetPoint(areaIndex, newTargetPoint)
        }
    }

    /**
     * Reset target point to area center
     */
    fun resetToCenter(areaIndex: Int) {
        if (areaIndex < 0 || areaIndex >= currentWaypoints.size) return

        val waypoint = currentWaypoints[areaIndex]
        val centerPoint = AATLatLng(waypoint.lat, waypoint.lon)
        updateWaypointTargetPoint(areaIndex, centerPoint)
    }

    /**
     * Calculate and get task distance limits (min/max)
     */
    fun getDistanceLimitsMeters(): Pair<Double, Double> {
        if (currentWaypoints.isEmpty()) return Pair(0.0, 0.0)

        val minWaypoints = currentWaypoints.map { it.copy(targetPoint = AATLatLng(it.lat, it.lon)) }
        val maxWaypoints = distanceCalculator.optimizeTargetPointsForMaxDistance(currentWaypoints)

        val minDistance = distanceCalculator.calculateInteractiveTaskDistance(minWaypoints).totalDistanceMeters
        val maxDistance = distanceCalculator.calculateInteractiveTaskDistance(maxWaypoints).totalDistanceMeters

        return Pair(minDistance, maxDistance)
    }

    // ========== Event Handlers ==========

    private fun handleAreaTapped(index: Int, waypoint: AATWaypoint) {
        // Area tap is handled by the interaction handler
    }

    private fun handleEditModeEntered(index: Int, waypoint: AATWaypoint) {
        isEditMode = true
        focusedAreaIndex = index
        callbacks.onEditModeChanged(true, index)
    }

    private fun handleEditModeExited() {
        isEditMode = false
        focusedAreaIndex = -1
        callbacks.onEditModeChanged(false, -1)
    }

    private fun handleTargetPointMoved(index: Int, newPoint: AATLatLng) {
        updateWaypointTargetPoint(index, newPoint)
    }

    private fun handleZoomToArea(waypoint: AATWaypoint, zoom: Float) {
        callbacks.onMapCameraUpdate(waypoint.lat, waypoint.lon, zoom)
    }

    private fun handleZoomToOverview(zoom: Float) {
        // Calculate center of all waypoints for overview
        if (currentWaypoints.isNotEmpty()) {
            val centerLat = currentWaypoints.map { it.lat }.average()
            val centerLon = currentWaypoints.map { it.lon }.average()
            callbacks.onMapCameraUpdate(centerLat, centerLon, zoom)
        }
    }

    // ========== Private Methods ==========

    internal fun updateWaypointTargetPoint(index: Int, newTargetPoint: AATLatLng) {
        if (index < 0 || index >= currentWaypoints.size) return

        val waypoint = currentWaypoints[index]
        // Clamp through shared geometry manager to keep it inside the displayed area
        val clampedWaypoint = movablePointManager.moveTargetPoint(
            waypoint,
            newTargetPoint.latitude,
            newTargetPoint.longitude
        )

        val updatedWaypoints = currentWaypoints.toMutableList()
        updatedWaypoints[index] = clampedWaypoint
        currentWaypoints = updatedWaypoints

        // Update distance calculation
        updateDistance()

        // Notify callbacks
        callbacks.onWaypointUpdated(index, clampedWaypoint)

        val tp = clampedWaypoint.targetPoint
    }

    private fun updateDistance() {
        if (currentWaypoints.isEmpty()) {
            currentDistance = null
            return
        }

        val distance = distanceCalculator.calculateInteractiveTaskDistance(currentWaypoints)
        currentDistance = distance
        callbacks.onDistanceUpdated(distance)

    }
}
