package com.trust3.xcpro.map

internal class MapDisplayPoseSurfaceAdapter(
    private val mapState: MapScreenState
) : MapDisplayPoseSurfacePort {
    override fun isMapReady(): Boolean = mapState.mapLibreMap != null

    override fun currentCameraBearing(): Double? = mapState.mapLibreMap?.cameraPosition?.bearing

    override fun distancePerPixelMetersAt(latitude: Double): Double? {
        if (!latitude.isFinite()) {
            return null
        }
        val map = mapState.mapLibreMap ?: return null
        val mapView = mapState.mapView ?: return null
        val pixelRatio = mapView.pixelRatio
        if (!pixelRatio.isFinite() || pixelRatio <= 0f) {
            return null
        }
        val metersPerPixel = runCatching {
            map.projection.getMetersPerPixelAtLatitude(latitude)
        }.getOrNull() ?: return null
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) {
            return null
        }
        return metersPerPixel / pixelRatio
    }
}
