package com.trust3.xcpro.map

internal data class BlueLocationViewportScalePolicy(
    val iconScaleMultiplier: Float
)

internal const val BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER: Float = 0.75f

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
    return BlueLocationViewportScalePolicy(
        iconScaleMultiplier = BLUE_LOCATION_SMALL_ICON_SCALE_MULTIPLIER
    )
}
