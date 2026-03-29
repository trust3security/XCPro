package com.example.xcpro.map

internal data class OgnTrafficViewportDeclutterPolicy(
    val iconScaleMultiplier: Float,
    val labelsVisible: Boolean
)

internal fun resolveOgnTrafficViewportDeclutterPolicy(
    zoomLevel: Float
): OgnTrafficViewportDeclutterPolicy {
    val zoom = zoomLevel.takeIf { it.isFinite() } ?: OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD
    return when {
        zoom >= OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD ->
            OgnTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 1.0f,
                labelsVisible = true
            )

        zoom >= OGN_TRAFFIC_MID_ZOOM_THRESHOLD ->
            OgnTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.88f,
                labelsVisible = false
            )

        zoom >= OGN_TRAFFIC_WIDE_ZOOM_THRESHOLD ->
            OgnTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.78f,
                labelsVisible = false
            )

        else ->
            OgnTrafficViewportDeclutterPolicy(
                iconScaleMultiplier = 0.68f,
                labelsVisible = false
            )
    }
}

internal fun resolveOgnTrafficScreenDeclutterStrength(
    zoomLevel: Float
): Float = resolveTrafficDeclutterStrengthMultiplier(
    zoomLevel = zoomLevel,
    fullStrengthAtOrBelowZoom = OGN_TRAFFIC_WIDE_ZOOM_THRESHOLD,
    zeroStrengthAtOrAboveZoom = OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD
)

internal const val OGN_TRAFFIC_CLOSE_ZOOM_THRESHOLD = 10.5f
private const val OGN_TRAFFIC_MID_ZOOM_THRESHOLD = 9.25f
internal const val OGN_TRAFFIC_WIDE_ZOOM_THRESHOLD = 8.25f
