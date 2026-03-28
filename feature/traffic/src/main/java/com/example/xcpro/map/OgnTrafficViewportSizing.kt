package com.example.xcpro.map

import kotlin.math.roundToInt

internal data class OgnTrafficViewportSizing(
    val renderedIconSizePx: Int,
    val iconScaleMultiplier: Float
)

internal fun clampOgnRenderedIconSizePx(sizePx: Int): Int =
    sizePx.coerceIn(OGN_TRAFFIC_RENDERED_ICON_MIN_PX, OGN_ICON_SIZE_MAX_PX)

internal fun resolveOgnTrafficViewportSizing(
    baseIconSizePx: Int,
    zoomLevel: Float?
): OgnTrafficViewportSizing {
    val clampedBaseSizePx = clampOgnIconSizePx(baseIconSizePx)
    val declutterPolicy = zoomLevel
        ?.takeIf { it.isFinite() }
        ?.let(::resolveOgnTrafficViewportDeclutterPolicy)
    if (declutterPolicy == null) {
        return OgnTrafficViewportSizing(
            renderedIconSizePx = clampedBaseSizePx,
            iconScaleMultiplier = 1.0f
        )
    }
    val multiplier = declutterPolicy.iconScaleMultiplier
    val renderedIconSizePx = clampOgnRenderedIconSizePx(
        (clampedBaseSizePx * multiplier).roundToInt()
    ).coerceAtMost(clampedBaseSizePx)
    return OgnTrafficViewportSizing(
        renderedIconSizePx = renderedIconSizePx,
        iconScaleMultiplier = multiplier
    )
}
private const val OGN_TRAFFIC_RENDERED_ICON_MIN_PX = 48
