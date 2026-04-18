package com.trust3.xcpro.map.ui

import com.trust3.xcpro.map.MapLocationRenderFrameBinder
import com.trust3.xcpro.map.MapRenderSurfaceDiagnostics
import com.trust3.xcpro.map.MapScreenState
import org.maplibre.android.maps.MapView

internal class MapViewHostBindingController(
    private val mapState: MapScreenState,
    private val locationRenderFrameBinder: MapLocationRenderFrameBinder,
    private val renderSurfaceDiagnostics: MapRenderSurfaceDiagnostics
) {
    private var boundMapView: MapView? = null

    fun attach(mapView: MapView, onMapViewBound: () -> Unit) {
        if (boundMapView === mapView) {
            updateMapViewReference(mapView)
            return
        }
        renderSurfaceDiagnostics.recordMapViewAttached(swapped = boundMapView != null)
        boundMapView = mapView
        mapState.mapView = mapView
        locationRenderFrameBinder.bindRenderFrameListener(mapView)
        onMapViewBound()
    }

    fun updateMapViewReference(mapView: MapView) {
        if (mapState.mapView !== mapView) {
            mapState.mapView = mapView
        }
    }

    fun clear(mapView: MapView) {
        if (boundMapView !== mapView) {
            return
        }
        renderSurfaceDiagnostics.recordMapViewCleared()
        boundMapView = null
        // Lifecycle cleanup remains the final owner that clears mapState.mapView.
        // Keeping the instance here allows transient host disposal/recreation to
        // reuse the same SurfaceView-backed MapView instead of forcing a new one.
    }
}
