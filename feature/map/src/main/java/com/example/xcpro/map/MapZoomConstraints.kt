package com.example.xcpro.map

import kotlin.math.cos
import kotlin.math.log2
import org.maplibre.android.constants.MapLibreConstants

object MapZoomConstraints {
    const val MIN_SCALE_BAR_METERS = 600.0
    const val SCALE_BAR_MAX_WIDTH_RATIO = 0.35f
    private const val METERS_PER_PIXEL_EQUATOR = 156543.03392
    private const val SCALE_STEP_EPSILON_METERS = 1.0
    private val METRIC_SCALE_STEPS_METERS = intArrayOf(
        1, 2, 4, 10, 20, 50, 75, 100, 150, 200, 300, 500,
        1000, 1500, 3000, 5000, 10000, 20000, 30000, 50000,
        100000, 200000, 300000, 400000, 500000, 600000, 800000,
        1000000, 2000000, 3000000, 4000000, 5000000, 6000000,
        8000000, 10000000, 12000000, 15000000
    )

    fun maxZoomForMinScaleMeters(
        widthPx: Int,
        latitude: Double,
        minScaleMeters: Double = MIN_SCALE_BAR_METERS,
        maxWidthRatio: Float = SCALE_BAR_MAX_WIDTH_RATIO,
        pixelRatio: Float = 1.0f
    ): Double? {
        if (widthPx <= 0 || minScaleMeters <= 0.0) return null
        val resolvedMinScaleMeters = resolveMinScaleMeters(minScaleMeters)
        val ratio = if (pixelRatio > 0f) pixelRatio.toDouble() else 1.0
        val effectiveWidthPx = (widthPx.toDouble() / ratio) * maxWidthRatio
        if (effectiveWidthPx <= 0.0) return null
        val metersPerPixel = resolvedMinScaleMeters / effectiveWidthPx
        val latRad = Math.toRadians(latitude)
        val zoomRatio = METERS_PER_PIXEL_EQUATOR * cos(latRad) / metersPerPixel
        if (!zoomRatio.isFinite() || zoomRatio <= 0.0) return null
        val zoom = log2(zoomRatio)
        return zoom.coerceIn(
            MapLibreConstants.MINIMUM_ZOOM.toDouble(),
            MapLibreConstants.MAXIMUM_ZOOM.toDouble()
        )
    }

    fun clampZoom(
        zoom: Double,
        widthPx: Int,
        latitude: Double,
        minScaleMeters: Double = MIN_SCALE_BAR_METERS,
        maxWidthRatio: Float = SCALE_BAR_MAX_WIDTH_RATIO,
        pixelRatio: Float = 1.0f
    ): Double {
        val maxZoom = maxZoomForMinScaleMeters(widthPx, latitude, minScaleMeters, maxWidthRatio, pixelRatio) ?: return zoom
        return if (zoom > maxZoom) maxZoom else zoom
    }

    private fun resolveMinScaleMeters(minScaleMeters: Double): Double {
        if (minScaleMeters <= 0.0) return minScaleMeters
        val step = METRIC_SCALE_STEPS_METERS.firstOrNull { it.toDouble() >= minScaleMeters }?.toDouble()
            ?: minScaleMeters
        return step + SCALE_STEP_EPSILON_METERS
    }
}
