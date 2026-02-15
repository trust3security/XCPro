package com.example.xcpro.map

import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.clampForecastOpacity
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import java.util.Locale

class ForecastRasterOverlay(
    private val map: MapLibreMap
) {
    private var lastTileSpec: ForecastTileSpec? = null
    private var lastOpacity: Float = 1.0f
    private var lastLegendSpec: ForecastLegendSpec? = null

    fun render(
        tileSpec: ForecastTileSpec,
        opacity: Float,
        legendSpec: ForecastLegendSpec?
    ) {
        val style = map.style ?: return
        val resolvedOpacity = clampForecastOpacity(opacity)
        when (tileSpec.format) {
            ForecastTileFormat.RASTER -> {
                removeVectorLayerAndSource(style)
                ensureRasterSource(style, tileSpec)
                ensureRasterLayer(style, resolvedOpacity)
            }
            ForecastTileFormat.VECTOR_INDEXED_FILL -> {
                removeRasterLayerAndSource(style)
                ensureVectorSource(style, tileSpec)
                ensureVectorLayer(style, tileSpec, legendSpec, resolvedOpacity)
            }
        }
        lastTileSpec = tileSpec
        lastOpacity = resolvedOpacity
        lastLegendSpec = legendSpec
    }

    fun clear() {
        val style = map.style ?: return
        removeRasterLayerAndSource(style)
        removeVectorLayerAndSource(style)
        lastTileSpec = null
        lastLegendSpec = null
    }

    fun cleanup() = clear()

    private fun ensureRasterSource(style: Style, tileSpec: ForecastTileSpec) {
        val existing = style.getSource(RASTER_SOURCE_ID)
        val needsRecreate = existing !is RasterSource || tileSpec != lastTileSpec
        if (!needsRecreate) return

        removeRasterLayerAndSource(style)

        val tileSet = TileSet("2.2.0", tileSpec.urlTemplate).apply {
            minZoom = tileSpec.minZoom.toFloat()
            maxZoom = tileSpec.maxZoom.toFloat()
            attribution = tileSpec.attribution
        }
        style.addSource(RasterSource(RASTER_SOURCE_ID, tileSet, tileSpec.tileSizePx))
    }

    private fun ensureRasterLayer(style: Style, opacity: Float) {
        val existingLayer = style.getLayer(RASTER_LAYER_ID) as? RasterLayer
        if (existingLayer != null) {
            existingLayer.setProperties(rasterOpacity(opacity))
            return
        }

        val layer = RasterLayer(RASTER_LAYER_ID, RASTER_SOURCE_ID).withProperties(
            rasterOpacity(opacity)
        )
        addLayerBelowAnchor(style, layer)
    }

    private fun ensureVectorSource(style: Style, tileSpec: ForecastTileSpec) {
        val existing = style.getSource(VECTOR_SOURCE_ID)
        val needsRecreate = existing !is VectorSource || tileSpec != lastTileSpec
        if (!needsRecreate) return

        removeVectorLayerAndSource(style)

        val tileSet = TileSet("2.2.0", tileSpec.urlTemplate).apply {
            minZoom = tileSpec.minZoom.toFloat()
            maxZoom = tileSpec.maxZoom.toFloat()
            attribution = tileSpec.attribution
        }
        style.addSource(VectorSource(VECTOR_SOURCE_ID, tileSet))
    }

    private fun ensureVectorLayer(
        style: Style,
        tileSpec: ForecastTileSpec,
        legendSpec: ForecastLegendSpec?,
        opacity: Float
    ) {
        val existingLayer = style.getLayer(VECTOR_LAYER_ID) as? FillLayer
        if (existingLayer != null && legendSpec == lastLegendSpec) {
            existingLayer.setProperties(fillOpacity(opacity))
            return
        }

        runCatching { style.removeLayer(VECTOR_LAYER_ID) }
        val layer = FillLayer(VECTOR_LAYER_ID, VECTOR_SOURCE_ID).apply {
            setSourceLayer(tileSpec.sourceLayer ?: DEFAULT_VECTOR_SOURCE_LAYER)
        }.withProperties(
            fillColor(buildIndexedColorExpression(legendSpec, tileSpec.valueProperty)),
            fillOpacity(opacity)
        )
        addLayerBelowAnchor(style, layer)
    }

    private fun buildIndexedColorExpression(
        legendSpec: ForecastLegendSpec?,
        valueProperty: String
    ): Expression {
        val colors = legendSpec
            ?.stops
            ?.map { toColorHex(it.argb) }
            ?.toTypedArray()
            ?: emptyArray()
        if (colors.isEmpty()) {
            return Expression.literal("#000000")
        }
        return Expression.toColor(
            Expression.coalesce(
                Expression.at(
                    Expression.toNumber(Expression.get(valueProperty)),
                    Expression.literal(colors)
                ),
                Expression.literal(colors.first())
            )
        )
    }

    private fun toColorHex(argb: Int): String {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    }

    private fun addLayerBelowAnchor(style: Style, layer: org.maplibre.android.style.layers.Layer) {
        val anchorLayer = ANCHOR_LAYER_IDS.firstOrNull { id ->
            style.getLayer(id) != null
        }
        if (anchorLayer != null) {
            style.addLayerBelow(layer, anchorLayer)
        } else {
            style.addLayer(layer)
        }
    }

    private fun removeRasterLayerAndSource(style: Style) {
        runCatching { style.removeLayer(RASTER_LAYER_ID) }
        runCatching { style.removeSource(RASTER_SOURCE_ID) }
    }

    private fun removeVectorLayerAndSource(style: Style) {
        runCatching { style.removeLayer(VECTOR_LAYER_ID) }
        runCatching { style.removeSource(VECTOR_SOURCE_ID) }
    }

    private companion object {
        const val RASTER_SOURCE_ID = "forecast-raster-source"
        const val RASTER_LAYER_ID = "forecast-raster-layer"
        const val VECTOR_SOURCE_ID = "forecast-vector-source"
        const val VECTOR_LAYER_ID = "forecast-vector-layer"
        const val DEFAULT_VECTOR_SOURCE_LAYER = "1800"

        val ANCHOR_LAYER_IDS = listOf(
            "airspace-layer",
            "racing-course-line",
            "racing-turnpoint-areas-fill",
            "racing-waypoints",
            "aat-task-line",
            "aat-areas-layer",
            "aat-waypoints",
            "adsb-traffic-icon-layer",
            "ogn-traffic-icon-layer",
            BlueLocationOverlay.LAYER_ID
        )
    }
}
