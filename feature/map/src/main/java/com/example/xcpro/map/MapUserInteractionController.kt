package com.example.xcpro.map

import android.util.Log
import com.example.xcpro.core.time.TimeBridge
import com.example.xcpro.map.model.MapLocationUiModel
import org.maplibre.android.geometry.LatLng

class MapUserInteractionController(
    private val mapStateReader: MapStateReader,
    private val stateActions: MapStateActions,
    private val paddingProvider: () -> IntArray,
    private val cameraControllerProvider: MapCameraControllerProvider,
    private val logTag: String,
    private val nowWallMs: () -> Long = { TimeBridge.nowWallMs() }
) {
    private var returnZoomSnapshot: Double? = null
    private var returnBearingSnapshot: Double? = null

    private var isTrackingLocation: Boolean
        get() = mapStateReader.isTrackingLocation.value
        set(value) {
            stateActions.setTrackingLocation(value)
        }

    private var showRecenterButton: Boolean
        get() = mapStateReader.showRecenterButton.value
        set(value) {
            stateActions.setShowRecenterButton(value)
        }

    private var showReturnButton: Boolean
        get() = mapStateReader.showReturnButton.value
        set(value) {
            stateActions.setShowReturnButton(value)
        }

    private var lastUserPanTime: Long
        get() = mapStateReader.lastUserPanTime.value
        set(value) {
            stateActions.updateLastUserPanTime(value)
        }

    private val savedLocation: LatLng?
        get() = mapStateReader.savedLocation.value?.let { LatLng(it.latitude, it.longitude) }

    private val savedZoom: Double?
        get() = mapStateReader.savedZoom.value

    private val savedBearing: Double?
        get() = mapStateReader.savedBearing.value

    private val currentUserLocation: LatLng?
        get() = mapStateReader.currentUserLocation.value?.let { LatLng(it.latitude, it.longitude) }

    fun saveLocation(location: LatLng, zoom: Double, bearing: Double) {
        stateActions.saveLocation(
            location = MapPoint(location.latitude, location.longitude),
            zoom = zoom,
            bearing = bearing
        )
    }

    fun saveLocationFromGPS(location: MapLocationUiModel?, zoom: Double, bearing: Double) {
        location?.let {
            saveLocation(LatLng(it.latitude, it.longitude), zoom, bearing)
        }
    }

    fun showReturnButton() {
        // Return and recenter are mutually exclusive controls on the same lane.
        // Showing return after user pan must suppress recenter to avoid overlap/flicker.
        showRecenterButton = false
        showReturnButton = true
        lastUserPanTime = nowWallMs()
        Log.d(logTag, "Return button shown due to user interaction")
    }

    fun returnToSavedLocation(): Boolean {
        val location = savedLocation ?: return false
        val cameraController = cameraControllerProvider.controllerOrNull() ?: return false
        val currentPosition = cameraController.cameraPosition
        val zoomToUse = returnZoomSnapshot ?: savedZoom ?: currentPosition.zoom
        val bearingToUse = returnBearingSnapshot ?: savedBearing ?: currentPosition.bearing
        val returnCameraPosition = MapCameraPositionSnapshot(
            target = location,
            zoom = zoomToUse,
            bearing = bearingToUse,
            tilt = currentPosition.tilt
        )

        // Pause tracking updates so the return animation can set the zoom cleanly.
        isTrackingLocation = false
        showReturnButton = false
        showRecenterButton = false
        returnZoomSnapshot = null
        returnBearingSnapshot = null

        val padding = paddingProvider()
        cameraController.setPadding(padding[0], padding[1], padding[2], padding[3])
        cameraController.animateCamera(
            position = returnCameraPosition,
            durationMs = 1000,
            callback = object : MapCameraController.CancelableCallback {
                override fun onFinish() {
                    isTrackingLocation = true
                    Log.d(logTag, "Returned to saved position")
                }

                override fun onCancel() {
                    isTrackingLocation = true
                    Log.d(logTag, "Return animation canceled")
                }
            }
        )
        return true
    }

    fun recenterOnCurrentLocation() {
        val location = currentUserLocation ?: return
        val cameraController = cameraControllerProvider.controllerOrNull() ?: return
        val currentPosition = cameraController.cameraPosition
        val newCameraPosition = MapCameraPositionSnapshot(
            target = location,
            zoom = currentPosition.zoom,
            bearing = currentPosition.bearing,
            tilt = currentPosition.tilt
        )

        cameraController.animateCamera(newCameraPosition, 800)
        val padding = paddingProvider()
        cameraController.setPadding(padding[0], padding[1], padding[2], padding[3])
        showRecenterButton = false
        Log.d(logTag, "Recentered to current location")
    }

    fun handleUserInteraction(currentLocation: MapLocationUiModel?, currentZoom: Double, currentBearing: Double) {
        if (!showReturnButton) {
            val locationToSave = currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: savedLocation
            if (locationToSave != null) {
                saveLocation(locationToSave, currentZoom, currentBearing)
            }
            returnZoomSnapshot = currentZoom
            returnBearingSnapshot = currentBearing
        }
        showReturnButton()
    }
}
