package com.trust3.xcpro.map.trail

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

internal data class RenderPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val varioMs: Double,
    val timestampMillis: Long
)

internal data class ColorRamp(
    val position: Int,
    val r: Int,
    val g: Int,
    val b: Int
)

internal class ScreenBounds(mapView: MapView?) {
    private val width = mapView?.width?.toFloat() ?: 0f
    private val height = mapView?.height?.toFloat() ?: 0f

    private val minX = if (width > 0f) -1.5f * width else Float.NEGATIVE_INFINITY
    private val maxX = if (width > 0f) 2.5f * width else Float.POSITIVE_INFINITY
    private val minY = if (height > 0f) -1.5f * height else Float.NEGATIVE_INFINITY
    private val maxY = if (height > 0f) 2.5f * height else Float.POSITIVE_INFINITY

    fun isInside(map: MapLibreMap, lat: Double, lon: Double): Boolean {
        if (width <= 0f || height <= 0f) return true
        val point = map.projection.toScreenLocation(LatLng(lat, lon))
        return point.x in minX..maxX && point.y in minY..maxY
    }
}
