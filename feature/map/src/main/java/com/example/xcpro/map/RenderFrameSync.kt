package com.example.xcpro.map

import android.os.Looper
import org.maplibre.android.maps.MapView

class RenderFrameSync(
    private val isEnabled: () -> Boolean,
    private val onRenderFrame: () -> Unit
) {
    private var boundMapView: MapView? = null
    private val listener = MapView.OnWillStartRenderingFrameListener {
        dispatch()
    }

    fun bind(mapView: MapView) {
        if (boundMapView === mapView) return
        boundMapView?.removeOnWillStartRenderingFrameListener(listener)
        mapView.addOnWillStartRenderingFrameListener(listener)
        boundMapView = mapView
    }

    fun unbind() {
        boundMapView?.removeOnWillStartRenderingFrameListener(listener)
        boundMapView = null
    }

    private fun dispatch() {
        if (!isEnabled()) return
        val mapView = boundMapView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            onRenderFrame()
        } else {
            mapView.post { onRenderFrame() }
        }
    }
}
