package com.example.xcpro.map

import com.example.xcpro.core.common.logging.AppLogger
import kotlin.math.abs
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class OgnTargetRingOverlay(
    private val map: MapLibreMap,
    initialIconSizePx: Int = OGN_ICON_SIZE_DEFAULT_PX
) : OgnTargetRingOverlayHandle {
    private var currentIconSizePx: Int = clampOgnRenderedIconSizePx(initialIconSizePx)

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(LAYER_ID) == null) {
                addLayer(style)
            }
            applyRingSizeToStyle()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN target ring overlay: ${t.message}", t)
        }
    }

    override fun setIconSizePx(iconSizePx: Int) {
        val clamped = clampOgnRenderedIconSizePx(iconSizePx)
        if (currentIconSizePx == clamped) return
        currentIconSizePx = clamped
        applyRingSizeToStyle()
    }

    override fun render(enabled: Boolean, target: OgnTrafficTarget?) {
        if (!enabled || target == null || !isValidCoordinate(target.latitude, target.longitude)) {
            clear()
            return
        }
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val feature = Feature.fromGeometry(
            Point.fromLngLat(target.longitude, target.latitude)
        )
        feature.addStringProperty(PROP_TARGET_KEY, target.canonicalKey)
        feature.addStringProperty(PROP_TARGET_ID, target.id)
        source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(feature)))
    }

    override fun findTargetAt(tap: LatLng): String? {
        val style = map.style ?: return null
        if (style.getSource(SOURCE_ID) == null || style.getLayer(LAYER_ID) == null) return null
        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(screenPoint, LAYER_ID)
        }.getOrNull().orEmpty()
        for (feature in features) {
            val targetKey = resolveOgnTrafficTargetKey(feature)
            if (targetKey != null) return targetKey
        }
        return null
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
            AppLogger.w(TAG, "Failed to cleanup OGN target ring overlay: ${t.message}")
        }
    }

    override fun bringToFront() {
        val style = map.style ?: return
        if (style.getLayer(LAYER_ID) == null) return
        try {
            style.removeLayer(LAYER_ID)
            addLayer(style)
            applyRingSizeToStyle()
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN target ring overlay to front: ${t.message}")
        }
    }

    private fun addLayer(style: Style) {
        val layer = createLayer()
        when {
            style.getLayer(TOP_LABEL_LAYER_ID) != null -> style.addLayerAbove(layer, ICON_LAYER_ID)
            style.getLayer(ICON_LAYER_ID) != null -> style.addLayerAbove(layer, ICON_LAYER_ID)
            style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null ->
                style.addLayerAbove(layer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)

            else -> style.addLayer(layer)
        }
    }

    private fun createLayer(): CircleLayer =
        CircleLayer(LAYER_ID, SOURCE_ID).withProperties(
            circleColor("rgba(0,0,0,0)"),
            circleStrokeColor(RING_STROKE_COLOR),
            circleStrokeWidth(resolveOgnTargetRingStrokeWidthPx(currentIconSizePx)),
            circleOpacity(RING_OPACITY),
            circleRadius(resolveOgnTargetRingRadiusPx(currentIconSizePx))
        )

    private fun applyRingSizeToStyle() {
        val style = map.style ?: return
        val layer = style.getLayer(LAYER_ID) as? CircleLayer ?: return
        layer.setProperties(
            circleStrokeWidth(resolveOgnTargetRingStrokeWidthPx(currentIconSizePx)),
            circleRadius(resolveOgnTargetRingRadiusPx(currentIconSizePx))
        )
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private companion object {
        private const val TAG = "OgnTargetRingOverlay"
        private const val SOURCE_ID = "ogn-target-ring-source"
        private const val LAYER_ID = "ogn-target-ring-layer"
        private const val RING_OPACITY = 0.95f
        private const val RING_STROKE_COLOR = "#E0B52E"
    }
}

internal fun resolveOgnTargetRingRadiusPx(iconSizePx: Int): Float =
    (clampOgnRenderedIconSizePx(iconSizePx) * OGN_TARGET_RING_RADIUS_MULTIPLIER)
        .coerceIn(OGN_TARGET_RING_RADIUS_MIN_PX, OGN_TARGET_RING_RADIUS_MAX_PX)

internal fun resolveOgnTargetRingStrokeWidthPx(iconSizePx: Int): Float =
    (clampOgnRenderedIconSizePx(iconSizePx) / OGN_TARGET_RING_STROKE_DIVISOR)
        .coerceIn(OGN_TARGET_RING_STROKE_MIN_PX, OGN_TARGET_RING_STROKE_MAX_PX)

private const val OGN_TARGET_RING_RADIUS_MULTIPLIER = 0.23f
private const val OGN_TARGET_RING_RADIUS_MIN_PX = 10.5f
private const val OGN_TARGET_RING_RADIUS_MAX_PX = 72f
private const val OGN_TARGET_RING_STROKE_DIVISOR = 64f
private const val OGN_TARGET_RING_STROKE_MIN_PX = 1.0f
private const val OGN_TARGET_RING_STROKE_MAX_PX = 3.0f
