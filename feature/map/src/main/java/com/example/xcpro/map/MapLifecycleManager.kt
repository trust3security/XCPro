package com.example.xcpro.map

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.xcpro.MapOrientationManager
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.replay.SessionStatus

/**
 * Centralized lifecycle management for MapScreen
 * Handles map view lifecycle events, cleanup operations, and orientation manager coordination
 */
class MapLifecycleManager(
    internal val mapState: MapScreenState,
    private val orientationManager: MapOrientationManager,
    internal val locationManager: LocationManager,
    private val igcReplayController: IgcReplayController,
    private val stateActions: MapStateActions
) {
    companion object {
        private const val TAG = "MapLifecycleManager"
    }

    /**
     * Handle map view lifecycle events
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                mapState.mapView?.onCreate(null)
                Log.d(TAG, "Map view onCreate")
            }
            Lifecycle.Event.ON_START -> {
                mapState.mapView?.onStart()
                orientationManager.start()
                Log.d(TAG, "Map view onStart, orientation manager started")
            }
            Lifecycle.Event.ON_RESUME -> {
                mapState.mapView?.onResume()

                //  Restart sensors if needed after sleep mode
                // This ensures GPS and other sensors resume after screen-off
                val replaySession = igcReplayController.session.value
                val allowRestart = replaySession.selection == null ||
                    replaySession.status == SessionStatus.IDLE
                if (allowRestart) {
                    locationManager.restartSensorsIfNeeded()
                } else {
                    Log.d(TAG, "Replay active; skipping sensor restart on resume")
                }

                Log.d(TAG, "Map view onResume - sensors checked for restart")
            }
            Lifecycle.Event.ON_PAUSE -> {
                mapState.mapView?.onPause()
                Log.d(TAG, "Map view onPause")
            }
            Lifecycle.Event.ON_STOP -> {
                mapState.mapView?.onStop()
                orientationManager.stop()
                Log.d(TAG, "Map view onStop, orientation manager stopped")
            }
            Lifecycle.Event.ON_DESTROY -> {
                mapState.mapView?.onDestroy()
                locationManager.unbindRenderFrameListener()
                Log.d(TAG, "Map view onDestroy")
            }
            else -> Unit
        }
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
            mapState.mapView?.onDestroy()
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

    /**
     * Handle map style changes
     */
    fun handleMapStyleChange(styleUrl: String, onStyleLoaded: () -> Unit = {}) {
        try {
            mapState.mapLibreMap?.setStyle(styleUrl) {
                Log.d(TAG, "Map style loaded: $styleUrl")
                onStyleLoaded()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting map style: ${e.message}", e)
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
            append("- Location Tracking: ${locationManager.unifiedSensorManager.isGpsEnabled()}\n")
        }
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

        DisposableEffect(Unit) {
            val observer = LifecycleEventObserver { _, event ->
                lifecycleManager.handleLifecycleEvent(event)
            }
            lifecycle.addObserver(observer)

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

    /**
     * Map style loading effect
     */
    @Composable
    fun MapStyleEffect(
        lifecycleManager: MapLifecycleManager,
        styleUrl: String,
        onStyleLoaded: () -> Unit = {}
    ) {
        DisposableEffect(styleUrl) {
            lifecycleManager.handleMapStyleChange(styleUrl, onStyleLoaded)
            onDispose { }
        }
    }

    /**
     * Combined lifecycle effects for easy integration
     */
    @Composable
    fun AllLifecycleEffects(
        lifecycleManager: MapLifecycleManager,
        styleUrl: String,
        onStyleLoaded: () -> Unit = {}
    ) {
        LifecycleObserverEffect(lifecycleManager)

        MapStyleEffect(
            lifecycleManager = lifecycleManager,
            styleUrl = styleUrl,
            onStyleLoaded = onStyleLoaded
        )
    }
}
