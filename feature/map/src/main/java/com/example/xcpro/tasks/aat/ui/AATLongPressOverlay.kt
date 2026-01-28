package com.example.xcpro.tasks.aat.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import org.maplibre.android.maps.MapLibreMap
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverter
import com.example.xcpro.tasks.aat.map.AATMapCoordinateConverterFactory
import kotlin.math.*

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
                            println(" AAT: Started drag on waypoint $waypointIndex")
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
                                println(" AAT: Dragging pin $editModeIndex to ${newPosition.latitude}, ${newPosition.longitude}")
                            }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        println(" AAT: Drag ended, staying in edit mode")
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
                            println(" AAT: Long press detected on waypoint $waypointIndex")
                        } else if (editModeIndex != -1) {
                            // Long press outside - exit edit mode
                            editModeIndex = -1
                            onExitEditMode()
                            println(" AAT: Long press outside - exiting edit mode")
                        }
                    },
                    onTap = { offset ->
                        if (editModeIndex != -1) {
                            val waypointIndex = checkWaypointHit(offset, aatWaypoints, coordinateConverter)
                            if (waypointIndex == null) {
                                // Tap outside - exit edit mode
                                editModeIndex = -1
                                onExitEditMode()
                                println(" AAT: Tap outside - exiting edit mode")
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
        val distance = haversineDistance(
            tapLatLng.latitude, tapLatLng.longitude,
            waypoint.lat, waypoint.lon
        )
        val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

        if (distance <= areaRadiusKm) {
            println(" AAT: Hit detected in ${waypoint.title} area (${String.format("%.2f", distance)}km from center)")
            return index
        }
    }
    return null
}

/**
 * Calculate haversine distance between two points in kilometers
 */
private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371.0 // km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}
