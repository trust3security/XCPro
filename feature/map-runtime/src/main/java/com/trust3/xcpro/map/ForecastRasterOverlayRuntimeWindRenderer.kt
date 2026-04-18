package com.trust3.xcpro.map

import com.trust3.xcpro.forecast.ForecastLegendSpec
import com.trust3.xcpro.forecast.ForecastTileSpec
import com.trust3.xcpro.forecast.ForecastWindDisplayMode
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconKeepUpright
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer

internal class ForecastRasterOverlayRuntimeWindRenderer(
    private val resolveSourceLayerForRender: (ForecastTileSpec) -> String?,
    private val removeWindCircleLayer: (Style) -> Unit,
    private val addLayerBelowAnchor: (Style, Layer) -> Unit,
    private val vectorSourceId: () -> String,
    private val windSymbolLayerId: () -> String,
    private val windArrowIconId: () -> String,
    private val windArrowIconIdForColor: (Int) -> String,
    private val windBarbIconId: (Int) -> String,
    private val windBarbLayerId: (Int) -> String
) {
    fun ensureWindLayers(
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

    fun removeWindLayers(style: Style) {
        removeWindSymbolLayer(style)
        removeWindCircleLayer(style)
    }

    private fun ensureWindArrowImage(style: Style) {
        val image = runCatching { style.getImage(windArrowIconId()) }.getOrNull()
        if (image == null) {
            style.addImage(windArrowIconId(), createForecastWindArrowBitmap())
        }
    }

    private fun ensureWindBarbImages(style: Style) {
        WIND_BARB_BUCKETS_KT.forEach { bucketKt ->
            val id = windBarbIconId(bucketKt)
            val existing = runCatching { style.getImage(id) }.getOrNull()
            if (existing == null) {
                style.addImage(id, createForecastWindBarbBitmap(bucketKt))
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
                style.addImage(stop.iconId, createForecastWindArrowBitmap(stop.argb))
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

    private data class WindBarbBucketRange(
        val minKt: Int,
        val maxExclusiveKt: Int?
    )

    private data class WindArrowColorStop(
        val value: Double,
        val argb: Int,
        val iconId: String
    )

    private companion object {
        private const val DEFAULT_WIND_SPEED_PROPERTY = "spd"
        private const val DEFAULT_WIND_DIRECTION_PROPERTY = "dir"
        private const val ARROW_ICON_SIZE_FACTOR = 0.4f
        private const val ARROW_DIRECTION_OFFSET_DEG = 180.0
        private const val BARB_ICON_SIZE_BASE = 1.05f
        private const val BARB_DIRECTION_OFFSET_DEG = 0.0
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
    }
}
