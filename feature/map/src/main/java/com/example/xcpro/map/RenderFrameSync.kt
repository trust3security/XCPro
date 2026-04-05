package com.example.xcpro.map

import android.os.Looper
import org.maplibre.android.maps.MapView

class RenderFrameSync(
    private val isEnabled: () -> Boolean,
    private val onRenderFrame: () -> Unit,
    private val diagnostics: MapRenderSurfaceDiagnostics,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() }
) {
    private var boundMapView: MapView? = null
    private var pendingPostedDispatch = false
    private val listener = MapView.OnWillStartRenderingFrameListener {
        dispatch()
    }
    private val dispatchRunnable = Runnable {
        pendingPostedDispatch = false
        if (isEnabled()) {
            onRenderFrame()
        }
    }

    fun bind(mapView: MapView) {
        if (boundMapView === mapView) return
        clearPendingPostedDispatch()
        boundMapView?.removeOnWillStartRenderingFrameListener(listener)
        mapView.addOnWillStartRenderingFrameListener(listener)
        boundMapView = mapView
    }

    fun unbind() {
        clearPendingPostedDispatch()
        boundMapView?.removeOnWillStartRenderingFrameListener(listener)
        boundMapView = null
    }

    private fun dispatch() {
        if (!isEnabled()) return
        val mapView = boundMapView ?: return
        diagnostics.recordRenderFrameCallback()
        if (isMainThread()) {
            if (pendingPostedDispatch) {
                mapView.removeCallbacks(dispatchRunnable)
                pendingPostedDispatch = false
                diagnostics.recordPendingDispatchCleared()
            }
            diagnostics.recordImmediateDispatch()
            onRenderFrame()
        } else {
            if (pendingPostedDispatch) {
                diagnostics.recordPostedDispatchDropped()
                return
            }
            pendingPostedDispatch = mapView.post(dispatchRunnable)
            if (pendingPostedDispatch) {
                diagnostics.recordPostedDispatchScheduled()
            }
        }
    }

    private fun clearPendingPostedDispatch() {
        if (!pendingPostedDispatch) {
            return
        }
        boundMapView?.removeCallbacks(dispatchRunnable)
        pendingPostedDispatch = false
        diagnostics.recordPendingDispatchCleared()
    }
}
