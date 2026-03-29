package com.example.xcpro.map

internal data class BlueLocationViewportScalePolicy(
    val iconScaleMultiplier: Float
)

internal fun resolveBlueLocationVisibleRadiusMeters(
    screenX: Float,
    screenY: Float,
    viewportMetrics: MapCameraViewportMetrics,
    distancePerPixelMeters: Double
): Double? {
    if (!screenX.isFinite() || !screenY.isFinite()) return null
    if (viewportMetrics.widthPx <= 0 || viewportMetrics.heightPx <= 0) return null
    if (!distancePerPixelMeters.isFinite() || distancePerPixelMeters <= 0.0) return null

    val widthPx = viewportMetrics.widthPx.toFloat()
    val heightPx = viewportMetrics.heightPx.toFloat()
    if (screenX < 0f || screenX > widthPx || screenY < 0f || screenY > heightPx) {
        return null
    }

    val nearestEdgeDistancePx = minOf(
        screenX,
        widthPx - screenX,
        screenY,
        heightPx - screenY
    )
    if (!nearestEdgeDistancePx.isFinite() || nearestEdgeDistancePx < 0f) {
        return null
    }

    return nearestEdgeDistancePx.toDouble() * distancePerPixelMeters
}

internal fun resolveBlueLocationViewportScalePolicy(
    visibleRadiusMeters: Double?
): BlueLocationViewportScalePolicy {
    val radiusMeters = visibleRadiusMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?: return BlueLocationViewportScalePolicy(iconScaleMultiplier = 1.0f)

    return when {
        radiusMeters >= BLUE_LOCATION_VISIBLE_RADIUS_MID_METERS ->
            BlueLocationViewportScalePolicy(iconScaleMultiplier = 0.75f)

        else ->
            BlueLocationViewportScalePolicy(iconScaleMultiplier = 1.0f)
    }
}

internal const val BLUE_LOCATION_VISIBLE_RADIUS_MID_METERS: Double = 5_000.0
