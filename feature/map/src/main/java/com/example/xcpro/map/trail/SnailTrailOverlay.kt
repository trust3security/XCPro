// Role: Render the snail trail on the MapLibre map.
// Invariants: Layers stay below the aircraft icon and use per-segment styling.
package com.example.xcpro.map.trail

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class SnailTrailOverlay(
    private val context: Context,
    private val map: MapLibreMap,
    private val mapView: MapView?
) {
    companion object {
        private const val TAG = "SnailTrailOverlay"
        private const val TAG_RENDER = "SnailTrailRender"
        private const val LINE_SOURCE_ID = "snail-trail-line-source"
        private const val DOT_SOURCE_ID = "snail-trail-dot-source"
        private const val LINE_LAYER_ID = "snail-trail-line-layer"
        private const val DOT_LAYER_ID = "snail-trail-dot-layer"
        private const val PROP_COLOR_INDEX = "colorIndex"
        private const val PROP_WIDTH = "width"
        private const val PROP_RADIUS = "radius"
        private const val NUM_COLORS = 15
        private const val METERS_PER_PIXEL_EQUATOR = 156543.03392
        private const val MAX_SEGMENT_LOGS = 400
        private const val REPLAY_DISTANCE_FACTOR = 1.0
        private const val REPLAY_MAX_DISTANCE_METERS = 30.0
        private const val MIN_CURRENT_SEGMENT_METERS = 0.5
        private const val ICON_CLEARANCE_FRACTION = 0.35f
        private const val TRAIL_WIDTH_SCALE = 0.5f
        private const val MIN_WIDTH_FACTOR = 0.8f
        private const val MAX_WIDTH_FACTOR = 0.42f
    }

    private var isInitialized = false
    private var currentType: TrailType? = null
    private var renderSequence: Long = 0
    private val verboseLogging = com.example.xcpro.map.BuildConfig.DEBUG

    fun initialize() {
        val style = map.style ?: return
        if (isInitialized) return

        try {
            style.addSource(GeoJsonSource(LINE_SOURCE_ID))
            style.addSource(GeoJsonSource(DOT_SOURCE_ID))

            val lineLayer = LineLayer(LINE_LAYER_ID, LINE_SOURCE_ID)
                .withProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineWidth(Expression.get(PROP_WIDTH)),
                    lineOpacity(1.0f)
                )

            val dotLayer = CircleLayer(DOT_LAYER_ID, DOT_SOURCE_ID)
                .withProperties(
                    circleRadius(Expression.get(PROP_RADIUS)),
                    circleOpacity(1.0f)
                )

            val anchorLayerId = com.example.xcpro.map.BlueLocationOverlay.LAYER_ID
            if (style.getLayer(anchorLayerId) != null) {
                style.addLayerBelow(lineLayer, anchorLayerId)
                style.addLayerAbove(dotLayer, LINE_LAYER_ID)
            } else {
                style.addLayer(lineLayer)
                style.addLayerAbove(dotLayer, LINE_LAYER_ID)
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
        val lineLayer = style.getLayerAs<LineLayer>(LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(DOT_LAYER_ID) ?: return
        val state = if (visible) "visible" else "none"
        lineLayer.setProperties(visibility(state))
        dotLayer.setProperties(visibility(state))
    }

    fun clear() {
        val style = map.style ?: return
        val lineSource = style.getSourceAs<GeoJsonSource>(LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(DOT_SOURCE_ID) ?: return
        lineSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        dotSource.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }

    fun cleanup() {
        val style = map.style ?: return
        if (!isInitialized) return
        try {
            style.removeLayer(DOT_LAYER_ID)
            style.removeLayer(LINE_LAYER_ID)
            style.removeSource(DOT_SOURCE_ID)
            style.removeSource(LINE_SOURCE_ID)
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

        val minTime = minTimeMillis(settings.length, currentTimeMillis)
        val renderPoints = filterPoints(
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

        val metersPerPixel = metersPerPixelAtLatitude(currentLocation.latitude, currentZoom)
        val distanceFactor = if (isReplay) REPLAY_DISTANCE_FACTOR else 3.0
        val rawMinDistance = metersPerPixel * distanceFactor
        val minDistanceMeters = if (isReplay) {
            min(rawMinDistance, REPLAY_MAX_DISTANCE_METERS)
        } else {
            rawMinDistance
        }
        val filtered = applyDistanceFilter(renderPoints, minDistanceMeters)
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
            altitudeRange(filtered)
        } else {
            varioRange(filtered)
        }

        val density = context.resources.displayMetrics.density
        val minWidth = 2f * density * TRAIL_WIDTH_SCALE * MIN_WIDTH_FACTOR
        val scaledWidths = buildWidths(settings.scalingEnabled, density)
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
                    altitudeColorIndex(point.altitudeMeters, valueMin, valueMax)
                } else {
                    varioColorIndex(point.varioMs, valueMin, valueMax)
                }
                val width = if (useScaledLines) scaledWidths[colorIndex] else minWidth
                val dotRadius = scaledWidths[colorIndex]
                val colorInt = if (verboseLogging) colorFor(settings.type, colorIndex) else 0

                when (settings.type) {
                    TrailType.ALTITUDE -> {
                        lineFeatures.add(lineFeature(last, point, colorIndex, width))
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
                        lineFeatures.add(lineFeature(last, point, colorIndex, width))
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
                            dotFeatures.add(dotFeature(last, point, colorIndex, dotRadius))
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
                            lineFeatures.add(lineFeature(last, point, colorIndex, width))
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
                            dotFeatures.add(dotFeature(last, point, colorIndex, dotRadius))
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
                            dotFeatures.add(dotFeature(last, point, colorIndex, dotRadius))
                            lineFeatures.add(lineFeature(last, point, colorIndex, width))
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
                val clipped = clipLineToIconClearance(lastPoint, currentLocation, clearancePx)
                    ?: return
                val colorIndex = if (settings.type == TrailType.ALTITUDE) {
                    altitudeColorIndex(lastPoint.altitudeMeters, valueMin, valueMax)
                } else {
                    varioColorIndex(lastPoint.varioMs, valueMin, valueMax)
                }
                val width = if (useScaledLines) scaledWidths[colorIndex] else minWidth
                val colorInt = if (verboseLogging) colorFor(settings.type, colorIndex) else 0
                val currentPoint = RenderPoint(
                    latitude = clipped.latitude,
                    longitude = clipped.longitude,
                    altitudeMeters = lastPoint.altitudeMeters,
                    varioMs = lastPoint.varioMs,
                    timestampMillis = currentTimeMillis
                )
                lineFeatures.add(
                    lineFeature(
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

        val lineSource = style.getSourceAs<GeoJsonSource>(LINE_SOURCE_ID) ?: return
        val dotSource = style.getSourceAs<GeoJsonSource>(DOT_SOURCE_ID) ?: return
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
        val lineLayer = style.getLayerAs<LineLayer>(LINE_LAYER_ID) ?: return
        val dotLayer = style.getLayerAs<CircleLayer>(DOT_LAYER_ID) ?: return
        val palette = colorExpression(type)
        lineLayer.setProperties(lineColor(palette))
        dotLayer.setProperties(circleColor(palette))
        currentType = type
    }

    private fun minTimeMillis(length: TrailLength, nowMillis: Long): Long? = when (length) {
        TrailLength.FULL -> null
        TrailLength.LONG -> nowMillis - 60 * 60_000L
        TrailLength.MEDIUM -> nowMillis - 30 * 60_000L
        TrailLength.SHORT -> nowMillis - 10 * 60_000L
        TrailLength.OFF -> null
    }

    private fun filterPoints(
        points: List<TrailPoint>,
        minTimeMillis: Long?,
        currentLocation: LatLng,
        currentTimeMillis: Long,
        windSpeedMs: Double,
        windDirectionFromDeg: Double,
        isCircling: Boolean,
        settings: TrailSettings
    ): List<RenderPoint> {
        if (points.isEmpty()) return emptyList()
        val windValid = windSpeedMs > 0.5 && windDirectionFromDeg.isFinite()
        val applyDrift = settings.windDriftEnabled && isCircling && windValid
        val (driftLat, driftLon) = if (applyDrift) {
            val windToDeg = (windDirectionFromDeg + 180.0) % 360.0
            val (destLat, destLon) = TrailGeo.destinationPoint(
                currentLocation.latitude,
                currentLocation.longitude,
                windToDeg,
                windSpeedMs
            )
            (currentLocation.latitude - destLat) to (currentLocation.longitude - destLon)
        } else {
            0.0 to 0.0
        }

        return points
            .asSequence()
            .filter { minTimeMillis == null || it.timestampMillis >= minTimeMillis }
            .map { point ->
                val driftSeconds = if (applyDrift) {
                    max(0.0, (currentTimeMillis - point.timestampMillis) / 1000.0)
                } else {
                    0.0
                }
                val driftScale = driftSeconds * point.driftFactor
                RenderPoint(
                    latitude = point.latitude + driftLat * driftScale,
                    longitude = point.longitude + driftLon * driftScale,
                    altitudeMeters = point.altitudeMeters,
                    varioMs = point.varioMs,
                    timestampMillis = point.timestampMillis
                )
            }
            .toList()
    }

    private fun applyDistanceFilter(points: List<RenderPoint>, minDistanceMeters: Double): List<RenderPoint> {
        if (points.isEmpty() || minDistanceMeters <= 0.0) return points
        val filtered = ArrayList<RenderPoint>(points.size)
        var last: RenderPoint? = null
        for (point in points) {
            if (last == null) {
                filtered.add(point)
                last = point
                continue
            }
            val distance = TrailGeo.distanceMeters(
                last.latitude,
                last.longitude,
                point.latitude,
                point.longitude
            )
            if (distance >= minDistanceMeters) {
                filtered.add(point)
                last = point
            }
        }
        return filtered
    }

    private fun varioRange(points: List<RenderPoint>): Pair<Double, Double> {
        var minVal = -2.0
        var maxVal = 0.75
        for (point in points) {
            minVal = min(minVal, point.varioMs)
            maxVal = max(maxVal, point.varioMs)
        }
        maxVal = min(7.5, maxVal)
        minVal = max(-5.0, minVal)
        return minVal to maxVal
    }

    private fun altitudeRange(points: List<RenderPoint>): Pair<Double, Double> {
        var minVal = 500.0
        var maxVal = 1000.0
        for (point in points) {
            minVal = min(minVal, point.altitudeMeters)
            maxVal = max(maxVal, point.altitudeMeters)
        }
        if (maxVal == minVal) {
            maxVal += 1.0
        }
        return minVal to maxVal
    }

    private fun varioColorIndex(value: Double, minVal: Double, maxVal: Double): Int {
        val cv = if (value < 0) -value / minVal else value / maxVal
        val idx = (((cv + 1.0) / 2.0) * NUM_COLORS).toInt()
        return idx.coerceIn(0, NUM_COLORS - 1)
    }

    private fun altitudeColorIndex(value: Double, minVal: Double, maxVal: Double): Int {
        val relative = (value - minVal) / (maxVal - minVal)
        val idx = (relative * (NUM_COLORS - 1)).toInt()
        return idx.coerceIn(0, NUM_COLORS - 1)
    }

    private fun buildWidths(scalingEnabled: Boolean, density: Float): FloatArray {
        val minWidth = 2f * density * TRAIL_WIDTH_SCALE
        val scaleWidth = 16f * density * TRAIL_WIDTH_SCALE
        val maxWidth = if (scalingEnabled) {
            val peakIndex = (NUM_COLORS - 1) - NUM_COLORS / 2f
            max(minWidth, peakIndex * scaleWidth / NUM_COLORS)
        } else {
            minWidth
        }
        val widthSpan = maxWidth - minWidth
        val widths = FloatArray(NUM_COLORS)
        for (i in 0 until NUM_COLORS) {
            val rawWidth = if (i < NUM_COLORS / 2 || !scalingEnabled) {
                minWidth
            } else {
                max(minWidth, (i - NUM_COLORS / 2f) * scaleWidth / NUM_COLORS)
            }
            val t = if (widthSpan > 0f) {
                ((rawWidth - minWidth) / widthSpan).coerceIn(0f, 1f)
            } else {
                0f
            }
            val factor = MIN_WIDTH_FACTOR + (MAX_WIDTH_FACTOR - MIN_WIDTH_FACTOR) * t
            widths[i] = rawWidth * factor
        }
        return widths
    }

    private fun lineFeature(start: RenderPoint, end: RenderPoint, colorIndex: Int, width: Float): Feature {
        val line = LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.longitude, start.latitude),
                Point.fromLngLat(end.longitude, end.latitude)
            )
        )
        return Feature.fromGeometry(line).apply {
            addNumberProperty(PROP_COLOR_INDEX, colorIndex)
            addNumberProperty(PROP_WIDTH, width.toDouble())
        }
    }

    private fun dotFeature(start: RenderPoint, end: RenderPoint, colorIndex: Int, radius: Float): Feature {
        val midLat = (start.latitude + end.latitude) / 2.0
        val midLon = (start.longitude + end.longitude) / 2.0
        val point = Point.fromLngLat(midLon, midLat)
        return Feature.fromGeometry(point).apply {
            addNumberProperty(PROP_COLOR_INDEX, colorIndex)
            addNumberProperty(PROP_RADIUS, radius.toDouble())
        }
    }

    private fun colorExpression(type: TrailType): Expression {
        val stops = ArrayList<Expression.Stop>(NUM_COLORS)
        for (i in 0 until NUM_COLORS) {
            val color = colorFor(type, i)
            stops.add(Expression.stop(i, Expression.color(color)))
        }
        return Expression.match(
            Expression.get(PROP_COLOR_INDEX),
            Expression.color(Color.GRAY),
            *stops.toTypedArray()
        )
    }

    private fun colorFor(type: TrailType, index: Int): Int {
        val rampValue = index * 200 / (NUM_COLORS - 1)
        return when (type) {
            TrailType.ALTITUDE -> colorRampLookup(rampValue, altitudeRamp)
            TrailType.VARIO_2, TrailType.VARIO_2_DOTS, TrailType.VARIO_DOTS_AND_LINES -> {
                colorRampLookup(rampValue, vario2Ramp)
            }
            TrailType.VARIO_EINK -> colorRampLookup(rampValue, varioEinkRamp)
            else -> colorRampLookup(rampValue, vario1Ramp)
        }
    }

    private fun colorRampLookup(value: Int, ramp: List<ColorRamp>): Int {
        if (ramp.isEmpty()) return Color.GRAY
        val clamped = value.coerceIn(ramp.first().position, ramp.last().position)
        var lower = ramp.first()
        var upper = ramp.last()
        for (i in 1 until ramp.size) {
            if (clamped <= ramp[i].position) {
                lower = ramp[i - 1]
                upper = ramp[i]
                break
            }
        }
        val range = max(1, upper.position - lower.position)
        val t = (clamped - lower.position).toDouble() / range.toDouble()
        val r = (lower.r + (upper.r - lower.r) * t).toInt()
        val g = (lower.g + (upper.g - lower.g) * t).toInt()
        val b = (lower.b + (upper.b - lower.b) * t).toInt()
        return Color.rgb(r, g, b)
    }

    private fun metersPerPixelAtLatitude(latitude: Double, zoom: Float): Double {
        val latRad = Math.toRadians(latitude)
        return METERS_PER_PIXEL_EQUATOR * cos(latRad) / 2.0.pow(zoom.toDouble())
    }

    private fun clipLineToIconClearance(
        start: RenderPoint,
        end: LatLng,
        clearancePx: Float
    ): LatLng? {
        val projection = map.projection ?: return end
        val startPx = projection.toScreenLocation(LatLng(start.latitude, start.longitude)) ?: return end
        val endPx = projection.toScreenLocation(end) ?: return end
        val dx = endPx.x - startPx.x
        val dy = endPx.y - startPx.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (!dist.isFinite()) return end
        if (clearancePx <= 0f) return end
        if (dist <= clearancePx) return null
        val scale = (dist - clearancePx) / dist
        val clippedX = startPx.x + dx * scale
        val clippedY = startPx.y + dy * scale
        return projection.fromScreenLocation(PointF(clippedX, clippedY))
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
            "render#$renderId seg=$index kind=$kind idx=$colorIndex color=${colorHex(colorInt)} " +
                "$widthLabel $radiusLabel " +
                "from=${"%.5f".format(start.latitude)},${"%.5f".format(start.longitude)} " +
                "to=${"%.5f".format(end.latitude)},${"%.5f".format(end.longitude)} " +
                "vario=${"%.2f".format(end.varioMs)} alt=${"%.1f".format(end.altitudeMeters)}"
        )
    }

    private fun colorHex(color: Int): String =
        String.format("#%06X", 0xFFFFFF and color)

    private data class RenderPoint(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double,
        val varioMs: Double,
        val timestampMillis: Long
    )

    private data class ColorRamp(
        val position: Int,
        val r: Int,
        val g: Int,
        val b: Int
    )

    private class ScreenBounds(mapView: MapView?) {
        private val width = mapView?.width?.toFloat() ?: 0f
        private val height = mapView?.height?.toFloat() ?: 0f

        private val minX = if (width > 0f) -1.5f * width else Float.NEGATIVE_INFINITY
        private val maxX = if (width > 0f) 2.5f * width else Float.POSITIVE_INFINITY
        private val minY = if (height > 0f) -1.5f * height else Float.NEGATIVE_INFINITY
        private val maxY = if (height > 0f) 2.5f * height else Float.POSITIVE_INFINITY

        fun isInside(map: MapLibreMap, lat: Double, lon: Double): Boolean {
            if (width <= 0f || height <= 0f) return true
            val point = map.projection.toScreenLocation(LatLng(lat, lon))
            return point.x in minX..maxX && point.y in minY..maxY
        }
    }

    private val vario1Ramp = listOf(
        ColorRamp(0, 0xC4, 0x80, 0x1E),
        ColorRamp(100, 0xA0, 0xA0, 0xA0),
        ColorRamp(200, 0x1E, 0xF1, 0x73)
    )

    private val vario2Ramp = listOf(
        ColorRamp(0, 0x00, 0x00, 0x80),   // navy (largest sink)
        ColorRamp(50, 0x00, 0x00, 0xFF),  // blue
        ColorRamp(85, 0x00, 0xFF, 0xFF),  // cyan
        ColorRamp(100, 0xFF, 0xFF, 0x00), // yellow (zero)
        ColorRamp(130, 0xFF, 0xA5, 0x00), // orange
        ColorRamp(160, 0xFF, 0x00, 0x00), // red
        ColorRamp(200, 0x80, 0x00, 0x80)  // purple (largest lift)
    )

    private val varioEinkRamp = listOf(
        ColorRamp(0, 0x00, 0x00, 0x00),
        ColorRamp(200, 0x80, 0x80, 0x80)
    )

    private val altitudeRamp = listOf(
        ColorRamp(0, 0xFF, 0x00, 0x00),
        ColorRamp(50, 0xFF, 0xFF, 0x00),
        ColorRamp(100, 0x00, 0xFF, 0x00),
        ColorRamp(150, 0x00, 0xFF, 0xFF),
        ColorRamp(200, 0x00, 0x00, 0xFF)
    )
}
