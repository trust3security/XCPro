package com.example.xcpro.map

import com.example.xcpro.forecast.ForecastLegendSpec
import com.example.xcpro.forecast.ForecastTileFormat
import com.example.xcpro.forecast.ForecastTileSpec
import com.example.xcpro.forecast.ForecastWindDisplayMode
import com.example.xcpro.forecast.clampForecastOpacity
import com.example.xcpro.forecast.clampForecastWindOverlayScale
import java.util.Locale
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource


open class ForecastRasterOverlayRuntime(
    private val map: MapLibreMap,
    private val idNamespace: String = "forecast"
) {
    private var lastTileSpec: ForecastTileSpec? = null
    private var lastLegendSpec: ForecastLegendSpec? = null
    private var activeSourceLayerCandidates: List<String> = emptyList()
    private var activeSourceLayerIndex: Int = 0
    private var sourceLayerConsecutiveMisses: Int = 0
    private var runtimeWarningMessage: String? = null
    private val windRenderer = ForecastRasterOverlayRuntimeWindRenderer(
        resolveSourceLayerForRender = ::resolveSourceLayerForRender,
        removeWindCircleLayer = ::removeWindCircleLayer,
        addLayerBelowAnchor = ::addLayerBelowAnchor,
        vectorSourceId = ::vectorSourceId,
        windSymbolLayerId = ::windSymbolLayerId,
        windArrowIconId = ::windArrowIconId,
        windArrowIconIdForColor = ::windArrowIconIdForColor,
        windBarbIconId = ::windBarbIconId,
        windBarbLayerId = ::windBarbLayerId
    )

    fun render(
        tileSpec: ForecastTileSpec,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode,
        legendSpec: ForecastLegendSpec?
    ) {
        val style = map.style ?: return
        val resolvedOpacity = clampForecastOpacity(opacity)
        val resolvedWindOverlayScale = clampForecastWindOverlayScale(windOverlayScale)
        when (tileSpec.format) {
            ForecastTileFormat.RASTER -> {
                resetSourceLayerFallbackState()
                removeVectorLayersAndSource(style)
                ensureRasterSource(style, tileSpec)
                ensureRasterLayer(style, resolvedOpacity)
            }

            ForecastTileFormat.VECTOR_INDEXED_FILL -> {
                removeRasterLayerAndSource(style)
                ensureVectorSource(style, tileSpec)
                removeWindLayers(style)
                ensureVectorFillLayer(style, tileSpec, legendSpec, resolvedOpacity)
                if (maybeAdvanceSourceLayerFallback(tileSpec)) {
                    ensureVectorFillLayer(style, tileSpec, legendSpec, resolvedOpacity)
                }
            }

            ForecastTileFormat.VECTOR_WIND_POINTS -> {
                resetSourceLayerFallbackState()
                removeRasterLayerAndSource(style)
                ensureVectorSource(style, tileSpec)
                removeVectorFillLayer(style)
                ensureWindLayers(
                    style = style,
                    tileSpec = tileSpec,
                    opacity = resolvedOpacity,
                    windOverlayScale = resolvedWindOverlayScale,
                    windDisplayMode = windDisplayMode,
                    legendSpec = legendSpec
                )
            }
        }
        lastTileSpec = tileSpec
        if (legendSpec != null) {
            lastLegendSpec = legendSpec
        }
    }

    fun clear() {
        val style = map.style ?: return
        removeRasterLayerAndSource(style)
        removeVectorLayersAndSource(style)
        lastTileSpec = null
        lastLegendSpec = null
        resetSourceLayerFallbackState()
    }

    fun cleanup() = clear()

    fun runtimeWarningMessage(): String? = runtimeWarningMessage

    fun findWindArrowSpeedAt(tap: LatLng): Double? {
        val style = map.style ?: return null
        val tileSpec = lastTileSpec ?: return null
        if (tileSpec.format != ForecastTileFormat.VECTOR_WIND_POINTS) return null
        if (style.getLayer(windSymbolLayerId()) == null) return null
        val speedProperty = tileSpec.speedProperty ?: DEFAULT_WIND_SPEED_PROPERTY
        val screenPoint = map.projection.toScreenLocation(tap)
        val features = runCatching {
            map.queryRenderedFeatures(screenPoint, windSymbolLayerId())
        }.getOrNull().orEmpty()
        for (feature in features) {
            val speed = runCatching {
                feature.getNumberProperty(speedProperty)?.toDouble()
            }.getOrNull()
            if (speed != null && speed.isFinite()) {
                return speed
            }
        }
        return null
    }

    private fun ensureRasterSource(style: Style, tileSpec: ForecastTileSpec) {
        val existing = style.getSource(rasterSourceId())
        val needsRecreate = existing !is RasterSource || tileSpec != lastTileSpec
        if (!needsRecreate) return

        removeRasterLayerAndSource(style)

        val tileSet = TileSet("2.2.0", tileSpec.urlTemplate).apply {
            minZoom = tileSpec.minZoom.toFloat()
            maxZoom = tileSpec.maxZoom.toFloat()
            attribution = tileSpec.attribution
        }
        style.addSource(RasterSource(rasterSourceId(), tileSet, tileSpec.tileSizePx))
    }

    private fun ensureRasterLayer(style: Style, opacity: Float) {
        val existingLayer = style.getLayer(rasterLayerId()) as? RasterLayer
        if (existingLayer != null) {
            existingLayer.setProperties(rasterOpacity(opacity))
            return
        }

        val layer = RasterLayer(rasterLayerId(), rasterSourceId()).withProperties(
            rasterOpacity(opacity)
        )
        addLayerBelowAnchor(style, layer)
    }

    private fun ensureVectorSource(style: Style, tileSpec: ForecastTileSpec) {
        val existing = style.getSource(vectorSourceId())
        val needsRecreate = existing !is VectorSource || tileSpec != lastTileSpec
        if (!needsRecreate) return

        removeVectorLayersAndSource(style)

        val tileSet = TileSet("2.2.0", tileSpec.urlTemplate).apply {
            minZoom = tileSpec.minZoom.toFloat()
            maxZoom = tileSpec.maxZoom.toFloat()
            attribution = tileSpec.attribution
        }
        style.addSource(VectorSource(vectorSourceId(), tileSet))
    }

    private fun ensureVectorFillLayer(
        style: Style,
        tileSpec: ForecastTileSpec,
        legendSpec: ForecastLegendSpec?,
        opacity: Float
    ) {
        val sourceLayer = resolveSourceLayerForRender(tileSpec) ?: return
        val existingLayer = style.getLayer(vectorFillLayerId()) as? FillLayer
        if (existingLayer != null) {
            existingLayer.setSourceLayer(sourceLayer)
            if (legendSpec != null && legendSpec != lastLegendSpec) {
                existingLayer.setProperties(
                    fillColor(buildIndexedColorExpression(legendSpec, tileSpec.valueProperty))
                )
            }
            existingLayer.setProperties(fillOpacity(opacity))
            return
        }

        if (legendSpec == null) {
            return
        }

        val layer = FillLayer(vectorFillLayerId(), vectorSourceId()).apply {
            setSourceLayer(sourceLayer)
        }.withProperties(
            fillColor(buildIndexedColorExpression(legendSpec, tileSpec.valueProperty)),
            fillOpacity(opacity)
        )
        addLayerBelowAnchor(style, layer)
    }

    private fun ensureWindLayers(
        style: Style,
        tileSpec: ForecastTileSpec,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode,
        legendSpec: ForecastLegendSpec?
    ) {
        windRenderer.ensureWindLayers(
            style = style,
            tileSpec = tileSpec,
            opacity = opacity,
            windOverlayScale = windOverlayScale,
            windDisplayMode = windDisplayMode,
            legendSpec = legendSpec
        )
    }

    private fun resolveSourceLayerForRender(tileSpec: ForecastTileSpec): String? {
        val candidates = buildOrderedSourceLayerCandidates(tileSpec)
        if (candidates.isEmpty()) {
            resetSourceLayerFallbackState()
            return null
        }
        if (candidates != activeSourceLayerCandidates) {
            activeSourceLayerCandidates = candidates
            activeSourceLayerIndex = 0
            sourceLayerConsecutiveMisses = 0
            runtimeWarningMessage = null
        } else if (activeSourceLayerIndex !in candidates.indices) {
            activeSourceLayerIndex = 0
            sourceLayerConsecutiveMisses = 0
            runtimeWarningMessage = null
        }
        return candidates[activeSourceLayerIndex]
    }

    private fun buildOrderedSourceLayerCandidates(tileSpec: ForecastTileSpec): List<String> {
        val ordered = mutableListOf<String>()
        val seen = HashSet<String>()

        fun addCandidate(raw: String?) {
            val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return
            val dedupeKey = normalized.lowercase(Locale.US)
            if (seen.add(dedupeKey)) {
                ordered.add(normalized)
            }
        }

        addCandidate(tileSpec.sourceLayer)
        tileSpec.sourceLayerCandidates.forEach(::addCandidate)
        return ordered
    }

    private fun maybeAdvanceSourceLayerFallback(tileSpec: ForecastTileSpec): Boolean {
        if (tileSpec.format != ForecastTileFormat.VECTOR_INDEXED_FILL) return false
        if (activeSourceLayerCandidates.size < 2) return false
        val hasFeatures = layerHasRenderedFeatures(vectorFillLayerId())
        if (hasFeatures) {
            sourceLayerConsecutiveMisses = 0
            runtimeWarningMessage = if (activeSourceLayerIndex > 0) {
                "Forecast $idNamespace overlay using fallback source-layer '${activeSourceLayerCandidates[activeSourceLayerIndex]}'."
            } else {
                null
            }
            return false
        }
        sourceLayerConsecutiveMisses += 1
        if (sourceLayerConsecutiveMisses < SOURCE_LAYER_FALLBACK_MISS_THRESHOLD) {
            return false
        }
        if (activeSourceLayerIndex >= activeSourceLayerCandidates.lastIndex) {
            runtimeWarningMessage = "Forecast $idNamespace overlay source-layer fallback exhausted (${activeSourceLayerCandidates.joinToString(", ")})."
            return false
        }
        activeSourceLayerIndex += 1
        sourceLayerConsecutiveMisses = 0
        runtimeWarningMessage = "Forecast $idNamespace overlay source-layer fallback engaged ('${activeSourceLayerCandidates[activeSourceLayerIndex]}')."
        return true
    }

    private fun layerHasRenderedFeatures(layerId: String): Boolean {
        val cameraTarget = runCatching { map.cameraPosition.target }.getOrNull() ?: return false
        val screenPoint = runCatching {
            map.projection.toScreenLocation(cameraTarget)
        }.getOrNull() ?: return false
        val features = runCatching {
            map.queryRenderedFeatures(screenPoint, layerId)
        }.getOrNull().orEmpty()
        return features.isNotEmpty()
    }

    private fun resetSourceLayerFallbackState() {
        activeSourceLayerCandidates = emptyList()
        activeSourceLayerIndex = 0
        sourceLayerConsecutiveMisses = 0
        runtimeWarningMessage = null
    }

    private fun buildIndexedColorExpression(
        legendSpec: ForecastLegendSpec,
        valueProperty: String
    ): Expression {
        val colors = legendSpec.stops.map { toColorHex(it.argb) }.toTypedArray()
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

    private fun windBarbIconId(speedKtBucket: Int): String =
        "${windBarbIconPrefix()}$speedKtBucket"

    private fun windBarbLayerId(speedKtBucket: Int): String =
        "${windBarbLayerPrefix()}$speedKtBucket"

    private fun windArrowIconIdForColor(argb: Int): String =
        "${windArrowIconPrefix()}${String.format(Locale.US, "%08X", argb)}"

    private fun toColorHex(argb: Int): String {
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        return String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
    }

    private fun addLayerBelowAnchor(style: Style, layer: Layer) {
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
        runCatching { style.removeLayer(rasterLayerId()) }
        runCatching { style.removeSource(rasterSourceId()) }
    }

    private fun removeVectorLayersAndSource(style: Style) {
        removeVectorFillLayer(style)
        removeWindLayers(style)
        runCatching { style.removeSource(vectorSourceId()) }
    }

    private fun removeVectorFillLayer(style: Style) {
        runCatching { style.removeLayer(vectorFillLayerId()) }
    }

    private fun removeWindCircleLayer(style: Style) {
        runCatching { style.removeLayer(windCircleLayerId()) }
    }

    private fun removeWindLayers(style: Style) {
        windRenderer.removeWindLayers(style)
    }

    private fun overlayPrefix(): String = "forecast-$idNamespace"

    private fun rasterSourceId(): String = "${overlayPrefix()}-raster-source"

    private fun rasterLayerId(): String = "${overlayPrefix()}-raster-layer"

    private fun vectorSourceId(): String = "${overlayPrefix()}-vector-source"

    private fun vectorFillLayerId(): String = "${overlayPrefix()}-vector-fill-layer"

    private fun windCircleLayerId(): String = "${overlayPrefix()}-wind-circle-layer"

    private fun windSymbolLayerId(): String = "${overlayPrefix()}-wind-symbol-layer"

    private fun windArrowIconId(): String = "${overlayPrefix()}-wind-arrow-icon"

    private fun windArrowIconPrefix(): String = "${overlayPrefix()}-wind-arrow-"

    private fun windBarbIconPrefix(): String = "${overlayPrefix()}-wind-barb-"

    private fun windBarbLayerPrefix(): String = "${overlayPrefix()}-wind-barb-layer-"

    private companion object {
        private const val DEFAULT_WIND_SPEED_PROPERTY = "spd"
        private const val SOURCE_LAYER_FALLBACK_MISS_THRESHOLD = 2

        val ANCHOR_LAYER_IDS = listOf(
            "airspace-layer",
            "racing-course-line",
            "racing-turnpoint-areas-fill",
            "racing-waypoints",
            "aat-task-line",
            "aat-areas-layer",
            "aat-waypoints",
            "adsb-traffic-icon-layer",
            "ogn-thermal-label-layer",
            "ogn-thermal-circle-layer",
            "ogn-traffic-icon-layer",
            BlueLocationOverlay.LAYER_ID
        )
    }
}
