package com.example.xcpro.map

import com.example.xcpro.common.units.AltitudeUnit
import com.example.xcpro.common.units.UnitsPreferences
import com.example.xcpro.core.common.logging.AppLogger
import kotlin.math.abs
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
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

class OgnOwnshipTargetBadgeOverlay(
    private val map: MapLibreMap
) : OgnOwnshipTargetBadgeOverlayHandle {

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
            AppLogger.e(TAG, "Failed to initialize OGN ownship target badge overlay: ${t.message}", t)
        }
    }

    override fun render(
        enabled: Boolean,
        ownshipLocation: OverlayCoordinate?,
        target: OgnTrafficTarget?,
        ownshipAltitudeMeters: Double?,
        altitudeUnit: AltitudeUnit,
        unitsPreferences: UnitsPreferences
    ) {
        val ownship = ownshipLocation ?: run {
            clear()
            return
        }
        if (!isValidCoordinate(ownship.latitude, ownship.longitude)) {
            clear()
            return
        }
        if (target == null || !isValidCoordinate(target.latitude, target.longitude)) {
            clear()
            return
        }
        val renderModel = OgnOwnshipTargetBadgeRenderModelBuilder.build(
            OgnOwnshipTargetBadgeRenderRequest(
                enabled = enabled,
                target = target,
                ownshipAltitudeMeters = ownshipAltitudeMeters,
                altitudeUnit = altitudeUnit,
                unitsPreferences = unitsPreferences
            )
        ) ?: run {
            clear()
            return
        }

        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val layer = style.getLayerAs<SymbolLayer>(LAYER_ID) ?: return
        val feature = Feature.fromGeometry(
            Point.fromLngLat(ownship.longitude, ownship.latitude)
        )
        feature.addStringProperty(PROP_LABEL, renderModel.labelText)
        source.setGeoJson(FeatureCollection.fromFeatures(arrayOf(feature)))
        layer.setProperties(textColor(renderModel.textColorHex))
    }

    override fun cleanup() {
        val style = map.style ?: return
        try {
            style.removeLayer(LAYER_ID)
            style.removeSource(SOURCE_ID)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to cleanup OGN ownship target badge overlay: ${t.message}")
        }
    }

    override fun bringToFront() {
        val style = map.style ?: return
        if (style.getLayer(LAYER_ID) == null) return
        try {
            style.removeLayer(LAYER_ID)
            addLayer(style)
        } catch (t: Throwable) {
            AppLogger.w(TAG, "Failed to bring OGN ownship target badge overlay to front: ${t.message}")
        }
    }

    private fun addLayer(style: Style) {
        val layer = createLayer()
        when {
            style.getLayer(BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK) != null ->
                style.addLayerAbove(layer, BLUE_LOCATION_OVERLAY_LAYER_ID_FALLBACK)

            style.getLayer(TOP_LABEL_LAYER_ID) != null -> style.addLayerAbove(layer, TOP_LABEL_LAYER_ID)
            style.getLayer(ICON_LAYER_ID) != null -> style.addLayerAbove(layer, ICON_LAYER_ID)
            else -> style.addLayer(layer)
        }
    }

    private fun clear() {
        val style = map.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    private fun createLayer(): SymbolLayer =
        SymbolLayer(LAYER_ID, SOURCE_ID)
            .withProperties(
                textField(Expression.get(PROP_LABEL)),
                textSize(TEXT_SIZE_SP),
                textColor(OgnOwnshipTargetBadgeRenderModelBuilder.ABOVE_OR_LEVEL_TEXT_COLOR_HEX),
                textHaloColor(TEXT_HALO_COLOR_HEX),
                textHaloWidth(TEXT_HALO_WIDTH_DP),
                textOffset(arrayOf(TEXT_OFFSET_X_EM, TEXT_OFFSET_Y_EM)),
                textAnchor("left"),
                textAllowOverlap(true),
                textIgnorePlacement(true)
            )

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (abs(latitude) > 90.0) return false
        if (abs(longitude) > 180.0) return false
        return true
    }

    private companion object {
        private const val TAG = "OgnOwnshipBadgeOverlay"
        private const val SOURCE_ID = "ogn-ownship-target-badge-source"
        private const val LAYER_ID = "ogn-ownship-target-badge-layer"
        private const val PROP_LABEL = "label"
        private const val TEXT_SIZE_SP = 14.5f
        private const val TEXT_HALO_COLOR_HEX = "#FFFFFF"
        private const val TEXT_HALO_WIDTH_DP = 1.8f
        private const val TEXT_OFFSET_X_EM = 5.0f
        private const val TEXT_OFFSET_Y_EM = 0.12f
    }
}
