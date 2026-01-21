// Role: Render the snail trail on the MapLibre map.
// Invariants: Layers stay below the aircraft icon and use per-segment styling.
package com.example.xcpro.map.trail

import android.content.Context
import android.util.Log
import kotlin.math.min
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
    private val mapView: MapView?
) {
    companion object {
        private const val TAG = "SnailTrailOverlay"
        private const val TAG_RENDER = "SnailTrailRender"
        private const val MAX_SEGMENT_LOGS = 400
        private const val REPLAY_DISTANCE_FACTOR = 1.0
        private const val REPLAY_MAX_DISTANCE_METERS = 30.0
        private const val MIN_CURRENT_SEGMENT_METERS = 0.5
        private const val ICON_CLEARANCE_FRACTION = 0f
        private const val TAIL_OFFSET_FRACTION = 0.12f
    }

    private var isInitialized = false
    private var currentType: TrailType? = null
    private var renderSequence: Long = 0
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG
    private var tailCache: TailCache? = null

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
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId start points=${points.size} length=${settings.length} type=${settings.type} " +
                    "scaling=${settings.scalingEnabled} drift=${settings.windDriftEnabled} " +
                    "loc=${currentLocation?.latitude},${currentLocation?.longitude} " +
                    "time=$currentTimeMillis zoom=$currentZoom replay=$isReplay wind=${"%.2f".format(windSpeedMs)} " +
                    "windDir=${"%.1f".format(windDirectionFromDeg)} circling=$isCircling frame=${frameId ?: -1}"
            )
        }

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

        val minTime = SnailTrailMath.minTimeMillis(settings.length, currentTimeMillis)
        val renderPoints = SnailTrailMath.filterPoints(
            points,
            minTime,
            currentTimeMillis,
            isCircling,
            settings
        )

        if (renderPoints.isEmpty()) {
            clear()
            return
        }

        val metersPerPixel = SnailTrailMath.metersPerPixel(
            map = map,
            mapView = mapView,
            latitude = currentLocation.latitude,
            zoom = currentZoom
        )
        val distanceFactor = if (isReplay) REPLAY_DISTANCE_FACTOR else 3.0
        val rawMinDistance = metersPerPixel * distanceFactor
        val minDistanceMeters = if (isReplay) {
            min(rawMinDistance, REPLAY_MAX_DISTANCE_METERS)
        } else {
            rawMinDistance
        }
        val sim2FullTrail = isReplay && MapFeatureFlags.useRenderFrameSync
        val filtered = SnailTrailMath.applyDistanceFilter(
            renderPoints,
            if (sim2FullTrail) 0.0 else minDistanceMeters
        )
        val latestPoint = renderPoints.lastOrNull()
        val filteredWithTail = if (
            latestPoint != null &&
            filtered.isNotEmpty() &&
            filtered.last().timestampMillis < latestPoint.timestampMillis
        ) {
            val extended = ArrayList<RenderPoint>(filtered.size + 1)
            extended.addAll(filtered)
            extended.add(latestPoint)
            extended
        } else {
            filtered
        }
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId filtered points=${filteredWithTail.size} minDistanceMeters=${"%.2f".format(minDistanceMeters)} " +
                    "metersPerPixel=${"%.6f".format(metersPerPixel)} minTime=${minTime ?: -1}"
            )
        }
        if (filteredWithTail.isEmpty()) {
            clear()
            return
        }

        val (valueMin, valueMax) = if (settings.type == TrailType.ALTITUDE) {
            SnailTrailMath.altitudeRange(filteredWithTail)
        } else {
            SnailTrailMath.varioRange(filteredWithTail)
        }

        val density = context.resources.displayMetrics.density
        val minWidth = SnailTrailMath.minWidth(density)
        val scaledWidths = SnailTrailMath.buildWidths(settings.scalingEnabled, density)
        val useScaledLines = settings.scalingEnabled && metersPerPixel <= 6000.0
        tailCache = TailCache(
            type = settings.type,
            valueMin = valueMin,
            valueMax = valueMax,
            useScaledLines = useScaledLines,
            scaledWidths = scaledWidths,
            minWidth = minWidth
        )
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId range min=${"%.2f".format(valueMin)} max=${"%.2f".format(valueMax)} " +
                    "useScaledLines=$useScaledLines minWidth=${"%.2f".format(minWidth)} density=$density"
            )
        }

        val lineFeatures = ArrayList<Feature>(filteredWithTail.size)
        val dotFeatures = ArrayList<Feature>(filteredWithTail.size)

        val bounds = ScreenBounds(mapView)
        val skipBoundsCheck = sim2FullTrail
        var last: RenderPoint? = null
        var lastInside = false
        var segmentLogCount = 0

        for (point in filteredWithTail) {
            val inside = if (skipBoundsCheck) true else bounds.isInside(map, point.latitude, point.longitude)
            if (last != null && (skipBoundsCheck || (lastInside && inside))) {
                val colorIndex = if (settings.type == TrailType.ALTITUDE) {
                    SnailTrailMath.altitudeColorIndex(point.altitudeMeters, valueMin, valueMax)
                } else {
                    SnailTrailMath.varioColorIndex(point.varioMs, valueMin, valueMax)
                }
                val width = if (useScaledLines) scaledWidths[colorIndex] else minWidth
                val dotRadius = scaledWidths[colorIndex]
                val colorInt = if (verboseLogging) SnailTrailPalette.colorFor(settings.type, colorIndex) else 0

                when (settings.type) {
                    TrailType.ALTITUDE -> {
                        lineFeatures.add(SnailTrailFeatureBuilder.lineFeature(last, point, colorIndex, width))
                        logSegment(
                            renderId,
                            segmentLogCount++,
                            "line",
                            last,
                            point,
                            colorIndex,
                            colorInt,
                            width,
                            null
                        )
                    }
                    TrailType.VARIO_1,
                    TrailType.VARIO_2 -> {
                        lineFeatures.add(SnailTrailFeatureBuilder.lineFeature(last, point, colorIndex, width))
                        logSegment(
                            renderId,
                            segmentLogCount++,
                            "line",
                            last,
                            point,
                            colorIndex,
                            colorInt,
                            width,
                            null
                        )
                    }
                    TrailType.VARIO_1_DOTS,
                    TrailType.VARIO_2_DOTS -> {
                        if (point.varioMs < 0) {
                            dotFeatures.add(SnailTrailFeatureBuilder.dotFeature(last, point, colorIndex, dotRadius))
                            logSegment(
                                renderId,
                                segmentLogCount++,
                                "dot",
                                last,
                                point,
                                colorIndex,
                                colorInt,
                                null,
                                dotRadius
                            )
                        } else {
                            lineFeatures.add(SnailTrailFeatureBuilder.lineFeature(last, point, colorIndex, width))
                            logSegment(
                                renderId,
                                segmentLogCount++,
                                "line",
                                last,
                                point,
                                colorIndex,
                                colorInt,
                                width,
                                null
                            )
                        }
                    }
                    TrailType.VARIO_DOTS_AND_LINES,
                    TrailType.VARIO_EINK -> {
                        if (point.varioMs < 0) {
                            dotFeatures.add(SnailTrailFeatureBuilder.dotFeature(last, point, colorIndex, dotRadius))
                            logSegment(
                                renderId,
                                segmentLogCount++,
                                "dot",
                                last,
                                point,
                                colorIndex,
                                colorInt,
                                null,
                                dotRadius
                            )
                        } else {
                            dotFeatures.add(SnailTrailFeatureBuilder.dotFeature(last, point, colorIndex, dotRadius))
                            lineFeatures.add(SnailTrailFeatureBuilder.lineFeature(last, point, colorIndex, width))
                            logSegment(
                                renderId,
                                segmentLogCount++,
                                "dot+line",
                                last,
                                point,
                                colorIndex,
                                colorInt,
                                width,
                                dotRadius
                            )
                        }
                    }
                }
            }
            last = point
            lastInside = inside
        }

        renderTailInternal(
            lastPoint = filteredWithTail.lastOrNull(),
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            currentZoom = currentZoom,
            tailCache = tailCache,
            frameId = frameId,
            metersPerPixel = metersPerPixel,
            renderId = renderId
        )

        val lineSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID) ?: return
        lineSource.setGeoJson(FeatureCollection.fromFeatures(lineFeatures))
        dotSource.setGeoJson(FeatureCollection.fromFeatures(dotFeatures))
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId end lineFeatures=${lineFeatures.size} dotFeatures=${dotFeatures.size}"
            )
        }
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
        val style = map.style ?: return
        val tailSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID) ?: return
        if (settings.length == TrailLength.OFF || currentLocation == null || lastPoint == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }
        updatePaletteIfNeeded(settings.type)
        val cache = tailCache ?: return
        val minTime = SnailTrailMath.minTimeMillis(settings.length, currentTimeMillis)
        val renderPoints = SnailTrailMath.filterPoints(
            points = listOf(lastPoint),
            minTimeMillis = minTime,
            currentTimeMillis = currentTimeMillis,
            isCircling = isCircling,
            settings = settings
        )
        val renderPoint = renderPoints.firstOrNull()
        if (renderPoint == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
            return
        }
        val metersPerPixel = SnailTrailMath.metersPerPixel(
            map = map,
            mapView = mapView,
            latitude = currentLocation.latitude,
            zoom = currentZoom
        )
        val tailFeature = buildTailFeature(
            lastPoint = renderPoint,
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            cache = cache,
            metersPerPixel = metersPerPixel,
            frameId = frameId
        )
        if (tailFeature == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        } else {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(listOf(tailFeature)))
        }
    }

    private fun renderTailInternal(
        lastPoint: RenderPoint?,
        settings: TrailSettings,
        currentLocation: LatLng?,
        currentTimeMillis: Long,
        currentZoom: Float,
        tailCache: TailCache?,
        frameId: Long?,
        metersPerPixel: Double,
        renderId: Long?
    ) {
        if (lastPoint == null || currentLocation == null) return
        val cache = tailCache ?: return
        val tailFeature = buildTailFeature(
            lastPoint = lastPoint,
            settings = settings,
            currentLocation = currentLocation,
            currentTimeMillis = currentTimeMillis,
            cache = cache,
            metersPerPixel = metersPerPixel,
            frameId = frameId,
            renderId = renderId
        )
        val style = map.style ?: return
        val tailSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.TAIL_SOURCE_ID) ?: return
        if (tailFeature == null) {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        } else {
            tailSource.setGeoJson(FeatureCollection.fromFeatures(listOf(tailFeature)))
        }
    }

    private fun buildTailFeature(
        lastPoint: RenderPoint,
        settings: TrailSettings,
        currentLocation: LatLng,
        currentTimeMillis: Long,
        cache: TailCache,
        metersPerPixel: Double,
        frameId: Long?,
        renderId: Long? = null
    ): Feature? {
        val trackBearing = TrailGeo.bearingDegrees(
            lastPoint.latitude,
            lastPoint.longitude,
            currentLocation.latitude,
            currentLocation.longitude
        )
        val tailOffsetMeters = if (metersPerPixel.isFinite() && metersPerPixel > 0.0) {
            com.example.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX *
                TAIL_OFFSET_FRACTION * metersPerPixel
        } else {
            0.0
        }
        val tailLocation = if (trackBearing.isFinite() && tailOffsetMeters > 0.0) {
            val tailBearing = (trackBearing + 180.0) % 360.0
            val (lat, lon) = TrailGeo.destinationPoint(
                currentLocation.latitude,
                currentLocation.longitude,
                tailBearing,
                tailOffsetMeters
            )
            if (TrailGeo.isValidCoordinate(lat, lon)) {
                LatLng(lat, lon)
            } else {
                currentLocation
            }
        } else {
            currentLocation
        }
        val distToAnchor = TrailGeo.distanceMeters(
            lastPoint.latitude,
            lastPoint.longitude,
            tailLocation.latitude,
            tailLocation.longitude
        )
        if (distToAnchor < MIN_CURRENT_SEGMENT_METERS) {
            return null
        }
        val clearancePx = com.example.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX * ICON_CLEARANCE_FRACTION
        val clipped = SnailTrailMath.clipLineToIconClearance(map, lastPoint, tailLocation, clearancePx)
            ?: return null
        val colorIndex = if (settings.type == TrailType.ALTITUDE) {
            SnailTrailMath.altitudeColorIndex(lastPoint.altitudeMeters, cache.valueMin, cache.valueMax)
        } else {
            SnailTrailMath.varioColorIndex(lastPoint.varioMs, cache.valueMin, cache.valueMax)
        }
        val width = if (cache.useScaledLines) cache.scaledWidths[colorIndex] else cache.minWidth
        val colorInt = if (verboseLogging) SnailTrailPalette.colorFor(settings.type, colorIndex) else 0
        val currentPoint = RenderPoint(
            latitude = clipped.latitude,
            longitude = clipped.longitude,
            altitudeMeters = lastPoint.altitudeMeters,
            varioMs = lastPoint.varioMs,
            timestampMillis = currentTimeMillis
        )
        val feature = SnailTrailFeatureBuilder.lineFeature(
            lastPoint,
            currentPoint,
            colorIndex,
            width
        )
        val logId = frameId ?: renderId ?: renderSequence
        logSegment(
            logId,
            0,
            "line-to-current frame=${frameId ?: -1}",
            lastPoint,
            currentPoint,
            colorIndex,
            colorInt,
            width,
            null
        )
        return feature
    }

    private data class TailCache(
        val type: TrailType,
        val valueMin: Double,
        val valueMax: Double,
        val useScaledLines: Boolean,
        val scaledWidths: FloatArray,
        val minWidth: Float
    )

    private fun logSegment(
        renderId: Long,
        index: Int,
        kind: String,
        start: RenderPoint,
        end: RenderPoint,
        colorIndex: Int,
        colorInt: Int,
        width: Float?,
        radius: Float?
    ) {
        if (!verboseLogging) return
        if (index >= MAX_SEGMENT_LOGS) {
            if (index == MAX_SEGMENT_LOGS) {
                Log.d(TAG_RENDER, "render#$renderId segment logs truncated at $MAX_SEGMENT_LOGS")
            }
            return
        }
        val widthLabel = width?.let { "width=${"%.2f".format(it)}" } ?: "width=-"
        val radiusLabel = radius?.let { "radius=${"%.2f".format(it)}" } ?: "radius=-"
        Log.d(
            TAG_RENDER,
            "render#$renderId seg=$index kind=$kind idx=$colorIndex color=${SnailTrailPalette.colorHex(colorInt)} " +
                "$widthLabel $radiusLabel " +
                "from=${"%.5f".format(start.latitude)},${"%.5f".format(start.longitude)} " +
                "to=${"%.5f".format(end.latitude)},${"%.5f".format(end.longitude)} " +
                "vario=${"%.2f".format(end.varioMs)} alt=${"%.1f".format(end.altitudeMeters)}"
        )
    }
}
