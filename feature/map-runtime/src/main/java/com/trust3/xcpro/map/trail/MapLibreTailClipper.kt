// Role: Adapt MapLibre projection clipping for tail rendering.
package com.trust3.xcpro.map.trail

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

internal class MapLibreTailClipper(
    private val map: MapLibreMap
) : TailClipper {
    override fun clipToClearance(start: RenderPoint, end: TrailGeoPoint, clearancePx: Float): TrailGeoPoint? {
        val clipped = SnailTrailMath.clipLineToIconClearance(
            map = map,
            start = start,
            end = LatLng(end.latitude, end.longitude),
            clearancePx = clearancePx
        ) ?: return null
        return TrailGeoPoint(clipped.latitude, clipped.longitude)
    }
}
