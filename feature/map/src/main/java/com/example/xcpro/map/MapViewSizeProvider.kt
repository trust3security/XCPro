package com.example.xcpro.map

data class MapViewSize(
    val widthPx: Int,
    val heightPx: Int
)

interface MapViewSizeProvider {
    fun size(): MapViewSize
}

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
