package com.trust3.xcpro.map

import org.maplibre.android.maps.MapView

interface MapLocationRenderFrameBinder {
    fun bindRenderFrameListener(mapView: MapView)

    fun unbindRenderFrameListener() = Unit
}
