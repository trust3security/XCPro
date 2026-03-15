// Role: Provide meters-per-pixel values for trail planning.
package com.example.xcpro.map.trail

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

internal interface MetersPerPixelProvider {
    fun metersPerPixel(latitude: Double, zoom: Float): Double
}

internal class MapLibreMetersPerPixelProvider(
    private val map: MapLibreMap,
    private val mapView: MapView?
) : MetersPerPixelProvider {
    override fun metersPerPixel(latitude: Double, zoom: Float): Double {
        return SnailTrailMath.metersPerPixel(map, mapView, latitude, zoom)
    }
}
