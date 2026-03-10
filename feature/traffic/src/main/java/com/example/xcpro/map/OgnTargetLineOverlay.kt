package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger
import kotlin.math.abs
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class OgnTargetLineOverlay(
    private val map: MapLibreMap
) : OgnTargetLineOverlayHandle {
    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(LAYER_ID) == null) {
                addLayer(style)
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN target line overlay: ${t.message}", t)
        }
    }

    override fun render(enabled: Boolean, ownshipLocation: OverlayCoordinate?, target: OgnTrafficTarget?) {
        if (!enabled || ownshipLocation == null || target == null) {
            clear()
            return
        }
        if (!isValidCoordinate(ownshipLocation.latitude, ownshipLocation.longitude) ||
            !isValidCoordinate(target.latitude, target.longitude)
        ) {
            clear()
            return
        }
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val lineFeature = Feature.fromGeometry(
            LineString.fromLngLats(
                listOf(
                    Point.fromLngLat(ownshipLocation.longitude, ownshipLocation.latitude),
                    Point.fromLngLat(target.longitude, target.latitude)
                )
            )
        )
        source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(lineFeature)))
    }

    fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    override fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN target line overlay: ${t.message}")
        }
    }

    override fun bringToFront() {
        val style = map.style ?: return
        if (style.getLayer(LAYER_ID) == null) return
        try {
            style.removeLayer(LAYER_ID)
            addLayer(style)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN target line overlay to front: ${t.message}")
        }
    }

    private fun addLayer(style: Style) {
        val layer = createLayer()
        when {
            style.getLayer(ICON_LAYER_ID) != null -> style.addLayerBelow(layer, ICON_LAYER_ID)
            style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null ->
                style.addLayerAbove(layer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)

            else -> style.addLayer(layer)
        }
    }

    private fun createLayer(): LineLayer =
        LineLayer(LAYER_ID, SOURCE_ID).withProperties(
            lineColor(LINE_COLOR),
            lineWidth(LINE_WIDTH_PX),
            lineOpacity(LINE_OPACITY),
            lineCap("round"),
            lineJoin("round")
        )

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private companion object {
        private const val TAG = "OgnTargetLineOverlay"
        private const val SOURCE_ID = "ogn-target-line-source"
        private const val LAYER_ID = "ogn-target-line-layer"
        private const val LINE_COLOR = "#F5C842"
        private const val LINE_WIDTH_PX = 2.4f
        private const val LINE_OPACITY = 0.78f
    }
}
