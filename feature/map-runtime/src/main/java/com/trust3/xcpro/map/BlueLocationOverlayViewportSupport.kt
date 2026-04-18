package com.trust3.xcpro.map

import android.graphics.PointF
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

internal fun sameBlueLocationIconScale(first: Float?, second: Float): Boolean {
    val scale = first ?: return false
    return abs(scale - second) < 0.0001f
}

internal fun resolveBlueLocationDistancePerPixelMeters(
    metersPerPixelAtLatitude: Double?,
    viewportMetrics: MapCameraViewportMetrics
): Double? {
    val resolvedMetersPerPixel = metersPerPixelAtLatitude ?: return null
    val pixelRatio = viewportMetrics.pixelRatio
    if (!resolvedMetersPerPixel.isFinite() || resolvedMetersPerPixel <= 0.0) {
        return null
    }
    if (!pixelRatio.isFinite() || pixelRatio <= 0f) {
        return null
    }
    return resolvedMetersPerPixel / pixelRatio
}

internal fun resolveBlueLocationScreenPoint(
    map: MapLibreMap,
    location: LatLng
): PointF? {
    return runCatching { map.projection.toScreenLocation(location) }
        .getOrNull()
}
