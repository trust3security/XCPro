package com.example.xcpro.map.widgets

import com.example.xcpro.core.common.geometry.DensityScale
import com.example.xcpro.core.common.geometry.dpToPx
import kotlin.math.min

/**
 * Centralized size defaults and clamp policy for resizable map widgets.
 */
internal object MapWidgetSizePolicy {
    private const val SIDE_HAMBURGER_DEFAULT_DP = 90f
    private const val SIDE_HAMBURGER_MIN_DP = 56f
    private const val SIDE_HAMBURGER_MAX_DP = 140f

    private const val SETTINGS_SHORTCUT_DEFAULT_DP = 56f
    private const val SETTINGS_SHORTCUT_MIN_DP = 40f
    private const val SETTINGS_SHORTCUT_MAX_DP = 96f

    fun supportsSize(widgetId: MapWidgetId): Boolean =
        widgetId == MapWidgetId.SIDE_HAMBURGER || widgetId == MapWidgetId.SETTINGS_SHORTCUT

    fun defaultSizePx(widgetId: MapWidgetId, density: DensityScale): Float =
        density.dpToPx(defaultSizeDp(widgetId))

    fun clampSizePx(
        widgetId: MapWidgetId,
        requestedSizePx: Float,
        screenWidthPx: Float,
        screenHeightPx: Float,
        density: DensityScale
    ): Float {
        val minPx = density.dpToPx(minSizeDp(widgetId))
        val maxPx = density.dpToPx(maxSizeDp(widgetId))
        val screenBoundedMaxPx = min(maxPx, min(screenWidthPx, screenHeightPx).coerceAtLeast(minPx))
        return requestedSizePx.coerceIn(minPx, screenBoundedMaxPx)
    }

    private fun defaultSizeDp(widgetId: MapWidgetId): Float = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> SIDE_HAMBURGER_DEFAULT_DP
        MapWidgetId.SETTINGS_SHORTCUT -> SETTINGS_SHORTCUT_DEFAULT_DP
        else -> 0f
    }

    private fun minSizeDp(widgetId: MapWidgetId): Float = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> SIDE_HAMBURGER_MIN_DP
        MapWidgetId.SETTINGS_SHORTCUT -> SETTINGS_SHORTCUT_MIN_DP
        else -> 0f
    }

    private fun maxSizeDp(widgetId: MapWidgetId): Float = when (widgetId) {
        MapWidgetId.SIDE_HAMBURGER -> SIDE_HAMBURGER_MAX_DP
        MapWidgetId.SETTINGS_SHORTCUT -> SETTINGS_SHORTCUT_MAX_DP
        else -> 0f
    }
}
