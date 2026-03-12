package com.example.xcpro.map

import org.maplibre.android.maps.MapLibreMap

interface MapOverlayRuntimeLifecyclePort {
    fun toggleDistanceCircles()
    fun refreshAirspace(map: MapLibreMap?)
    fun refreshWaypoints(map: MapLibreMap?)
    fun plotSavedTask(map: MapLibreMap?)
    fun clearTaskOverlays(map: MapLibreMap?)
    fun onMapStyleChanged(map: MapLibreMap?)
    fun initializeOverlays(map: MapLibreMap?)
    fun initializeTrafficOverlays(map: MapLibreMap?)
    fun onZoomChanged(map: MapLibreMap?)
    fun onMapDetached()
}

interface MapOverlayRuntimeStatusReporter {
    fun getOverlayStatus(): String
}
