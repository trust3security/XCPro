package com.example.xcpro.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource

class ForecastRasterOverlay(
    private val map: MapLibreMap,
    private val idNamespace: String = "forecast"
) {
    private var lastTileSpec: ForecastTileSpec? = null
    private var lastLegendSpec: ForecastLegendSpec? = null
    private var activeSourceLayerCandidates: List<String> = emptyList()
    private var activeSourceLayerIndex: Int = 0
    private var sourceLayerConsecutiveMisses: Int = 0
    private var runtimeWarningMessage: String? = null

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

    private fun ensureWindArrowImage(style: Style) {
        val image = runCatching { style.getImage(windArrowIconId()) }.getOrNull()
        if (image == null) {
            style.addImage(windArrowIconId(), createWindArrowBitmap())
        }
    }

    private fun ensureWindBarbImages(style: Style) {
        WIND_BARB_BUCKETS_KT.forEach { bucketKt ->
            val id = windBarbIconId(bucketKt)
            val existing = runCatching { style.getImage(id) }.getOrNull()
            if (existing == null) {
                style.addImage(id, createWindBarbBitmap(bucketKt))
            }
        }
    }

    private fun ensureWindLayers(
        style: Style,
        tileSpec: ForecastTileSpec,
        opacity: Float,
        windOverlayScale: Float,
        windDisplayMode: ForecastWindDisplayMode,
        legendSpec: ForecastLegendSpec?
    ) {
        val sourceLayer = resolveSourceLayerForRender(tileSpec) ?: return
        val speedProperty = tileSpec.speedProperty ?: DEFAULT_WIND_SPEED_PROPERTY
        val directionProperty = tileSpec.directionProperty ?: DEFAULT_WIND_DIRECTION_PROPERTY

        when (windDisplayMode) {
            ForecastWindDisplayMode.ARROW -> {
                removeWindCircleLayer(style)
                ensureWindArrowImage(style)
                removeWindBarbLayers(style)
                ensureWindArrowLayer(
                    style = style,
                    sourceLayer = sourceLayer,
                    speedProperty = speedProperty,
                    directionProperty = directionProperty,
                    opacity = opacity,
                    windOverlayScale = windOverlayScale,
                    legendSpec = legendSpec
                )
            }
            ForecastWindDisplayMode.BARB -> {
                removeWindCircleLayer(style)
                runCatching { style.removeLayer(windSymbolLayerId()) }
                ensureWindBarbImages(style)
                ensureWindBarbLayers(
                    style = style,
                    sourceLayer = sourceLayer,
                    speedProperty = speedProperty,
                    directionProperty = directionProperty,
                    opacity = opacity,
                    windOverlayScale = windOverlayScale
                )
            }
        }
    }

    private fun ensureWindArrowLayer(
        style: Style,
        sourceLayer: String,
        speedProperty: String,
        directionProperty: String,
        opacity: Float,
        windOverlayScale: Float,
        legendSpec: ForecastLegendSpec?
    ) {
        val iconImageExpression = buildWindArrowIconImageExpression(
            style = style,
            speedProperty = speedProperty,
            legendSpec = legendSpec
        )
        val existingLayer = style.getLayer(windSymbolLayerId()) as? SymbolLayer
        if (existingLayer != null) {
            existingLayer.setSourceLayer(sourceLayer)
            existingLayer.setProperties(
                iconImage(iconImageExpression),
                iconSize(
                    buildWindArrowIconSizeExpression(
                        speedProperty = speedProperty,
                        windOverlayScale = windOverlayScale
                    )
                ),
                iconRotate(
                    buildWindDirectionExpression(
                        directionProperty = directionProperty,
                        directionOffsetDeg = ARROW_DIRECTION_OFFSET_DEG
                    )
                ),
                iconRotationAlignment("map"),
                iconKeepUpright(false),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor("center"),
                iconOpacity(opacity)
            )
            // Keep wind arrows above any freshly recreated primary forecast layers.
            runCatching { style.removeLayer(windSymbolLayerId()) }
            addLayerBelowAnchor(style, existingLayer)
            return
        }

        val newLayer = SymbolLayer(windSymbolLayerId(), vectorSourceId()).apply {
            setSourceLayer(sourceLayer)
        }.withProperties(
            iconImage(iconImageExpression),
            iconSize(
                buildWindArrowIconSizeExpression(
                    speedProperty = speedProperty,
                    windOverlayScale = windOverlayScale
                )
            ),
            iconRotate(
                buildWindDirectionExpression(
                    directionProperty = directionProperty,
                    directionOffsetDeg = ARROW_DIRECTION_OFFSET_DEG
                )
            ),
            iconRotationAlignment("map"),
            iconKeepUpright(false),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor("center"),
            iconOpacity(opacity)
        )
        addLayerBelowAnchor(style, newLayer)
    }

    private fun buildWindArrowIconImageExpression(
        style: Style,
        speedProperty: String,
        legendSpec: ForecastLegendSpec?
    ): Expression {
        val colorStops = resolveWindArrowColorStops(legendSpec)
        if (colorStops.isEmpty()) {
            ensureWindArrowImage(style)
            return Expression.literal(windArrowIconId())
        }

        colorStops.forEach { stop ->
            val existing = runCatching { style.getImage(stop.iconId) }.getOrNull()
            if (existing == null) {
                style.addImage(stop.iconId, createWindArrowBitmap(stop.argb))
            }
        }

        val speedExpression = Expression.coalesce(
            Expression.toNumber(Expression.get(speedProperty)),
            Expression.literal(colorStops.first().value - 1.0)
        )
        if (colorStops.size == 1) {
            return Expression.literal(colorStops.first().iconId)
        }

        val stepStops = ArrayList<Expression>((colorStops.size - 1) * 2)
        for (index in 1 until colorStops.size) {
            val stop = colorStops[index]
            stepStops.add(Expression.literal(stop.value))
            stepStops.add(Expression.literal(stop.iconId))
        }

        return Expression.step(
            speedExpression,
            Expression.literal(colorStops.first().iconId),
            *stepStops.toTypedArray()
        )
    }

    private fun resolveWindArrowColorStops(legendSpec: ForecastLegendSpec?): List<WindArrowColorStop> {
        if (legendSpec == null) return emptyList()
        return legendSpec.stops
            .asSequence()
            .map { stop ->
                WindArrowColorStop(
                    value = stop.value,
                    argb = stop.argb,
                    iconId = windArrowIconIdForColor(stop.argb)
                )
            }
            .filter { stop -> stop.value.isFinite() }
            .sortedBy { stop -> stop.value }
            .distinctBy { stop -> stop.value }
            .toList()
    }

    private fun ensureWindBarbLayers(
        style: Style,
        sourceLayer: String,
        speedProperty: String,
        directionProperty: String,
        opacity: Float,
        windOverlayScale: Float
    ) {
        WIND_BARB_BUCKET_RANGES.forEach { range ->
            ensureWindBarbLayer(
                style = style,
                sourceLayer = sourceLayer,
                speedProperty = speedProperty,
                directionProperty = directionProperty,
                opacity = opacity,
                windOverlayScale = windOverlayScale,
                minKt = range.minKt,
                maxExclusiveKt = range.maxExclusiveKt
            )
        }
    }

    private fun ensureWindBarbLayer(
        style: Style,
        sourceLayer: String,
        speedProperty: String,
        directionProperty: String,
        opacity: Float,
        windOverlayScale: Float,
        minKt: Int,
        maxExclusiveKt: Int?
    ) {
        val layerId = windBarbLayerId(minKt)
        val filterExpression = buildWindBarbFilterExpression(
            speedProperty = speedProperty,
            minKt = minKt,
            maxExclusiveKt = maxExclusiveKt
        )
        val existingLayer = style.getLayer(layerId) as? SymbolLayer
        if (existingLayer != null) {
            existingLayer.setSourceLayer(sourceLayer)
            existingLayer.setFilter(filterExpression)
            existingLayer.setProperties(
                iconImage(windBarbIconId(minKt)),
                iconSize(BARB_ICON_SIZE_BASE * windOverlayScale),
                iconRotate(
                    buildWindDirectionExpression(
                        directionProperty = directionProperty,
                        directionOffsetDeg = BARB_DIRECTION_OFFSET_DEG
                    )
                ),
                iconRotationAlignment("map"),
                iconKeepUpright(false),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconAnchor("center"),
                iconOpacity(opacity)
            )
            // Keep wind barbs above any freshly recreated primary forecast layers.
            runCatching { style.removeLayer(layerId) }
            addLayerBelowAnchor(style, existingLayer)
            return
        }

        val newLayer = SymbolLayer(layerId, vectorSourceId()).apply {
            setSourceLayer(sourceLayer)
            setFilter(filterExpression)
        }.withProperties(
            iconImage(windBarbIconId(minKt)),
            iconSize(BARB_ICON_SIZE_BASE * windOverlayScale),
            iconRotate(
                buildWindDirectionExpression(
                    directionProperty = directionProperty,
                    directionOffsetDeg = BARB_DIRECTION_OFFSET_DEG
                )
            ),
            iconRotationAlignment("map"),
            iconKeepUpright(false),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            iconAnchor("center"),
            iconOpacity(opacity)
        )
        addLayerBelowAnchor(style, newLayer)
    }

    private fun buildWindDirectionExpression(
        directionProperty: String,
        directionOffsetDeg: Double = 0.0
    ): Expression {
        val baseDirection = Expression.coalesce(
            Expression.toNumber(Expression.get(directionProperty)),
            Expression.literal(0.0)
        )
        if (directionOffsetDeg == 0.0) return baseDirection
        return Expression.mod(
            Expression.sum(baseDirection, Expression.literal(directionOffsetDeg)),
            Expression.literal(360.0)
        )
    }

    private fun buildWindBarbFilterExpression(
        speedProperty: String,
        minKt: Int,
        maxExclusiveKt: Int?
    ): Expression {
        val speedExpression = Expression.coalesce(
            Expression.toNumber(Expression.get(speedProperty)),
            Expression.literal(-1.0)
        )
        return if (maxExclusiveKt == null) {
            Expression.gte(speedExpression, Expression.literal(minKt.toDouble()))
        } else {
            Expression.all(
                Expression.gte(speedExpression, Expression.literal(minKt.toDouble())),
                Expression.lt(speedExpression, Expression.literal(maxExclusiveKt.toDouble()))
            )
        }
    }

    private fun removeWindSymbolLayer(style: Style) {
        runCatching { style.removeLayer(windSymbolLayerId()) }
        removeWindBarbLayers(style)
    }

    private fun removeWindBarbLayers(style: Style) {
        WIND_BARB_BUCKET_RANGES.forEach { range ->
            runCatching { style.removeLayer(windBarbLayerId(range.minKt)) }
        }
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

    private fun buildWindArrowIconSizeExpression(
        speedProperty: String,
        windOverlayScale: Float
    ): Expression {
        return Expression.interpolate(
            Expression.linear(),
            Expression.toNumber(Expression.get(speedProperty)),
            Expression.literal(0.0),
            Expression.literal(0.95f * windOverlayScale * ARROW_ICON_SIZE_FACTOR),
            Expression.literal(15.0),
            Expression.literal(1.08f * windOverlayScale * ARROW_ICON_SIZE_FACTOR),
            Expression.literal(30.0),
            Expression.literal(1.22f * windOverlayScale * ARROW_ICON_SIZE_FACTOR),
            Expression.literal(50.0),
            Expression.literal(1.36f * windOverlayScale * ARROW_ICON_SIZE_FACTOR)
        )
    }

    private fun createWindArrowBitmap(
        colorArgb: Int = WIND_GLYPH_COLOR_BLACK
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIND_ARROW_ICON_BITMAP_SIZE_PX,
            WIND_ARROW_ICON_BITMAP_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WIND_GLYPH_OUTLINE_COLOR_BLACK
            style = Paint.Style.STROKE
            strokeWidth = WIND_ARROW_STROKE_WIDTH_PX
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val center = WIND_ARROW_ICON_BITMAP_SIZE_PX / 2f
        val tipY = WIND_ARROW_PADDING_PX
        val tailY = WIND_ARROW_ICON_BITMAP_SIZE_PX - WIND_ARROW_PADDING_PX
        val wingHalf = WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.24f
        val shaftHalf = WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.09f

        val path = Path().apply {
            moveTo(center, tipY)
            lineTo(center + wingHalf, center + WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
            lineTo(center + shaftHalf, center + WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
            lineTo(center + shaftHalf, tailY)
            lineTo(center - shaftHalf, tailY)
            lineTo(center - shaftHalf, center + WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
            lineTo(center - wingHalf, center + WIND_ARROW_ICON_BITMAP_SIZE_PX * 0.08f)
            close()
        }
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        return bitmap
    }

    private fun createWindBarbBitmap(speedKtBucket: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIND_BARB_ICON_BITMAP_SIZE_PX,
            WIND_BARB_ICON_BITMAP_SIZE_PX,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BARB_OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = WIND_BARB_STROKE_WIDTH_PX + 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BARB_FILL_COLOR
            style = Paint.Style.STROKE
            strokeWidth = WIND_BARB_STROKE_WIDTH_PX
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BARB_FILL_COLOR
            style = Paint.Style.FILL
        }
        val fillOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BARB_OUTLINE_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeJoin = Paint.Join.ROUND
        }

        val centerX = WIND_BARB_ICON_BITMAP_SIZE_PX / 2f
        val topY = WIND_BARB_PADDING_PX
        val bottomY = WIND_BARB_ICON_BITMAP_SIZE_PX - WIND_BARB_PADDING_PX
        canvas.drawLine(centerX, bottomY, centerX, topY, outlinePaint)
        canvas.drawLine(centerX, bottomY, centerX, topY, strokePaint)

        var remainingKt = speedKtBucket.coerceAtLeast(0)
        var markY = topY + WIND_BARB_MARK_SPACING_PX
        val maxFlagY = bottomY - WIND_BARB_MARK_SPACING_PX

        while (remainingKt >= 50 && markY <= maxFlagY) {
            val triangle = Path().apply {
                moveTo(centerX, markY)
                lineTo(centerX + WIND_BARB_MARK_LENGTH_PX, markY + WIND_BARB_MARK_SPACING_PX)
                lineTo(centerX, markY + WIND_BARB_MARK_SPACING_PX * 2f)
                close()
            }
            canvas.drawPath(triangle, fillPaint)
            canvas.drawPath(triangle, fillOutlinePaint)
            remainingKt -= 50
            markY += WIND_BARB_MARK_SPACING_PX * 2f
        }

        while (remainingKt >= 10 && markY <= maxFlagY) {
            canvas.drawLine(
                centerX,
                markY,
                centerX + WIND_BARB_MARK_LENGTH_PX,
                markY + WIND_BARB_MARK_SPACING_PX,
                outlinePaint
            )
            canvas.drawLine(
                centerX,
                markY,
                centerX + WIND_BARB_MARK_LENGTH_PX,
                markY + WIND_BARB_MARK_SPACING_PX,
                strokePaint
            )
            remainingKt -= 10
            markY += WIND_BARB_MARK_SPACING_PX
        }

        if (remainingKt >= 5 && markY <= maxFlagY) {
            canvas.drawLine(
                centerX,
                markY,
                centerX + WIND_BARB_MARK_LENGTH_PX * 0.55f,
                markY + WIND_BARB_MARK_SPACING_PX * 0.55f,
                outlinePaint
            )
            canvas.drawLine(
                centerX,
                markY,
                centerX + WIND_BARB_MARK_LENGTH_PX * 0.55f,
                markY + WIND_BARB_MARK_SPACING_PX * 0.55f,
                strokePaint
            )
        }

        return bitmap
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
        removeWindSymbolLayer(style)
        removeWindCircleLayer(style)
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
        private const val DEFAULT_WIND_DIRECTION_PROPERTY = "dir"
        private const val WIND_ARROW_ICON_BITMAP_SIZE_PX = 96
        private const val WIND_ARROW_PADDING_PX = 8f
        private const val WIND_ARROW_STROKE_WIDTH_PX = 3f
        private const val WIND_BARB_ICON_BITMAP_SIZE_PX = 96
        private const val WIND_BARB_PADDING_PX = 10f
        private const val WIND_BARB_STROKE_WIDTH_PX = 4f
        private const val WIND_BARB_MARK_SPACING_PX = 10f
        private const val WIND_BARB_MARK_LENGTH_PX = 28f
        private const val ARROW_ICON_SIZE_FACTOR = 0.4f
        // Arrow and barb must represent the same wind direction value.
        private const val ARROW_DIRECTION_OFFSET_DEG = 180.0
        private const val BARB_ICON_SIZE_BASE = 1.05f
        private const val BARB_DIRECTION_OFFSET_DEG = 0.0
        private const val SOURCE_LAYER_FALLBACK_MISS_THRESHOLD = 2
        private val WIND_GLYPH_COLOR_BLACK = 0xFF000000.toInt()
        private val WIND_GLYPH_OUTLINE_COLOR_BLACK = WIND_GLYPH_COLOR_BLACK
        private val BARB_FILL_COLOR = WIND_GLYPH_COLOR_BLACK
        private val BARB_OUTLINE_COLOR = WIND_GLYPH_COLOR_BLACK
        private val WIND_BARB_BUCKETS_KT = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 50, 60)
        private val WIND_BARB_BUCKET_RANGES = listOf(
            WindBarbBucketRange(0, 5),
            WindBarbBucketRange(5, 10),
            WindBarbBucketRange(10, 15),
            WindBarbBucketRange(15, 20),
            WindBarbBucketRange(20, 25),
            WindBarbBucketRange(25, 30),
            WindBarbBucketRange(30, 35),
            WindBarbBucketRange(35, 40),
            WindBarbBucketRange(40, 50),
            WindBarbBucketRange(50, 60),
            WindBarbBucketRange(60, null)
        )

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

        private data class WindBarbBucketRange(
            val minKt: Int,
            val maxExclusiveKt: Int?
        )

        private data class WindArrowColorStop(
            val value: Double,
            val argb: Int,
            val iconId: String
        )
    }
}

