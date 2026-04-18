package com.trust3.xcpro.map

import androidx.lifecycle.Lifecycle
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.replay.SessionState
import com.trust3.xcpro.replay.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized lifecycle management for MapScreen
 * Handles map view lifecycle events, cleanup operations, and orientation manager coordination
 */
class MapLifecycleManager(
    private val lifecycleSurface: MapLifecycleSurfacePort,
    private val orientationManager: MapOrientationRuntimePort,
    internal val locationManager: MapLocationRuntimePort,
    private val locationRenderFrameCleanup: MapRenderFrameCleanupPort,
    private val renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics,
    private val replaySessionState: StateFlow<SessionState>
) : MapLifecycleRuntimePort {
    companion object {
        private const val LOG_TAG = "MapLifecycleManager"
    }

    private var trackedMapView: Any? = null
    private var mapViewCreated = false
    private var mapViewStarted = false
    private var mapViewResumed = false
    private var lastSyncedOwnerState: Lifecycle.State? = null
    private var lastSyncedOwnerMapView: Any? = null

    /**
     * Handle map view lifecycle events
     */
    override fun handleLifecycleEvent(event: Lifecycle.Event) {
        resetLifecycleTrackingIfMapViewChanged()
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                dispatchMapViewCreateIfNeeded()
            }
            Lifecycle.Event.ON_START -> {
                dispatchMapViewStartIfNeeded()
                orientationManager.start()
            }
            Lifecycle.Event.ON_RESUME -> {
                dispatchMapViewResumeIfNeeded()
                restartSensorsIfAllowed()
                renderSurfaceDiagnostics.recordLifecycleResumeForcedFrame()
                locationManager.onDisplayFrame()
            }
            Lifecycle.Event.ON_PAUSE -> {
                if (mapViewResumed) {
                    lifecycleSurface.dispatchPauseIfPresent()
                    mapViewResumed = false
                }
            }
            Lifecycle.Event.ON_STOP -> {
                if (mapViewResumed) {
                    lifecycleSurface.dispatchPauseIfPresent()
                    mapViewResumed = false
                }
                if (mapViewStarted) {
                    lifecycleSurface.dispatchStopIfPresent()
                    mapViewStarted = false
                }
                orientationManager.stop()
                AppLogger.i(LOG_TAG, renderSurfaceDiagnostics.buildCompactStatus(reason = "on_stop"))
            }
            Lifecycle.Event.ON_DESTROY -> {
                if (mapViewCreated) {
                    lifecycleSurface.dispatchDestroyIfPresent()
                }
                resetLifecycleTracking()
                locationRenderFrameCleanup.unbindRenderFrameListener()
                lifecycleSurface.clearRuntimeOverlays()
                lifecycleSurface.clearMapSurfaceReferences()
            }
            else -> Unit
        }
    }

    /**
     * Sync non-MapView lifecycle responsibilities when observer attaches after resume.
     */
    override fun syncCurrentOwnerState(state: Lifecycle.State) {
        resetLifecycleTrackingIfMapViewChanged()
        val currentMapView = lifecycleSurface.currentHostToken()
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
    override fun cleanup() {
        try {
            lifecycleSurface.captureCameraSnapshot()
            orientationManager.stop()
            locationManager.stopLocationTracking()
            locationRenderFrameCleanup.unbindRenderFrameListener()
            if (mapViewCreated) {
                lifecycleSurface.dispatchDestroyIfPresent()
            }
            resetLifecycleTracking()
            lifecycleSurface.clearRuntimeOverlays()
            lifecycleSurface.clearMapSurfaceReferences()
            AppLogger.i(LOG_TAG, renderSurfaceDiagnostics.buildCompactStatus(reason = "cleanup"))
        } catch (e: Exception) {
            AppLogger.e(LOG_TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    private fun restartSensorsIfAllowed() {
        val replaySession = replaySessionState.value
        val allowRestart = replaySession.selection == null ||
            replaySession.status == SessionStatus.IDLE
        if (allowRestart) {
            locationManager.restartSensorsIfNeeded()
        }
    }

    /**
     * Get current lifecycle state
     */
    fun isMapViewReady(): Boolean {
        return lifecycleSurface.isMapViewReady() && lifecycleSurface.isMapLibreReady()
    }

    /**
     * Get status for debugging
     */
    fun getLifecycleStatus(): String {
        return buildString {
            append("MapLifecycleManager Status:\n")
            append("- Map View: ${if (lifecycleSurface.isMapViewReady()) "Initialized" else "Not Initialized"}\n")
            append("- Map LibreMap: ${if (lifecycleSurface.isMapLibreReady()) "Initialized" else "Not Initialized"}\n")
            append("- Orientation Manager: Available\n")
            append("- Location Tracking: ${locationManager.isGpsEnabled()}\n")
            append(renderSurfaceDiagnostics.buildStatus(header = "Render Surface"))
        }
    }

    private fun resetLifecycleTrackingIfMapViewChanged() {
        val currentMapView = lifecycleSurface.currentHostToken()
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
        mapViewCreated = lifecycleSurface.dispatchCreateIfPresent()
    }

    private fun dispatchMapViewStartIfNeeded() {
        if (mapViewStarted) return
        dispatchMapViewCreateIfNeeded()
        mapViewStarted = lifecycleSurface.dispatchStartIfPresent()
    }

    private fun dispatchMapViewResumeIfNeeded() {
        if (mapViewResumed) return
        dispatchMapViewStartIfNeeded()
        mapViewResumed = lifecycleSurface.dispatchResumeIfPresent()
    }

    private fun resetLifecycleTracking() {
        trackedMapView = null
        mapViewCreated = false
        mapViewStarted = false
        mapViewResumed = false
        lastSyncedOwnerState = null
        lastSyncedOwnerMapView = null
    }

}
