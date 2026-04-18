package com.trust3.xcpro.map

class MapScreenSizeProvider(
    private val mapState: MapScreenState
) : MapViewSizeProvider {
    override fun size(): MapViewSize {
        val mapView = mapState.mapView
        return MapViewSize(
            widthPx = mapView?.width ?: 0,
            heightPx = mapView?.height ?: 0
        )
    }
}
