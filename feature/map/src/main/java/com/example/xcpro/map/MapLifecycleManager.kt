package com.example.xcpro.map

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.replay.SessionState
import com.example.xcpro.replay.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized lifecycle management for MapScreen
 * Handles map view lifecycle events, cleanup operations, and orientation manager coordination
 */
class MapLifecycleManager(
    internal val mapState: MapScreenState,
    private val orientationManager: MapOrientationManager,
    internal val locationManager: LocationManager,
    private val replaySessionState: StateFlow<SessionState>,
    private val stateActions: MapStateActions
) {
    companion object {
        private const val TAG = "MapLifecycleManager"
    }

    private var trackedMapView: org.maplibre.android.maps.MapView? = null
    private var mapViewCreated = false
    private var mapViewStarted = false
    private var mapViewResumed = false
    private var lastSyncedOwnerState: Lifecycle.State? = null
    private var lastSyncedOwnerMapView: org.maplibre.android.maps.MapView? = null

    /**
     * Handle map view lifecycle events
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        resetLifecycleTrackingIfMapViewChanged()
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                dispatchMapViewCreateIfNeeded()
            }
            Lifecycle.Event.ON_START -> {
                dispatchMapViewStartIfNeeded()
                orientationManager.start()
                Log.d(TAG, "Map view onStart, orientation manager started")
            }
            Lifecycle.Event.ON_RESUME -> {
                dispatchMapViewResumeIfNeeded()
                restartSensorsIfAllowed()
                locationManager.onDisplayFrame()
                Log.d(TAG, "Map view onResume - sensors checked for restart")
            }
            Lifecycle.Event.ON_PAUSE -> {
                if (mapViewResumed) {
                    mapState.mapView?.onPause()
                    mapViewResumed = false
                    Log.d(TAG, "Map view onPause")
                }
            }
            Lifecycle.Event.ON_STOP -> {
                if (mapViewStarted) {
                    mapState.mapView?.onStop()
                    mapViewStarted = false
                }
                orientationManager.stop()
                Log.d(TAG, "Map view onStop, orientation manager stopped")
            }
            Lifecycle.Event.ON_DESTROY -> {
                if (mapViewCreated) {
                    mapState.mapView?.onDestroy()
                }
                resetLifecycleTracking()
                locationManager.unbindRenderFrameListener()
                clearRuntimeOverlays()
                mapState.mapView = null
                mapState.mapLibreMap = null
                Log.d(TAG, "Map view onDestroy")
            }
            else -> Unit
        }
    }

    /**
     * Sync non-MapView lifecycle responsibilities when observer attaches after resume.
     */
    fun syncCurrentOwnerState(state: Lifecycle.State) {
        resetLifecycleTrackingIfMapViewChanged()
        val currentMapView = mapState.mapView
        if (lastSyncedOwnerState == state && lastSyncedOwnerMapView === currentMapView) {
            return
        }
        if (state.isAtLeast(Lifecycle.State.CREATED)) {
            dispatchMapViewCreateIfNeeded()
        }
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
            dispatchMapViewStartIfNeeded()
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            dispatchMapViewResumeIfNeeded()
        }
        if (state.isAtLeast(Lifecycle.State.STARTED)) {
            orientationManager.start()
        }
        if (state.isAtLeast(Lifecycle.State.RESUMED)) {
            restartSensorsIfAllowed()
        }
        lastSyncedOwnerState = state
        lastSyncedOwnerMapView = currentMapView
    }

    /**
     * Perform cleanup operations when component is disposed
     */
    fun cleanup() {
        try {
            captureCameraSnapshot()
            orientationManager.stop()
            locationManager.stopLocationTracking()
            locationManager.unbindRenderFrameListener()
            if (mapViewCreated) {
                mapState.mapView?.onDestroy()
            }
            resetLifecycleTracking()
            clearRuntimeOverlays()
            mapState.mapView = null
            mapState.mapLibreMap = null
            Log.d(TAG, "Cleanup completed: orientation manager stopped, location tracking stopped, map view destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    private fun captureCameraSnapshot() {
        try {
            val map = mapState.mapLibreMap ?: return
            val cameraPosition = map.cameraPosition
            val target = cameraPosition.target ?: return
            stateActions.updateCurrentZoom(cameraPosition.zoom.toFloat())
            stateActions.updateCameraSnapshot(
                target = MapStateStore.MapPoint(target.latitude, target.longitude),
                zoom = cameraPosition.zoom,
                bearing = cameraPosition.bearing
            )
            Log.d(TAG, "Captured camera snapshot for restore")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture camera snapshot: ${e.message}", e)
        }
    }

    private fun restartSensorsIfAllowed() {
        val replaySession = replaySessionState.value
        val allowRestart = replaySession.selection == null ||
            replaySession.status == SessionStatus.IDLE
        if (allowRestart) {
            locationManager.restartSensorsIfNeeded()
        } else {
            Log.d(TAG, "Replay active; skipping sensor restart on resume")
        }
    }

    /**
     * Get current lifecycle state
     */
    fun isMapViewReady(): Boolean {
        return mapState.mapView != null && mapState.mapLibreMap != null
    }

    /**
     * Get status for debugging
     */
    fun getLifecycleStatus(): String {
        return buildString {
            append("MapLifecycleManager Status:\n")
            append("- Map View: ${if (mapState.mapView != null) "Initialized" else "Not Initialized"}\n")
            append("- Map LibreMap: ${if (mapState.mapLibreMap != null) "Initialized" else "Not Initialized"}\n")
            append("- Orientation Manager: Available\n")
            append("- Location Tracking: ${locationManager.isGpsEnabled()}\n")
        }
    }

    private fun resetLifecycleTrackingIfMapViewChanged() {
        val currentMapView = mapState.mapView
        if (currentMapView !== trackedMapView) {
            trackedMapView = currentMapView
            mapViewCreated = false
            mapViewStarted = false
            mapViewResumed = false
            lastSyncedOwnerState = null
            lastSyncedOwnerMapView = null
        }
    }

    private fun dispatchMapViewCreateIfNeeded() {
        if (mapViewCreated) return
        mapState.mapView?.onCreate(null)
        mapViewCreated = mapState.mapView != null
        if (mapViewCreated) {
            Log.d(TAG, "Map view onCreate")
        }
    }

    private fun dispatchMapViewStartIfNeeded() {
        if (mapViewStarted) return
        dispatchMapViewCreateIfNeeded()
        mapState.mapView?.onStart()
        mapViewStarted = mapState.mapView != null
    }

    private fun dispatchMapViewResumeIfNeeded() {
        if (mapViewResumed) return
        dispatchMapViewStartIfNeeded()
        mapState.mapView?.onResume()
        mapViewResumed = mapState.mapView != null
    }

    private fun resetLifecycleTracking() {
        trackedMapView = null
        mapViewCreated = false
        mapViewStarted = false
        mapViewResumed = false
        lastSyncedOwnerState = null
        lastSyncedOwnerMapView = null
    }

    private fun clearRuntimeOverlays() {
        mapState.ognTrafficOverlay?.cleanup()
        mapState.ognTrafficOverlay = null
        mapState.ognThermalOverlay?.cleanup()
        mapState.ognThermalOverlay = null
        mapState.ognGliderTrailOverlay?.cleanup()
        mapState.ognGliderTrailOverlay = null
        mapState.adsbTrafficOverlay?.cleanup()
        mapState.adsbTrafficOverlay = null
        mapState.forecastOverlay?.cleanup()
        mapState.forecastOverlay = null
        mapState.forecastWindOverlay?.cleanup()
        mapState.forecastWindOverlay = null
        mapState.skySightSatelliteOverlay?.cleanup()
        mapState.skySightSatelliteOverlay = null
        mapState.weatherRainOverlay?.cleanup()
        mapState.weatherRainOverlay = null
        mapState.blueLocationOverlay?.cleanup()
        mapState.blueLocationOverlay = null
        mapState.snailTrailOverlay?.cleanup()
        mapState.snailTrailOverlay = null
        mapState.distanceCirclesOverlay?.cleanup()
        mapState.distanceCirclesOverlay = null
        mapState.scaleBarController?.clear()
        mapState.scaleBarController = null
        mapState.scaleBarPlugin = null
        mapState.scaleBarWidget = null
    }
}

/**
 * Compose lifecycle effects for MapScreen integration
 */
object MapLifecycleEffects {

    /**
     * Main lifecycle effect that observes activity lifecycle events
     */
    @Composable
    fun LifecycleObserverEffect(
        lifecycleManager: MapLifecycleManager
    ) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle

        DisposableEffect(lifecycle, lifecycleManager) {
            val observer = LifecycleEventObserver { _, event ->
                lifecycleManager.handleLifecycleEvent(event)
            }
            lifecycle.addObserver(observer)
            lifecycleManager.syncCurrentOwnerState(lifecycle.currentState)

            onDispose {
                lifecycle.removeObserver(observer)
            }
        }
    }

    /**
     * Location tracking cleanup effect
     */
    @Composable
    fun LocationCleanupEffect(
        locationManager: LocationManager
    ) {
        DisposableEffect(Unit) {
            onDispose {
                locationManager.stopLocationTracking()
            }
        }
    }

}
