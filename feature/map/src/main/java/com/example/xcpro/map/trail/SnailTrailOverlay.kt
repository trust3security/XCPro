// Role: Render the snail trail on the MapLibre map.
// Invariants: Layers stay below the aircraft icon and use per-segment styling.
package com.example.xcpro.map.trail

import android.content.Context
import android.util.Log
import com.example.xcpro.map.config.MapFeatureFlags
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection

class SnailTrailOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    private val mapView: MapView?,
    private val featureFlags: MapFeatureFlags
) {
    companion object {
        private const val TAG = "SnailTrailOverlay"
    }

    private var isInitialized = false
    private var currentType: TrailType? = null
    private var renderSequence: Long = 0
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG
    private val logger = SnailTrailLogger(verboseLogging)
    private val metersPerPixelProvider = MapLibreMetersPerPixelProvider(map, mapView)
    private val renderPlanner = SnailTrailRenderPlanner(metersPerPixelProvider)
    private val tailBuilder = SnailTrailTailBuilder(MapLibreTailClipper(map))
    private val tailRenderer = SnailTrailTailRenderer(
        map = map,
        logger = logger,
        tailBuilder = tailBuilder,
        metersPerPixelProvider = metersPerPixelProvider,
        iconSizePx = com.example.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX.toFloat(),
        renderSequenceProvider = { renderSequence }
    )
    private var tailCache: SnailTrailStyleCache? = null

    fun initialize() {
        val style = map.style ?: return
        if (isInitialized) return

        try {
            style.addSource(GeoJsonSource(SnailTrailStyle.LINE_SOURCE_ID))
            style.addSource(GeoJsonSource(SnailTrailStyle.DOT_SOURCE_ID))
            style.addSource(GeoJsonSource(SnailTrailStyle.TAIL_SOURCE_ID))

            val lineLayer = LineLayer(SnailTrailStyle.LINE_LAYER_ID, SnailTrailStyle.LINE_SOURCE_ID)
                .withProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(Expression.get(SnailTrailStyle.PROP_WIDTH)),
                    lineOpacity(1.0f)
                )

            val tailLayer = LineLayer(SnailTrailStyle.TAIL_LAYER_ID, SnailTrailStyle.TAIL_SOURCE_ID)
                .withProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(Expression.get(SnailTrailStyle.PROP_WIDTH)),
                    lineOpacity(1.0f)
                )

            val dotLayer = CircleLayer(SnailTrailStyle.DOT_LAYER_ID, SnailTrailStyle.DOT_SOURCE_ID)
                .withProperties(
                    circleRadius(Expression.get(SnailTrailStyle.PROP_RADIUS)),
                    circleOpacity(1.0f)
                )

            val anchorLayerId = com.example.xcpro.map.BlueLocationOverlay.LAYER_ID
            if (style.getLayer(anchorLayerId) != null) {
                style.addLayerBelow(lineLayer, anchorLayerId)
                style.addLayerAbove(tailLayer, SnailTrailStyle.LINE_LAYER_ID)
                style.addLayerAbove(dotLayer, SnailTrailStyle.TAIL_LAYER_ID)
            } else {
                style.addLayer(lineLayer)
                style.addLayerAbove(tailLayer, SnailTrailStyle.LINE_LAYER_ID)
                style.addLayerAbove(dotLayer, SnailTrailStyle.TAIL_LAYER_ID)
            }

            isInitialized = true
            Log.d(TAG, "Snail trail overlay initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize snail trail overlay: ${e.message}", e)
        }
    }

    fun setVisible(visible: Boolean) {
        val style = map.style ?: return
        if (!isInitialized) return
        val lineLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(SnailTrailStyle.DOT_LAYER_ID) ?: return
        val tailLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.TAIL_LAYER_ID)
        val state = if (visible) "visible" else "none"
        lineLayer.setProperties(visibility(state))
        dotLayer.setProperties(visibility(state))
        tailLayer?.setProperties(visibility(state))
    }

    fun clear() {
        val style = map.style ?: return
        val lineSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID) ?: return
        val tailSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID)
        lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        dotSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        tailSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        val style = map.style ?: return
        if (!isInitialized) return
        try {
            style.removeLayer(SnailTrailStyle.DOT_LAYER_ID)
            style.removeLayer(SnailTrailStyle.TAIL_LAYER_ID)
            style.removeLayer(SnailTrailStyle.LINE_LAYER_ID)
            style.removeSource(SnailTrailStyle.DOT_SOURCE_ID)
            style.removeSource(SnailTrailStyle.TAIL_SOURCE_ID)
            style.removeSource(SnailTrailStyle.LINE_SOURCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup snail trail overlay: ${e.message}", e)
        } finally {
            isInitialized = false
            currentType = null
            tailCache = null
        }
    }

    fun render(
        points: List<TrailPoint>,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        windSpeedMs: Double,
        windDirectionFromDeg: Double,
        isCircling: Boolean,
        currentZoom: Float,
        isReplay: Boolean,
        frameId: Long? = null
    ) {
        if (!isInitialized) return
        val style = map.style ?: return
        val renderId = renderSequence++
        logger.logRenderStart(
            renderId = renderId,
            pointsSize = points.size,
            settings = settings,
            locationLat = currentLocation?.latitude,
            locationLon = currentLocation?.longitude,
            currentTimeMillis = currentTimeMillis,
            currentZoom = currentZoom,
            isReplay = isReplay,
            windSpeedMs = windSpeedMs,
            windDirectionFromDeg = windDirectionFromDeg,
            isCircling = isCircling,
            frameId = frameId
        )

        if (settings.length == TrailLength.OFF) {
            setVisible(false)
            clear()
            return
        }

        if (currentLocation == null || !TrailGeo.isValidCoordinate(currentLocation.latitude, currentLocation.longitude)) {
            clear()
            return
        }

        setVisible(true)
        updatePaletteIfNeeded(settings.type)

        val density = context.resources.displayMetrics.density
        val plan = renderPlanner.plan(
            SnailTrailRenderPlanner.Input(
                points = points,
                settings = settings,
                currentLocation = TrailGeoPoint(currentLocation.latitude, currentLocation.longitude),
                currentTimeMillis = currentTimeMillis,
                isCircling = isCircling,
                currentZoom = currentZoom,
                isReplay = isReplay,
                useRenderFrameSync = featureFlags.useRenderFrameSync,
                density = density
            )
        )
        if (plan == null) {
            clear()
            return
        }

        val minTime = plan.minTimeMillis
        val filteredWithTail = plan.renderPoints
        val metersPerPixel = plan.metersPerPixel
        val minDistanceMeters = plan.minDistanceMeters
        val styleCache = plan.styleCache
        val valueMin = styleCache.valueMin
        val valueMax = styleCache.valueMax
        val useScaledLines = styleCache.useScaledLines
        val minWidth = styleCache.minWidth
        val skipBoundsCheck = plan.skipBoundsCheck
        tailCache = styleCache

        logger.logFiltered(
            renderId = renderId,
            filteredCount = filteredWithTail.size,
            minDistanceMeters = minDistanceMeters,
            metersPerPixel = metersPerPixel,
            minTime = minTime
        )
        logger.logRange(
            renderId = renderId,
            valueMin = valueMin,
            valueMax = valueMax,
            useScaledLines = useScaledLines,
            minWidth = minWidth,
            density = density
        )

        val bounds = ScreenBounds(mapView)
        val segmentBuilder = SnailTrailSegmentBuilder(
            object : SnailTrailSegmentBuilder.BoundsChecker {
                override fun isInside(point: RenderPoint): Boolean {
                    return bounds.isInside(map, point.latitude, point.longitude)
                }
            }
        )
        val segmentPlan = segmentBuilder.build(
            points = filteredWithTail,
            settings = settings,
            styleCache = styleCache,
            skipBoundsCheck = skipBoundsCheck,
            includeLogs = verboseLogging
        )

        val lineFeatures = ArrayList<Feature>(segmentPlan.lineSegments.size)
        for (segment in segmentPlan.lineSegments) {
            lineFeatures.add(
                SnailTrailFeatureBuilder.lineFeature(segment.start, segment.end, segment.colorIndex, segment.width)
            )
        }
        val dotFeatures = ArrayList<Feature>(segmentPlan.dotSegments.size)
        for (segment in segmentPlan.dotSegments) {
            dotFeatures.add(
                SnailTrailFeatureBuilder.dotFeature(segment.start, segment.end, segment.colorIndex, segment.radius)
            )
        }
        var segmentLogCount = 0
        for (entry in segmentPlan.logEntries) {
            logger.logSegment(
                renderId = renderId,
                index = segmentLogCount++,
                kind = entry.kind,
                start = entry.start,
                end = entry.end,
                colorIndex = entry.colorIndex,
                type = settings.type,
                width = entry.width,
                radius = entry.radius
            )
        }

        tailRenderer.renderTailInternal(
            lastPoint = filteredWithTail.lastOrNull(),
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            tailCache = tailCache,
            frameId = frameId,
            metersPerPixel = metersPerPixel,
            renderId = renderId
        )

        val lineSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID) ?: return
        lineSource.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        dotSource.setGeoJson(FeatureCollection.fromFeatures(dotFeatures))
        logger.logRenderEnd(renderId, lineFeatures.size, dotFeatures.size)
    }

    private fun updatePaletteIfNeeded(type: TrailType) {
        if (currentType == type) return
        val style = map.style ?: return
        val lineLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(SnailTrailStyle.DOT_LAYER_ID) ?: return
        val tailLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.TAIL_LAYER_ID)
        val palette = SnailTrailPalette.colorExpression(type)
        lineLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(palette))
        tailLayer?.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(palette))
        dotLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.circleColor(palette))
        currentType = type
    }

    fun renderTail(
        lastPoint: TrailPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        windSpeedMs: Double,
        windDirectionFromDeg: Double,
        isCircling: Boolean,
        currentZoom: Float,
        isReplay: Boolean,
        frameId: Long? = null
    ) {
        if (!isInitialized) return
        tailRenderer.renderTail(
            lastPoint = lastPoint,
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            isCircling = isCircling,
            currentZoom = currentZoom,
            tailCache = tailCache,
            frameId = frameId,
            updatePalette = {
                updatePaletteIfNeeded(settings.type)
            }
        )
    }

}
