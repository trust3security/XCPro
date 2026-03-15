package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger

/**
 * Shell-owned adapter that keeps `MapScreenState` and concrete map handles out
 * of the runtime lifecycle owner.
 */
class MapLifecycleSurfaceAdapter(
    private val mapState: MapScreenState,
    private val stateActions: MapStateActions
) : MapLifecycleSurfacePort {
    companion object {
        private const val LOG_TAG = "MapLifecycleSurface"
    }

    override fun currentHostToken(): Any? = mapState.mapView

    override fun dispatchCreateIfPresent(): Boolean {
        mapState.mapView?.onCreate(null)
        return mapState.mapView != null
    }

    override fun dispatchStartIfPresent(): Boolean {
        mapState.mapView?.onStart()
        return mapState.mapView != null
    }

    override fun dispatchResumeIfPresent(): Boolean {
        mapState.mapView?.onResume()
        return mapState.mapView != null
    }

    override fun dispatchPauseIfPresent(): Boolean {
        mapState.mapView?.onPause()
        return mapState.mapView != null
    }

    override fun dispatchStopIfPresent(): Boolean {
        mapState.mapView?.onStop()
        return mapState.mapView != null
    }

    override fun dispatchDestroyIfPresent(): Boolean {
        mapState.mapView?.onDestroy()
        return mapState.mapView != null
    }

    override fun captureCameraSnapshot() {
        try {
            val map = mapState.mapLibreMap ?: return
            val cameraPosition = map.cameraPosition
            val target = cameraPosition.target ?: return
            stateActions.updateCurrentZoom(cameraPosition.zoom.toFloat())
            stateActions.updateCameraSnapshot(
                target = MapPoint(target.latitude, target.longitude),
                zoom = cameraPosition.zoom,
                bearing = cameraPosition.bearing
            )
        } catch (e: Exception) {
            AppLogger.w(LOG_TAG, "Failed to capture camera snapshot: ${e.message}", e)
        }
    }

    override fun clearRuntimeOverlays() {
        mapState.ognTargetRingOverlay?.cleanup()
        mapState.ognTargetRingOverlay = null
        mapState.ognTargetLineOverlay?.cleanup()
        mapState.ognTargetLineOverlay = null
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
        mapState.scaleBarController?.clear()
        mapState.scaleBarController = null
        mapState.scaleBarPlugin = null
        mapState.scaleBarWidget = null
    }

    override fun clearMapSurfaceReferences() {
        mapState.mapView = null
        mapState.mapLibreMap = null
    }

    override fun isMapViewReady(): Boolean = mapState.mapView != null

    override fun isMapLibreReady(): Boolean = mapState.mapLibreMap != null
}
