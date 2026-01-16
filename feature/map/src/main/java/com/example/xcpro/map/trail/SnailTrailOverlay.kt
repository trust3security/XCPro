// Role: Render the snail trail on the MapLibre map.
// Invariants: Layers stay below the aircraft icon and use per-segment styling.
package com.example.xcpro.map.trail

import android.content.Context
import android.util.Log
import kotlin.math.max
import kotlin.math.min
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
        private const val ICON_CLEARANCE_FRACTION = 0.35f
    }

    private var isInitialized = false
    private var currentType: TrailType? = null
    private var renderSequence: Long = 0
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG

    fun initialize() {
        val style = map.style ?: return
        if (isInitialized) return

        try {
            style.addSource(GeoJsonSource(SnailTrailStyle.LINE_SOURCE_ID))
            style.addSource(GeoJsonSource(SnailTrailStyle.DOT_SOURCE_ID))

            val lineLayer = LineLayer(SnailTrailStyle.LINE_LAYER_ID, SnailTrailStyle.LINE_SOURCE_ID)
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
                style.addLayerAbove(dotLayer, SnailTrailStyle.LINE_LAYER_ID)
            } else {
                style.addLayer(lineLayer)
                style.addLayerAbove(dotLayer, SnailTrailStyle.LINE_LAYER_ID)
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
        val state = if (visible) "visible" else "none"
        lineLayer.setProperties(visibility(state))
        dotLayer.setProperties(visibility(state))
    }

    fun clear() {
        val style = map.style ?: return
        val lineSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(SnailTrailStyle.DOT_SOURCE_ID) ?: return
        lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        dotSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        val style = map.style ?: return
        if (!isInitialized) return
        try {
            style.removeLayer(SnailTrailStyle.DOT_LAYER_ID)
            style.removeLayer(SnailTrailStyle.LINE_LAYER_ID)
            style.removeSource(SnailTrailStyle.DOT_SOURCE_ID)
            style.removeSource(SnailTrailStyle.LINE_SOURCE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup snail trail overlay: ${e.message}", e)
        } finally {
            isInitialized = false
            currentType = null
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
        isReplay: Boolean
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
                    "windDir=${"%.1f".format(windDirectionFromDeg)} circling=$isCircling"
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
            currentLocation,
            currentTimeMillis,
            windSpeedMs,
            windDirectionFromDeg,
            isCircling,
            settings
        )

        if (renderPoints.isEmpty()) {
            clear()
            return
        }

        val metersPerPixel = SnailTrailMath.metersPerPixelAtLatitude(currentLocation.latitude, currentZoom)
        val distanceFactor = if (isReplay) REPLAY_DISTANCE_FACTOR else 3.0
        val rawMinDistance = metersPerPixel * distanceFactor
        val minDistanceMeters = if (isReplay) {
            min(rawMinDistance, REPLAY_MAX_DISTANCE_METERS)
        } else {
            rawMinDistance
        }
        val filtered = SnailTrailMath.applyDistanceFilter(renderPoints, minDistanceMeters)
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId filtered points=${filtered.size} minDistanceMeters=${"%.2f".format(minDistanceMeters)} " +
                    "metersPerPixel=${"%.6f".format(metersPerPixel)} minTime=${minTime ?: -1}"
            )
        }
        if (filtered.isEmpty()) {
            clear()
            return
        }

        val (valueMin, valueMax) = if (settings.type == TrailType.ALTITUDE) {
            SnailTrailMath.altitudeRange(filtered)
        } else {
            SnailTrailMath.varioRange(filtered)
        }

        val density = context.resources.displayMetrics.density
        val minWidth = SnailTrailMath.minWidth(density)
        val scaledWidths = SnailTrailMath.buildWidths(settings.scalingEnabled, density)
        val useScaledLines = settings.scalingEnabled && metersPerPixel <= 6000.0
        if (verboseLogging) {
            Log.d(
                TAG_RENDER,
                "render#$renderId range min=${"%.2f".format(valueMin)} max=${"%.2f".format(valueMax)} " +
                    "useScaledLines=$useScaledLines minWidth=${"%.2f".format(minWidth)} density=$density"
            )
        }

        val lineFeatures = ArrayList<Feature>(filtered.size)
        val dotFeatures = ArrayList<Feature>(filtered.size)

        val bounds = ScreenBounds(mapView)
        var last: RenderPoint? = null
        var lastInside = false
        var segmentLogCount = 0

        for (point in filtered) {
            val inside = bounds.isInside(map, point.latitude, point.longitude)
            if (last != null && lastInside && inside) {
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

        val lastPoint = filtered.lastOrNull()
        if (lastPoint != null && currentLocation != null) {
            val distToCurrent = TrailGeo.distanceMeters(
                lastPoint.latitude,
                lastPoint.longitude,
                currentLocation.latitude,
                currentLocation.longitude
            )
            if (distToCurrent >= MIN_CURRENT_SEGMENT_METERS) {
                val clearancePx = com.example.xcpro.map.BlueLocationOverlay.ICON_SIZE_PX * ICON_CLEARANCE_FRACTION
                val clipped = SnailTrailMath.clipLineToIconClearance(map, lastPoint, currentLocation, clearancePx)
                    ?: return
                val colorIndex = if (settings.type == TrailType.ALTITUDE) {
                    SnailTrailMath.altitudeColorIndex(lastPoint.altitudeMeters, valueMin, valueMax)
                } else {
                    SnailTrailMath.varioColorIndex(lastPoint.varioMs, valueMin, valueMax)
                }
                val width = if (useScaledLines) scaledWidths[colorIndex] else minWidth
                val colorInt = if (verboseLogging) SnailTrailPalette.colorFor(settings.type, colorIndex) else 0
                val currentPoint = RenderPoint(
                    latitude = clipped.latitude,
                    longitude = clipped.longitude,
                    altitudeMeters = lastPoint.altitudeMeters,
                    varioMs = lastPoint.varioMs,
                    timestampMillis = currentTimeMillis
                )
                lineFeatures.add(
                    SnailTrailFeatureBuilder.lineFeature(
                        lastPoint,
                        currentPoint,
                        colorIndex,
                        width
                    )
                )
                logSegment(
                    renderId,
                    segmentLogCount++,
                    "line-to-current",
                    lastPoint,
                    currentPoint,
                    colorIndex,
                    colorInt,
                    width,
                    null
                )
            }
        }

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
        val palette = SnailTrailPalette.colorExpression(type)
        lineLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.lineColor(palette))
        dotLayer.setProperties(org.maplibre.android.style.layers.PropertyFactory.circleColor(palette))
        currentType = type
    }

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
