// Role: Render the snail trail on the MapLibre map.
// Invariants: Layers stay below the aircraft icon and use per-segment styling.
package com.trust3.xcpro.map.trail

import android.content.Context
import com.trust3.xcpro.core.common.logging.AppLogger
import com.trust3.xcpro.map.config.MapFeatureFlags
import com.trust3.xcpro.map.runtime.BuildConfig as RuntimeBuildConfig
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.visibility
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
    private var renderSequence: Long = 0
    private val verboseLogging = RuntimeBuildConfig.DEBUG
    private val logger = SnailTrailLogger(verboseLogging)
    private val metersPerPixelProvider = MapLibreMetersPerPixelProvider(map, mapView)
    private val renderPlanner = SnailTrailRenderPlanner(metersPerPixelProvider)
    private val tailBuilder = SnailTrailTailBuilder(MapLibreTailClipper(map))
    private val tailRenderer = SnailTrailTailRenderer(
        map = map,
        logger = logger,
        tailBuilder = tailBuilder,
        metersPerPixelProvider = metersPerPixelProvider,
        iconSizePx = com.trust3.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX.toFloat(),
        renderSequenceProvider = { renderSequence }
    )
    private val displayTrailRenderer = SnailTrailDisplayTrailRenderer(map, mapView)
    private val displayConnectorRenderer = SnailTrailDisplayConnectorRenderer(
        map = map,
        tailBuilder = tailBuilder,
        metersPerPixelProvider = metersPerPixelProvider,
        iconSizePx = com.trust3.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX.toFloat()
    )
    private val paletteApplier = SnailTrailPaletteApplier(map)
    private val styleCacheResolver = SnailTrailStyleCacheResolver(context)
    private val layerLifecycle = SnailTrailLayerLifecycle(map)
    private var tailCache: SnailTrailStyleCache? = null
    private var displayTrailCache: SnailTrailStyleCache? = null

    fun initialize() {
        if (isInitialized) return

        try {
            if (layerLifecycle.install()) {
                isInitialized = true
                AppLogger.d(TAG, "Snail trail overlay initialized")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize snail trail overlay: ${e.message}", e)
        }
    }

    fun setVisible(visible: Boolean) {
        val style = map.style ?: return
        if (!isInitialized) return
        val lineLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(SnailTrailStyle.DOT_LAYER_ID) ?: return
        val tailLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.TAIL_LAYER_ID)
        val displayLineLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.DISPLAY_LINE_LAYER_ID)
        val displayConnectorLayer = style.getLayerAs<LineLayer>(SnailTrailStyle.DISPLAY_CONNECTOR_LAYER_ID)
        val displayDotLayer = style.getLayerAs<CircleLayer>(SnailTrailStyle.DISPLAY_DOT_LAYER_ID)
        val state = if (visible) "visible" else "none"
        lineLayer.setProperties(visibility(state))
        dotLayer.setProperties(visibility(state))
        tailLayer?.setProperties(visibility(state))
        displayLineLayer?.setProperties(visibility(state))
        displayConnectorLayer?.setProperties(visibility(state))
        displayDotLayer?.setProperties(visibility(state))
    }

    fun clear() {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        displayTrailRenderer.clear()
        displayConnectorRenderer.clear()
        displayTrailCache = null
    }

    fun clearRawTrail() {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun clearTail() {
        val style = map.style ?: return
        style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        if (!isInitialized) return
        try {
            layerLifecycle.remove()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to cleanup snail trail overlay: ${e.message}", e)
        } finally {
            isInitialized = false
            paletteApplier.reset()
            tailCache = null
            displayTrailCache = null
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
        isTurnSmoothing: Boolean,
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
        paletteApplier.updateIfNeeded(settings.type)

        val density = context.resources.displayMetrics.density
        val plan = renderPlanner.plan(
            SnailTrailRenderPlanner.Input(
                points = points,
                settings = settings,
                currentLocation = TrailGeoPoint(currentLocation.latitude, currentLocation.longitude),
                currentTimeMillis = currentTimeMillis,
                isCircling = isCircling,
                isTurnSmoothing = isTurnSmoothing,
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

    internal fun renderDisplayTrail(
        points: List<RenderPoint>,
        settings: TrailSettings
    ) {
        if (!isInitialized) return
        if (settings.length != TrailLength.OFF) {
            setVisible(true)
        }
        paletteApplier.updateIfNeeded(settings.type)
        displayTrailCache = tailCache ?: styleCacheResolver.resolve(settings, points)
        displayTrailRenderer.render(
            points = points,
            settings = settings,
            styleCache = displayTrailCache
        )
    }

    internal fun clearDisplayTrail() {
        if (!isInitialized) return
        displayTrailRenderer.clear()
        displayConnectorRenderer.clear()
        displayTrailCache = null
    }

    internal fun renderDisplayConnector(
        lastPoint: RenderPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        currentZoom: Float
    ) {
        if (!isInitialized) return
        if (settings.length != TrailLength.OFF) {
            setVisible(true)
        }
        paletteApplier.updateIfNeeded(settings.type)
        displayConnectorRenderer.render(
            lastPoint = lastPoint,
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            currentZoom = currentZoom,
            styleCache = displayTrailCache ?: lastPoint?.let {
                styleCacheResolver.resolve(settings, listOf(it))
            }
        )
    }

    internal fun clearDisplayConnector() {
        if (!isInitialized) return
        displayConnectorRenderer.clear()
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
        val resolvedTailCache = tailCache ?: lastPoint?.let { styleCacheResolver.resolve(settings, it) }
        tailRenderer.renderTail(
            lastPoint = lastPoint,
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            isCircling = isCircling,
            currentZoom = currentZoom,
            tailCache = resolvedTailCache,
            frameId = frameId,
            updatePalette = {
                paletteApplier.updateIfNeeded(settings.type)
            }
        )
    }
}
