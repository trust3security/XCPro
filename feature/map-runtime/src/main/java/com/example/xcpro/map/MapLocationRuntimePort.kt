package com.example.xcpro.map

import com.example.dfcards.RealTimeFlightData
import com.example.xcpro.common.orientation.OrientationData
import com.example.xcpro.map.model.MapLocationUiModel
import org.maplibre.android.geometry.LatLng

fun interface MapLocationPermissionRequester {
    fun requestLocationPermissions()
}

interface MapLocationRuntimePort {
    fun onLocationPermissionsResult(fineLocationGranted: Boolean)

    fun requestLocationPermissions(permissionRequester: MapLocationPermissionRequester)

    fun updateLocationFromGPS(
        location: MapLocationUiModel,
        orientation: OrientationData
    )

    fun setLocalOwnshipRenderEnabled(enabled: Boolean)

    fun updateOrientation(orientation: OrientationData)

    fun setReplaySpeedMultiplier(multiplier: Double)

    fun shouldDispatchLiveDisplayFrame(): Boolean

    fun onDisplayFrame()

    fun updateLocationFromFlightData(
        liveData: RealTimeFlightData,
        orientation: OrientationData
    )

    fun getDisplayPoseLocation(): LatLng?

    fun getDisplayPoseTimestampMs(): Long?

    fun getDisplayPoseSnapshot(): DisplayPoseSnapshot?

    fun setDisplayPoseFrameListener(listener: ((DisplayPoseSnapshot) -> Unit)?)

    fun showReturnButton()

    fun returnToSavedLocation(): Boolean

    fun recenterOnCurrentLocation()

    fun handleUserInteraction(
        currentLocation: MapLocationUiModel?,
        currentZoom: Double,
        currentBearing: Double
    )

    fun stopLocationTracking(force: Boolean = false)

    fun restartSensorsIfNeeded()

    fun isGpsEnabled(): Boolean

    fun setActiveProfileId(profileId: String)
}
