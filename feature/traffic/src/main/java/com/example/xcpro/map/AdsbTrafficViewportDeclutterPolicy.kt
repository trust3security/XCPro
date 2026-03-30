package com.example.xcpro.map

internal data class AdsbTrafficViewportDeclutterPolicy(
    val iconScaleMultiplier: Float,
    val showAllLabels: Boolean,
    val closeTrafficLabelDistanceMeters: Double,
    val maxTargets: Int
)

internal fun resolveAdsbTrafficViewportDeclutterPolicy(
    zoomLevel: Float,
    viewportRangeMeters: Double? = null
): AdsbTrafficViewportDeclutterPolicy {
    val zoom = zoomLevel.takeIf { it.isFinite() } ?: ADSB_TRAFFIC_DEFAULT_VIEWPORT_ZOOM
    val showAllLabels = shouldShowAllAdsbLabels(
        zoomLevel = zoom,
        viewportRangeMeters = viewportRangeMeters
    )
    return when {
        zoom >= ADSB_TRAFFIC_LABELS_MIN_ZOOM ->
            AdsbTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 1.0f,
                showAllLabels = showAllLabels,
                closeTrafficLabelDistanceMeters = ADSB_TRAFFIC_PRIORITY_LABEL_DISTANCE_METERS,
                maxTargets = ADSB_TRAFFIC_MAX_TARGETS
            )

        zoom >= ADSB_TRAFFIC_MID_ZOOM_THRESHOLD ->
            AdsbTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.88f,
                showAllLabels = showAllLabels,
                closeTrafficLabelDistanceMeters = ADSB_TRAFFIC_PRIORITY_LABEL_DISTANCE_METERS,
                maxTargets = 72
            )

        zoom >= ADSB_TRAFFIC_WIDE_ZOOM_THRESHOLD ->
            AdsbTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.78f,
                showAllLabels = showAllLabels,
                closeTrafficLabelDistanceMeters = ADSB_TRAFFIC_PRIORITY_LABEL_DISTANCE_METERS,
                maxTargets = 48
            )

        else ->
            AdsbTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.68f,
                showAllLabels = showAllLabels,
                closeTrafficLabelDistanceMeters = ADSB_TRAFFIC_PRIORITY_LABEL_DISTANCE_METERS,
                maxTargets = 28
            )
    }
}

internal fun shouldShowAllAdsbLabels(
    zoomLevel: Float,
    viewportRangeMeters: Double?
): Boolean {
    val normalizedViewportRangeMeters = viewportRangeMeters
        ?.takeIf { it.isFinite() && it > 0.0 }
    return if (normalizedViewportRangeMeters != null) {
        normalizedViewportRangeMeters <= ADSB_TRAFFIC_LABELS_VIEWPORT_RANGE_METERS
    } else {
        zoomLevel >= ADSB_TRAFFIC_LABELS_MIN_ZOOM
    }
}

internal fun resolveAdsbTrafficScreenDeclutterStrength(
    zoomLevel: Float
): Float = resolveTrafficDeclutterStrengthMultiplier(
    zoomLevel = zoomLevel,
    fullStrengthAtOrBelowZoom = ADSB_TRAFFIC_WIDE_ZOOM_THRESHOLD,
    zeroStrengthAtOrAboveZoom = ADSB_TRAFFIC_LABELS_MIN_ZOOM
)

private const val ADSB_TRAFFIC_DEFAULT_VIEWPORT_ZOOM = 10f
private const val ADSB_TRAFFIC_LABELS_VIEWPORT_RANGE_METERS = 30_000.0
private const val ADSB_TRAFFIC_PRIORITY_LABEL_DISTANCE_METERS = 10_000.0
internal const val ADSB_TRAFFIC_LABELS_MIN_ZOOM = 10.5f
private const val ADSB_TRAFFIC_MID_ZOOM_THRESHOLD = 9.25f
internal const val ADSB_TRAFFIC_WIDE_ZOOM_THRESHOLD = 8.25f
