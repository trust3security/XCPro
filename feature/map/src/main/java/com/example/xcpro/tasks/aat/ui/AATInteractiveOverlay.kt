package com.example.xcpro.tasks.aat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.xcpro.tasks.aat.map.*
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng

/**
 * AAT Interactive Overlay - Phase 2 Integration Component
 *
 * Main overlay component that combines all AAT interactive features:
 * - Edit mode visual feedback
 * - Target point drag and drop
 * - Real-time distance calculations
 * - Visual indicators and controls
 *
 * This component sits on top of the MapScreen and handles all AAT interaction.
 *
 * Features:
 * - Seamless integration with MapScreen
 * - State management for edit sessions
 * - Visual feedback and animations
 * - Coordinate conversion integration
 * - Callback system for map updates
 *
 * Usage in MapScreen:
 * ```kotlin
 * Box {
 *     AndroidView(...) { mapView -> ... } // Map view
 *
 *     AATInteractiveOverlay(
 *         aatWaypoints = aatWaypoints,
 *         mapLibreMap = mapLibreMap,
 *         onTargetPointUpdated = { index, newPoint ->
 *             // Update AAT task
 *         }
 *     )
 * }
 * ```
 */

data class AATInteractiveCallbacks(
    val onTargetPointUpdated: (Int, AATLatLng) -> Unit = { _, _ -> },
    val onEditModeChanged: (Boolean, Int) -> Unit = { _, _ -> },
    val onDistanceRecalculated: (Double) -> Unit = {},
    val onZoomToArea: (AATWaypoint, Float) -> Unit = { _, _ -> },
    val onZoomToOverview: (Float) -> Unit = {},
    val onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null }
)

@Composable
fun AATInteractiveOverlay(
    aatWaypoints: List<AATWaypoint>,
    mapLibreMap: org.maplibre.android.maps.MapLibreMap?,
    callbacks: AATInteractiveCallbacks = AATInteractiveCallbacks(),
    modifier: Modifier = Modifier
) {
    // State management
    val editModeManager = rememberAATEditModeState()
    val coordinateConverter = remember(mapLibreMap) {
        mapLibreMap?.let { AATMapCoordinateConverterFactory.create(it) }
    }

    // Interaction handler
    val interactionHandler = rememberAATMapInteractionHandler(
        aatWaypoints = aatWaypoints,
        callbacks = AATInteractionCallbacks(
            onAreaTapped = { index, waypoint ->
                println("🎯 AAT: Area $index tapped (${waypoint.title})")
            },
            onEditModeEntered = { index, waypoint ->
                callbacks.onEditModeChanged(true, index)
                callbacks.onZoomToArea(waypoint, 3.0f)
            },
            onEditModeExited = {
                callbacks.onEditModeChanged(false, -1)
                callbacks.onZoomToOverview(1.0f)
            },
            onTargetPointMoved = { index, newPoint ->
                callbacks.onTargetPointUpdated(index, newPoint)
            },
            onCheckTargetPointHit = { screenX, screenY ->
                callbacks.onCheckTargetPointHit(screenX, screenY)
            }
        )
    )

    // Attach interaction handler to map
    LaunchedEffect(mapLibreMap) {
        interactionHandler.attachToMap(mapLibreMap)
    }

    // Update waypoints when they change
    LaunchedEffect(aatWaypoints) {
        interactionHandler.updateWaypoints(aatWaypoints)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Map visual indicators overlay
        if (coordinateConverter != null) {
            BoxWithConstraints {
                val screenWidth = with(LocalDensity.current) { maxWidth.toPx() }
                val screenHeight = with(LocalDensity.current) { maxHeight.toPx() }

                AATMapVisualIndicators(
                    editSession = interactionHandler.getCurrentEditState(),
                    mapWidth = screenWidth,
                    mapHeight = screenHeight,
                    coordinateToPixel = { latLng ->
                        coordinateConverter.mapToScreen(
                            org.maplibre.android.geometry.LatLng(latLng.latitude, latLng.longitude)
                        )?.let { androidx.compose.ui.geometry.Offset(it.x, it.y) }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Edit mode status indicator (top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.Start
        ) {
            val editSession = interactionHandler.getCurrentEditState()
            if (editSession.isEditingArea && editSession.focusedWaypoint != null) {
                EditModeStatusIndicator(
                    isEditMode = true,
                    areaName = editSession.focusedWaypoint!!.title
                )
            }
        }

        // Edit mode controls (bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            val editSession = interactionHandler.getCurrentEditState()

            AATEditModeOverlay(
                editSession = editSession,
                onSaveChanges = {
                    val updatedWaypoint = interactionHandler.saveEditChanges()
                    if (updatedWaypoint != null) {
                        val index = editSession.focusedAreaIndex
                        callbacks.onTargetPointUpdated(index, updatedWaypoint.targetPoint)
                        println("💾 AAT: Saved changes for waypoint ${updatedWaypoint.title}")
                    }
                },
                onDiscardChanges = {
                    interactionHandler.discardEditChanges()
                    println("↩️ AAT: Discarded changes")
                },
                onResetToCenter = {
                    val waypoint = editSession.focusedWaypoint
                    if (waypoint != null) {
                        val centerPoint = AATLatLng(waypoint.lat, waypoint.lon)
                        interactionHandler.updateTargetPoint(centerPoint)
                        callbacks.onTargetPointUpdated(editSession.focusedAreaIndex, centerPoint)
                        println("🎯 AAT: Reset target point to center")
                    }
                },
                onExitEditMode = {
                    interactionHandler.exitEditMode()
                }
            )
        }

        // Apply map gesture handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(interactionHandler.pointerInputModifier)
        )
    }
}

/**
 * Simplified AAT overlay for read-only display
 */
@Composable
fun AATDisplayOverlay(
    aatWaypoints: List<AATWaypoint>,
    mapLibreMap: org.maplibre.android.maps.MapLibreMap?,
    modifier: Modifier = Modifier
) {
    val coordinateConverter = remember(mapLibreMap) {
        mapLibreMap?.let { AATMapCoordinateConverterFactory.create(it) }
    }

    if (coordinateConverter != null && aatWaypoints.isNotEmpty()) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize()
        ) {
            val screenWidth = with(LocalDensity.current) { maxWidth.toPx() }
            val screenHeight = with(LocalDensity.current) { maxHeight.toPx() }

            // Show only target point indicators (no edit functionality)
            aatWaypoints.forEachIndexed { index, waypoint ->
                val targetPixel = coordinateConverter.mapToScreen(
                    org.maplibre.android.geometry.LatLng(
                        waypoint.targetPoint.latitude,
                        waypoint.targetPoint.longitude
                    )
                )

                if (targetPixel != null) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (targetPixel.x - 16).dp,
                                y = (targetPixel.y - 16).dp
                            )
                    ) {
                        AnimatedTargetPointIndicator(
                            isVisible = true,
                            hasUnsavedChanges = waypoint.isTargetPointCustomized,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * AAT overlay factory for different modes
 */
object AATOverlayFactory {

    /**
     * Create interactive overlay for AAT task editing
     */
    @Composable
    fun CreateInteractiveOverlay(
        aatWaypoints: List<AATWaypoint>,
        mapLibreMap: org.maplibre.android.maps.MapLibreMap?,
        onTargetPointUpdated: (Int, AATLatLng) -> Unit,
        onEditModeChanged: (Boolean, Int) -> Unit = { _, _ -> },
        onCheckTargetPointHit: (Float, Float) -> Int? = { _, _ -> null },
        modifier: Modifier = Modifier
    ) {
        AATInteractiveOverlay(
            aatWaypoints = aatWaypoints,
            mapLibreMap = mapLibreMap,
            callbacks = AATInteractiveCallbacks(
                onTargetPointUpdated = onTargetPointUpdated,
                onEditModeChanged = onEditModeChanged,
                onCheckTargetPointHit = onCheckTargetPointHit
            ),
            modifier = modifier
        )
    }

    /**
     * Create display-only overlay for AAT task viewing
     */
    @Composable
    fun CreateDisplayOverlay(
        aatWaypoints: List<AATWaypoint>,
        mapLibreMap: org.maplibre.android.maps.MapLibreMap?,
        modifier: Modifier = Modifier
    ) {
        AATDisplayOverlay(
            aatWaypoints = aatWaypoints,
            mapLibreMap = mapLibreMap,
            modifier = modifier
        )
    }
}
