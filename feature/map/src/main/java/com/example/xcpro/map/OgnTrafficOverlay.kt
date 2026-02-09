package com.example.xcpro.map

import android.os.SystemClock
import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.ogn.OgnSubscriptionPolicy
import com.example.xcpro.ogn.OgnTrafficTarget
import com.example.xcpro.ogn.OgnViewportBounds
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs

/**
 * Runtime map overlay for OGN traffic targets.
 * Keeps all MapLibre source/layer state local to the UI runtime.
 */
class OgnTrafficOverlay(
    private val map: MapLibreMap
) {

    fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(CIRCLE_LAYER_ID) == null) {
                val circleLayer = CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        circleRadius(CIRCLE_RADIUS_DP),
                        circleColor(CIRCLE_COLOR),
                        circleStrokeColor(CIRCLE_STROKE_COLOR),
                        circleStrokeWidth(CIRCLE_STROKE_WIDTH_DP),
                        circleOpacity(Expression.get(PROP_ALPHA))
                    )
                val anchorId = BlueLocationOverlay.LAYER_ID
                if (style.getLayer(anchorId) != null) {
                    style.addLayerBelow(circleLayer, anchorId)
                } else {
                    style.addLayer(circleLayer)
                }
            }
            if (style.getLayer(LABEL_LAYER_ID) == null) {
                val labelLayer = SymbolLayer(LABEL_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        textField(Expression.get(PROP_LABEL)),
                        textSize(LABEL_TEXT_SIZE_SP),
                        textColor(LABEL_TEXT_COLOR),
                        textHaloColor(LABEL_HALO_COLOR),
                        textHaloWidth(LABEL_HALO_WIDTH_DP),
                        textOffset(arrayOf(0f, LABEL_TEXT_OFFSET_Y)),
                        textAnchor("top"),
                        textAllowOverlap(true),
                        textIgnorePlacement(true)
                    )
                if (style.getLayer(CIRCLE_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, CIRCLE_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN overlay: ${t.message}", t)
        }
    }

    fun render(targets: List<OgnTrafficTarget>) {
        initialize()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val nowMonoMs = SystemClock.elapsedRealtime()
        val visibleBounds = map.projection.visibleRegion?.latLngBounds
        val features = ArrayList<Feature>(targets.size.coerceAtMost(MAX_TARGETS))

        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            if (!isValidCoordinate(target.latitude, target.longitude)) continue
            if (!isInVisibleBounds(target.latitude, target.longitude, visibleBounds)) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(target.longitude, target.latitude)
            )
            feature.addStringProperty(PROP_LABEL, target.displayLabel)
            feature.addNumberProperty(
                PROP_ALPHA,
                if (target.isStale(nowMonoMs, STALE_VISUAL_AFTER_MS)) STALE_ALPHA else LIVE_ALPHA
            )
            features.add(feature)
        }

        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(LABEL_LAYER_ID)
            style.removeLayer(CIRCLE_LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN overlay: ${t.message}")
        }
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private fun isInVisibleBounds(
        latitude: Double,
        longitude: Double,
        bounds: LatLngBounds?
    ): Boolean {
        if (bounds == null) return true
        return OgnSubscriptionPolicy.isInViewport(
            latitude = latitude,
            longitude = longitude,
            bounds = OgnViewportBounds(
                northLat = bounds.latitudeNorth,
                southLat = bounds.latitudeSouth,
                eastLon = bounds.longitudeEast,
                westLon = bounds.longitudeWest
            )
        )
    }

    private companion object {
        private const val TAG = "OgnTrafficOverlay"

        private const val SOURCE_ID = "ogn-traffic-source"
        private const val CIRCLE_LAYER_ID = "ogn-traffic-circle-layer"
        private const val LABEL_LAYER_ID = "ogn-traffic-label-layer"

        private const val PROP_LABEL = "label"
        private const val PROP_ALPHA = "alpha"

        private const val MAX_TARGETS = 500
        private const val STALE_VISUAL_AFTER_MS = 60_000L

        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val CIRCLE_RADIUS_DP = 4.5f
        private const val CIRCLE_STROKE_WIDTH_DP = 1.25f
        private const val CIRCLE_COLOR = "#00B0FF"
        private const val CIRCLE_STROKE_COLOR = "#0B1E2D"

        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_HALO_WIDTH_DP = 1.1f
        private const val LABEL_TEXT_OFFSET_Y = 1.1f
        private const val LABEL_TEXT_COLOR = "#EAF4FF"
        private const val LABEL_HALO_COLOR = "#0B1E2D"
    }
}
