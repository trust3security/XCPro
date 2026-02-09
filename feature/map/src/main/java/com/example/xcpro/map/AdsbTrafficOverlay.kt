package com.example.xcpro.map

import com.example.xcpro.adsb.AdsbTrafficUiModel
import com.example.xcpro.core.common.logging.AppLogger
import org.maplibre.android.geometry.LatLng
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
import org.maplibre.android.style.layers.PropertyFactory.textOpacity
import org.maplibre.android.style.layers.PropertyFactory.textRotate
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.maps.MapLibreMap

class AdsbTrafficOverlay(
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

            if (style.getLayer(HEADING_LAYER_ID) == null) {
                val headingLayer = SymbolLayer(HEADING_LAYER_ID, SOURCE_ID)
                    .withProperties(
                        textField(HEADING_ARROW_TEXT),
                        textSize(HEADING_TEXT_SIZE_SP),
                        textColor(HEADING_TEXT_COLOR),
                        textHaloColor(HEADING_HALO_COLOR),
                        textHaloWidth(HEADING_HALO_WIDTH_DP),
                        textRotate(Expression.get(PROP_TRACK_DEG)),
                        textAllowOverlap(true),
                        textIgnorePlacement(true),
                        textOpacity(Expression.get(PROP_ALPHA))
                    )
                if (style.getLayer(CIRCLE_LAYER_ID) != null) {
                    style.addLayerAbove(headingLayer, CIRCLE_LAYER_ID)
                } else {
                    style.addLayer(headingLayer)
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
                        textIgnorePlacement(true),
                        textOpacity(Expression.get(PROP_ALPHA))
                    )
                if (style.getLayer(HEADING_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, HEADING_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize ADS-B overlay: ${t.message}", t)
        }
    }

    fun render(targets: List<AdsbTrafficUiModel>) {
        initialize()
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val features = ArrayList<Feature>(targets.size.coerceAtMost(MAX_TARGETS))
        for (target in targets) {
            if (features.size >= MAX_TARGETS) break
            if (!target.lat.isFinite() || !target.lon.isFinite()) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(target.lon, target.lat)
            )
            feature.addStringProperty(PROP_ID, target.id.raw)
            feature.addStringProperty(PROP_LABEL, target.callsign ?: target.id.raw.uppercase())
            feature.addNumberProperty(PROP_TRACK_DEG, target.trackDeg ?: 0.0)
            feature.addNumberProperty(
                PROP_ALPHA,
                if (target.isStale) STALE_ALPHA else LIVE_ALPHA
            )
            features.add(feature)
        }
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    fun findTargetAt(
        tap: LatLng,
        targetsById: Map<String, AdsbTrafficUiModel>
    ): AdsbTrafficUiModel? {
        val style = map.style ?: return null
        if (style.getSource(SOURCE_ID) == null) return null
        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(
                screenPoint,
                CIRCLE_LAYER_ID,
                HEADING_LAYER_ID,
                LABEL_LAYER_ID
            )
        }.getOrNull().orEmpty()

        for (feature in features) {
            if (!feature.hasProperty(PROP_ID)) continue
            val id = runCatching { feature.getStringProperty(PROP_ID) }.getOrNull() ?: continue
            val found = targetsById[id]
            if (found != null) return found
        }
        return null
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
            style.removeLayer(HEADING_LAYER_ID)
            style.removeLayer(CIRCLE_LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup ADS-B overlay: ${t.message}")
        }
    }

    private companion object {
        private const val TAG = "AdsbTrafficOverlay"

        private const val SOURCE_ID = "adsb-traffic-source"
        private const val CIRCLE_LAYER_ID = "adsb-traffic-circle-layer"
        private const val HEADING_LAYER_ID = "adsb-traffic-heading-layer"
        private const val LABEL_LAYER_ID = "adsb-traffic-label-layer"

        private const val PROP_ID = "id"
        private const val PROP_LABEL = "label"
        private const val PROP_TRACK_DEG = "trackDeg"
        private const val PROP_ALPHA = "alpha"

        private const val MAX_TARGETS = 120
        private const val LIVE_ALPHA = 0.90
        private const val STALE_ALPHA = 0.45

        private const val CIRCLE_RADIUS_DP = 4.5f
        private const val CIRCLE_STROKE_WIDTH_DP = 1.25f
        private const val CIRCLE_COLOR = "#F59E0B"
        private const val CIRCLE_STROKE_COLOR = "#2B1204"

        private const val HEADING_ARROW_TEXT = "\u25B2"
        private const val HEADING_TEXT_SIZE_SP = 12f
        private const val HEADING_HALO_WIDTH_DP = 1.0f
        private const val HEADING_TEXT_COLOR = "#FFF3D4"
        private const val HEADING_HALO_COLOR = "#2B1204"

        private const val LABEL_TEXT_SIZE_SP = 11f
        private const val LABEL_HALO_WIDTH_DP = 1.1f
        private const val LABEL_TEXT_OFFSET_Y = 1.1f
        private const val LABEL_TEXT_COLOR = "#FFF3D4"
        private const val LABEL_HALO_COLOR = "#2B1204"
    }
}

