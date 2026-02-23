package com.example.xcpro.tasks.aat.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverter
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverterFactory

/**
 * AAT Long Press Overlay for Pin Dragging
 *
 * Handles:
 * - Long press detection on AAT waypoints
 * - Enter/exit edit mode
 * - Pin dragging within AAT areas
 * - Coordinate conversion
 */
@Composable
fun AATLongPressOverlay(
    aatWaypoints: List<AATWaypoint>,
    mapLibreMap: MapLibreMap?,
    onLongPressWaypoint: (Int) -> Unit,
    onExitEditMode: () -> Unit,
    onPinDrag: (Int, AATLatLng) -> Unit,
    modifier: Modifier = Modifier
) {
    val coordinateConverter = remember(mapLibreMap) {
        mapLibreMap?.let { AATMapCoordinateConverterFactory.create(it) }
    }

    var editModeIndex by remember { mutableStateOf(-1) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val waypointIndex = checkWaypointHit(offset, aatWaypoints, coordinateConverter)
                        if (waypointIndex != null) {
                            editModeIndex = waypointIndex
                            isDragging = true
                            onLongPressWaypoint(waypointIndex)
                        }
                    },
                    onDrag = { change, _ ->
                        if (isDragging && editModeIndex != -1) {
                            val newLatLng = coordinateConverter?.screenToMap(
                                change.position.x, change.position.y
                            )
                            if (newLatLng != null) {
                                val newPosition = AATLatLng(newLatLng.latitude, newLatLng.longitude)
                                onPinDrag(editModeIndex, newPosition)
                            }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        // Keep edit mode active until explicit exit
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { offset ->
                        val waypointIndex = checkWaypointHit(offset, aatWaypoints, coordinateConverter)
                        if (waypointIndex != null) {
                            editModeIndex = waypointIndex
                            onLongPressWaypoint(waypointIndex)
                        } else if (editModeIndex != -1) {
                            // Long press outside - exit edit mode
                            editModeIndex = -1
                            onExitEditMode()
                        }
                    },
                    onTap = { offset ->
                        if (editModeIndex != -1) {
                            val waypointIndex = checkWaypointHit(offset, aatWaypoints, coordinateConverter)
                            if (waypointIndex == null) {
                                // Tap outside - exit edit mode
                                editModeIndex = -1
                                onExitEditMode()
                            }
                        }
                    }
                )
            }
    ) {
    }
}

/**
 * Check if a screen coordinate hits any AAT waypoint area
 */
private fun checkWaypointHit(
    offset: Offset,
    waypoints: List<AATWaypoint>,
    converter: AATMapCoordinateConverter?
): Int? {
    if (converter == null) return null

    val tapLatLng = converter.screenToMap(offset.x, offset.y) ?: return null

    waypoints.forEachIndexed { index, waypoint ->
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            AATLatLng(tapLatLng.latitude, tapLatLng.longitude),
            AATLatLng(waypoint.lat, waypoint.lon)
        )

        if (distanceMeters <= waypoint.assignedArea.radiusMeters) {
            return index
        }
    }
    return null
}
