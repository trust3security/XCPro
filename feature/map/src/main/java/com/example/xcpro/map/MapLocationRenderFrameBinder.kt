package com.example.xcpro.map

import org.maplibre.android.maps.MapView

interface MapLocationRenderFrameBinder {
    fun bindRenderFrameListener(mapView: MapView)

    fun unbindRenderFrameListener() = Unit
}
