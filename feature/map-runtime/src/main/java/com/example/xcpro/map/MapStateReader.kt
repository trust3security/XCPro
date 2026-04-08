package com.example.xcpro.map
/**
 * Read-only view of map UI state for UI/runtime consumers.
 * Invariants: no mutation APIs are exposed.
 */

import com.example.xcpro.common.flight.FlightMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of map UI state for UI/runtime managers.
 */
interface MapStateReader {
    val safeContainerSize: StateFlow<MapSize>
    val mapStyleName: StateFlow<String>
    val showRecenterButton: StateFlow<Boolean>
    val showReturnButton: StateFlow<Boolean>
    val isTrackingLocation: StateFlow<Boolean>
    val showDistanceCircles: StateFlow<Boolean>
    val lastUserPanTime: StateFlow<Long>
    val hasInitiallyCentered: StateFlow<Boolean>
    val savedLocation: StateFlow<MapPoint?>
    val savedZoom: StateFlow<Double?>
    val savedBearing: StateFlow<Double?>
    val lastCameraSnapshot: StateFlow<CameraSnapshot?>
    val currentMode: StateFlow<FlightMode>
    val currentZoom: StateFlow<Float>
    val targetLatLng: StateFlow<MapPoint?>
    val targetZoom: StateFlow<Float?>
    val currentUserLocation: StateFlow<MapPoint?>
    val displayPoseMode: StateFlow<DisplayPoseMode>
    val displaySmoothingProfile: StateFlow<DisplaySmoothingProfile>
}
