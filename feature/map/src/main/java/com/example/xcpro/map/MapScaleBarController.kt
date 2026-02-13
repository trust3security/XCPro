package com.example.xcpro.map

import android.view.ViewGroup
import kotlin.math.max
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.scalebar.ScaleBarOptions
import org.maplibre.android.plugins.scalebar.ScaleBarPlugin

internal class MapScaleBarController(
    private val mapState: MapScreenState
) {
    companion object {
        private const val SCALE_BAR_TEXT_SIZE_SP = 12f
        private const val SCALE_BAR_BAR_HEIGHT_DP = 6f
        private const val SCALE_BAR_TEXT_MARGIN_DP = 2f
        private const val SCALE_BAR_BORDER_WIDTH_DP = 1f
        private const val SCALE_BAR_TEXT_BORDER_WIDTH_DP = 2f
        private const val SCALE_BAR_REFRESH_INTERVAL_MS = 200
        private const val SCALE_BAR_DISTANCE_EPSILON = 1e-6
    }

    private var scaleBarLayoutListenerInstalled = false
    private var lastScaleBarWidth = 0
    private var lastScaleBarHeight = 0
    private var lastScaleBarDistancePerPixel = 0.0

    fun setupScaleBar(map: MapLibreMap) {
        val mapView = mapState.mapView ?: return
        mapView.post { updateScaleBar(map, forceCreate = true) }
        if (!scaleBarLayoutListenerInstalled) {
            scaleBarLayoutListenerInstalled = true
            mapView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateScaleBar(map, forceCreate = true)
                }
            }
        }
    }

    fun onCameraIdle(map: MapLibreMap) {
        updateScaleBar(map)
    }

    private fun updateScaleBar(map: MapLibreMap, forceCreate: Boolean = false) {
        val mapView = mapState.mapView ?: return
        val width = mapView.width
        val height = mapView.height
        if (width <= 0 || height <= 0) return

        val distancePerPixel = resolveDistancePerPixel(map, mapView)
        val sizeChanged = width != lastScaleBarWidth || height != lastScaleBarHeight
        val distanceChanged = kotlin.math.abs(distancePerPixel - lastScaleBarDistancePerPixel) > SCALE_BAR_DISTANCE_EPSILON
        if (!forceCreate && !sizeChanged && !distanceChanged && mapState.scaleBarWidget != null) {
            return
        }

        lastScaleBarWidth = width
        lastScaleBarHeight = height
        lastScaleBarDistancePerPixel = distancePerPixel
        applyMaxZoomPreference(map)

        val resources = mapView.resources
        val density = resources.displayMetrics.density
        val scaledDensity = density * resources.configuration.fontScale

        val textSizePx = SCALE_BAR_TEXT_SIZE_SP * scaledDensity
        val barHeightPx = SCALE_BAR_BAR_HEIGHT_DP * density
        val textBarMarginPx = SCALE_BAR_TEXT_MARGIN_DP * density
        val borderWidthPx = SCALE_BAR_BORDER_WIDTH_DP * density
        val textBorderWidthPx = SCALE_BAR_TEXT_BORDER_WIDTH_DP * density
        val textColorRes = android.R.color.black
        val primaryColorRes = android.R.color.black
        val secondaryColorRes = android.R.color.white
        val textColor = mapView.context.getColor(textColorRes)
        val primaryColor = mapView.context.getColor(primaryColorRes)
        val secondaryColor = mapView.context.getColor(secondaryColorRes)

        val contentHeightPx = textSizePx + textBarMarginPx + barHeightPx + borderWidthPx * 2f
        val marginTopPx = max(0f, (height - contentHeightPx) / 2f)
        val maxDistanceMeters = if (distancePerPixel.isFinite() && distancePerPixel > 0.0) {
            width * distancePerPixel * MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO
        } else {
            0.0
        }
        val scaleDistanceMeters = MapZoomConstraints.resolveScaleBarDistanceMeters(maxDistanceMeters)
        val barWidthPx = if (scaleDistanceMeters != null && distancePerPixel > 0.0) {
            (scaleDistanceMeters / distancePerPixel).toFloat()
        } else {
            width * MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO
        }
        val marginLeftPx = max(0f, (width - barWidthPx) / 2f)

        val plugin = mapState.scaleBarPlugin ?: ScaleBarPlugin(mapView, map).also {
            mapState.scaleBarPlugin = it
        }

        if (mapState.scaleBarWidget == null || sizeChanged || forceCreate) {
            val options = ScaleBarOptions(mapView.context)
                .setMetricUnit(true)
                .setRefreshInterval(SCALE_BAR_REFRESH_INTERVAL_MS)
                .setTextColor(textColorRes)
                .setPrimaryColor(primaryColorRes)
                .setSecondaryColor(secondaryColorRes)
                .setTextSize(textSizePx)
                .setBarHeight(barHeightPx)
                .setBorderWidth(borderWidthPx)
                .setTextBarMargin(textBarMarginPx)
                .setTextBorderWidth(textBorderWidthPx)
                .setShowTextBorder(true)
                .setMarginLeft(marginLeftPx)
                .setMarginTop(marginTopPx)
                .setMaxWidthRatio(MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO)

            mapState.scaleBarWidget = plugin.create(options).also { widget ->
                val params = widget.layoutParams ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                widget.layoutParams = params
                widget.visibility = android.view.View.VISIBLE
                widget.bringToFront()
            }
        } else {
            mapState.scaleBarWidget?.apply {
                setMetricUnit(true)
                setRefreshInterval(SCALE_BAR_REFRESH_INTERVAL_MS)
                setTextColor(textColor)
                setPrimaryColor(primaryColor)
                setSecondaryColor(secondaryColor)
                setTextSize(textSizePx)
                setBarHeight(barHeightPx)
                setBorderWidth(borderWidthPx)
                setTextBarMargin(textBarMarginPx)
                setTextBorderWidth(textBorderWidthPx)
                setShowTextBorder(true)
                setMarginLeft(marginLeftPx)
                setMarginTop(marginTopPx)
                setRatio(MapZoomConstraints.SCALE_BAR_MAX_WIDTH_RATIO)
                visibility = android.view.View.VISIBLE
                bringToFront()
                invalidate()
            }
        }

        plugin.setEnabled(true)
        mapView.invalidate()
    }

    private fun resolveDistancePerPixel(map: MapLibreMap, mapView: MapView): Double {
        val latitude = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(latitude)
        val pixelRatio = mapView.pixelRatio
        return if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
    }

    private fun applyMaxZoomPreference(map: MapLibreMap) {
        val mapView = mapState.mapView ?: return
        val width = mapView.width
        if (width <= 0) return
        val latitude = map.cameraPosition.target?.latitude ?: 0.0
        val metersPerPixel = map.projection.getMetersPerPixelAtLatitude(latitude)
        val pixelRatio = mapView.pixelRatio
        val distancePerPixel = if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
        val maxZoom = MapZoomConstraints.maxZoomForMinScaleMeters(
            widthPx = width,
            currentZoom = map.cameraPosition.zoom,
            distancePerPixel = distancePerPixel
        ) ?: return
        map.setMaxZoomPreference(maxZoom)
        if (map.cameraPosition.zoom > maxZoom + 1e-3) {
            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.zoomTo(maxZoom))
        }
    }
}
