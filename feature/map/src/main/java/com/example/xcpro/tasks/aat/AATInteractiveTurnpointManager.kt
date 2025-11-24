package com.example.xcpro.tasks.aat

import androidx.compose.runtime.*
import com.example.xcpro.tasks.aat.calculations.AATDistanceCalculator
import com.example.xcpro.tasks.aat.calculations.AATInteractiveTaskDistance
import com.example.xcpro.tasks.aat.map.*
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

@Composable
fun rememberAATInteractiveTurnpointManager(
    aatWaypoints: List<AATWaypoint>,
    callbacks: AATManagerCallbacks = AATManagerCallbacks()
): AATInteractiveTurnpointManager {
    return remember {
        AATInteractiveTurnpointManager(callbacks)
    }.apply {
        updateWaypoints(aatWaypoints)
    }
}

class AATInteractiveTurnpointManager(
    private val callbacks: AATManagerCallbacks
) {
    // Core components
    private val distanceCalculator = AATDistanceCalculator()
    private val movablePointManager = AATMovablePointManager()
    private var mapInteractionHandler: AATMapInteractionHandler? = null
    private var coordinateConverter: AATMapCoordinateConverter? = null

    // State management
    private var currentWaypoints by mutableStateOf(emptyList<AATWaypoint>())
    private var currentDistance by mutableStateOf<AATInteractiveTaskDistance?>(null)
    private var isEditMode by mutableStateOf(false)
    private var focusedAreaIndex by mutableStateOf(-1)

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
                )
            )
            mapInteractionHandler?.attachToMap(map)

            println("🎯 AAT: Interactive turnpoint manager attached to map")
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

        println("🎯 AAT: Updated waypoints (${newWaypoints.size} waypoints)")
    }

    /**
     * Get current edit mode state
     */
    fun isInEditMode(): Boolean = isEditMode

    // Note: getCurrentWaypoints(), getCurrentDistance(), and getFocusedAreaIndex()
    // are automatically provided by the mutableStateOf properties above

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
            println("🎯 AAT: Moved waypoint $areaIndex to strategic position")
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
        println("🎯 AAT: Reset waypoint $areaIndex to center")
    }

    /**
     * Calculate and get task distance limits (min/max)
     */
    fun getDistanceLimits(): Pair<Double, Double> {
        if (currentWaypoints.isEmpty()) return Pair(0.0, 0.0)

        val minWaypoints = currentWaypoints.map { it.copy(targetPoint = AATLatLng(it.lat, it.lon)) }
        val maxWaypoints = distanceCalculator.optimizeTargetPointsForMaxDistance(currentWaypoints)

        val minDistance = distanceCalculator.calculateInteractiveTaskDistance(minWaypoints).totalDistance
        val maxDistance = distanceCalculator.calculateInteractiveTaskDistance(maxWaypoints).totalDistance

        return Pair(minDistance, maxDistance)
    }

    // ========== Event Handlers ==========

    private fun handleAreaTapped(index: Int, waypoint: AATWaypoint) {
        println("🎯 AAT: Area $index tapped (${waypoint.title})")
        // Area tap is handled by the interaction handler
    }

    private fun handleEditModeEntered(index: Int, waypoint: AATWaypoint) {
        isEditMode = true
        focusedAreaIndex = index
        callbacks.onEditModeChanged(true, index)
        println("🎯 AAT: Entered edit mode for area $index (${waypoint.title})")
    }

    private fun handleEditModeExited() {
        isEditMode = false
        focusedAreaIndex = -1
        callbacks.onEditModeChanged(false, -1)
        println("🎯 AAT: Exited edit mode")
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
        println("AAT: Updated waypoint $index target point to ${String.format("%.6f", tp.latitude)}, ${String.format("%.6f", tp.longitude)}")
    }

    private fun updateDistance() {
        if (currentWaypoints.isEmpty()) {
            currentDistance = null
            return
        }

        val distance = distanceCalculator.calculateInteractiveTaskDistance(currentWaypoints)
        currentDistance = distance
        callbacks.onDistanceUpdated(distance)

        println("🎯 AAT: Updated task distance: ${String.format("%.2f", distance.totalDistance)} km (${distance.calculationTime}ms)")
    }
}

/**
 * Composable for AAT Interactive Turnpoint integration
 */
@Composable
fun AATInteractiveTurnpointIntegration(
    aatWaypoints: List<AATWaypoint>,
    mapLibreMap: MapLibreMap?,
    onWaypointUpdated: (Int, AATWaypoint) -> Unit,
    onDistanceUpdated: (AATInteractiveTaskDistance) -> Unit = {},
    onEditModeChanged: (Boolean, Int) -> Unit = { _, _ -> },
    onMapCameraUpdate: (Double, Double, Float) -> Unit = { _, _, _ -> },
    onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null },
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val manager = rememberAATInteractiveTurnpointManager(
        aatWaypoints = aatWaypoints,
        callbacks = AATManagerCallbacks(
            onWaypointUpdated = onWaypointUpdated,
            onDistanceUpdated = onDistanceUpdated,
            onEditModeChanged = onEditModeChanged,
            onMapCameraUpdate = onMapCameraUpdate,
            onCheckTargetPointHit = onCheckTargetPointHit
        )
    )

    // Attach to map when available
    LaunchedEffect(mapLibreMap) {
        manager.attachToMap(mapLibreMap)
    }

    // Update waypoints when they change
    LaunchedEffect(aatWaypoints) {
        manager.updateWaypoints(aatWaypoints)
    }

    // Interactive overlay
    if (mapLibreMap != null) {
        com.example.xcpro.tasks.aat.ui.AATOverlayFactory.CreateInteractiveOverlay(
            aatWaypoints = aatWaypoints,
            mapLibreMap = mapLibreMap,
            onTargetPointUpdated = { index: Int, newTargetPoint: AATLatLng ->
                // Convert AATLatLng update to AATWaypoint update
                if (index < aatWaypoints.size) {
                    val updatedWaypoint = aatWaypoints[index].copy(
                        targetPoint = newTargetPoint,
                        isTargetPointCustomized = true
                    )
                    onWaypointUpdated(index, updatedWaypoint)
                }
            },
            onEditModeChanged = onEditModeChanged,
            onCheckTargetPointHit = onCheckTargetPointHit,
            modifier = modifier
        )
    }
}

/**
 * Factory for creating AAT interactive turnpoint managers
 */
object AATInteractiveTurnpointManagerFactory {

    /**
     * Create manager for full interactive mode
     */
    fun createInteractiveManager(
        callbacks: AATManagerCallbacks
    ): AATInteractiveTurnpointManager {
        return AATInteractiveTurnpointManager(callbacks)
    }

    /**
     * Create manager for read-only display mode
     */
    fun createDisplayManager(
        onDistanceUpdated: (AATInteractiveTaskDistance) -> Unit = {}
    ): AATInteractiveTurnpointManager {
        return AATInteractiveTurnpointManager(
            AATManagerCallbacks(
                onDistanceUpdated = onDistanceUpdated
            )
        )
    }
}
