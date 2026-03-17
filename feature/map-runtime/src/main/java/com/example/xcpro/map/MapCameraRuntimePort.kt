package com.example.xcpro.map

import com.example.xcpro.common.orientation.BearingSource
import com.example.xcpro.common.orientation.MapOrientationMode
import kotlinx.coroutines.flow.StateFlow

interface MapCameraRuntimePort {
    val isTrackingLocation: Boolean
    val showReturnButton: Boolean
    val targetLatLng: StateFlow<MapPoint?>
    val targetZoom: StateFlow<Float?>

    fun applyAnimatedZoom(animatedZoom: Float, targetLatLng: MapPoint?)

    fun updateBearing(
        newBearing: Double,
        orientationMode: MapOrientationMode,
        bearingSource: BearingSource
    )

    fun zoomToAATAreaForEdit(
        turnpointLat: Double,
        turnpointLon: Double,
        turnpointRadiusMeters: Double,
        bottomSheetHeightPx: Int = 0
    )

    fun restoreAATCameraPosition()

    fun fitTaskViewport(snapshot: TaskRenderSnapshot)
}
