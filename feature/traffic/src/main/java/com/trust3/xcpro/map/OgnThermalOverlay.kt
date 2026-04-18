package com.trust3.xcpro.map

import android.graphics.Color
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.ogn.displayClimbRateMps
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class OgnThermalOverlay(
    private val map: MapLibreMap
) : OgnThermalOverlayHandle {

    override fun initialize() {
        val style = map.style ?: return
        try {
            if (style.getSource(SOURCE_ID) == null) {
                style.addSource(GeoJsonSource(SOURCE_ID))
            }
            if (style.getLayer(CIRCLE_LAYER_ID) == null) {
                val layer = createCircleLayer()
                val ognIconLayer = style.getLayer(OGN_ICON_LAYER_ID)
                if (ognIconLayer != null) {
                    style.addLayerBelow(layer, OGN_ICON_LAYER_ID)
                } else {
                    style.addLayer(layer)
                }
            }
            if (style.getLayer(LABEL_LAYER_ID) == null) {
                val labelLayer = createLabelLayer()
                if (style.getLayer(CIRCLE_LAYER_ID) != null) {
                    style.addLayerAbove(labelLayer, CIRCLE_LAYER_ID)
                } else {
                    style.addLayer(labelLayer)
                }
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to initialize OGN thermal overlay: ${t.message}", t)
        }
    }

    override fun render(hotspots: List<OgnThermalHotspot>) {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return

        val features = ArrayList<Feature>(hotspots.size)
        for (hotspot in hotspots) {
            if (!isValidCoordinate(hotspot.latitude, hotspot.longitude)) continue
            val feature = Feature.fromGeometry(
                Point.fromLngLat(hotspot.longitude, hotspot.latitude)
            )
            feature.addStringProperty(PROP_HOTSPOT_ID, hotspot.id)
            feature.addNumberProperty(PROP_COLOR_INDEX, hotspot.snailColorIndex)
            feature.addStringProperty(PROP_LABEL, thermalHotspotOverlayLabel(hotspot))
            feature.addNumberProperty(PROP_ACTIVE, if (hotspot.state == OgnThermalHotspotState.ACTIVE) 1 else 0)
            features.add(feature)
        }

        try {
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to render OGN thermal overlay: ${t.message}", t)
        }
    }

    override fun findTargetAt(tap: LatLng): String? {
        val style = map.style ?: return null
        if (style.getSource(SOURCE_ID) == null) return null

        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(
                screenPoint,
                CIRCLE_LAYER_ID,
                LABEL_LAYER_ID
            )
        }.getOrNull().orEmpty()

        for (feature in features) {
            if (!feature.hasProperty(PROP_HOTSPOT_ID)) continue
            val id = runCatching { feature.getStringProperty(PROP_HOTSPOT_ID) }.getOrNull()
            val normalized = id?.trim().orEmpty()
            if (normalized.isNotEmpty()) return normalized
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
            style.removeLayer(LABEL_LAYER_ID)
            style.removeLayer(CIRCLE_LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN thermal overlay: ${t.message}")
        }
    }

    private fun createCircleLayer(): CircleLayer {
        val colorStops = snailColorHexStops().mapIndexed { index, hex ->
            Expression.stop(index, Expression.color(Color.parseColor(hex)))
        }.toTypedArray()

        val colorExpression = Expression.match(
            Expression.get(PROP_COLOR_INDEX),
            Expression.color(Color.parseColor(DEFAULT_COLOR)),
            *colorStops
        )

        val activeOpacityExpression = Expression.match(
            Expression.get(PROP_ACTIVE),
            Expression.literal(FINALIZED_ALPHA),
            Expression.stop(1, Expression.literal(ACTIVE_ALPHA))
        )

        return CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID)
            .withProperties(
                circleColor(colorExpression),
                circleRadius(CIRCLE_RADIUS_DP),
                circleOpacity(activeOpacityExpression),
                circleStrokeColor("#0A1E2E"),
                circleStrokeWidth(CIRCLE_STROKE_WIDTH_DP)
            )
    }

    private fun createLabelLayer(): SymbolLayer =
        SymbolLayer(LABEL_LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(PROP_LABEL)),
                textSize(LABEL_TEXT_SIZE_SP),
                textColor("#EAF4FF"),
                textHaloColor("#0B1E2D"),
                textHaloWidth(LABEL_HALO_WIDTH_DP),
                textOffset(arrayOf(0f, LABEL_TEXT_OFFSET_Y)),
                textAnchor("top"),
                textAllowOverlap(true),
                textIgnorePlacement(true)
            )

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0
    }

    private companion object {
        private const val TAG = "OgnThermalOverlay"

        private const val SOURCE_ID = "ogn-thermal-source"
        private const val CIRCLE_LAYER_ID = "ogn-thermal-circle-layer"
        private const val LABEL_LAYER_ID = "ogn-thermal-label-layer"

        private const val OGN_ICON_LAYER_ID = "ogn-traffic-icon-layer"

        private const val PROP_HOTSPOT_ID = "hotspot_id"
        private const val PROP_COLOR_INDEX = "color_index"
        private const val PROP_LABEL = "label"
        private const val PROP_ACTIVE = "active"

        private const val DEFAULT_COLOR = "#FFF4B0"
        private const val CIRCLE_RADIUS_DP = 10f
        private const val CIRCLE_STROKE_WIDTH_DP = 1.5f
        private const val ACTIVE_ALPHA = 0.90
        private const val FINALIZED_ALPHA = 0.65

        private const val LABEL_TEXT_SIZE_SP = 10f
        private const val LABEL_HALO_WIDTH_DP = 1.0f
        private const val LABEL_TEXT_OFFSET_Y = 1.1f
    }
}

internal fun thermalHotspotOverlayLabel(hotspot: OgnThermalHotspot): String {
    val climb = hotspot.displayClimbRateMps() ?: return hotspot.sourceLabel
    val signed = if (climb >= 0.0) "+" else ""
    return "${signed}${String.format(java.util.Locale.US, "%.1f", climb)}"
}
