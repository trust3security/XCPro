package com.example.xcpro.map.trail

import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

internal object SnailTrailMath {
    private const val METERS_PER_PIXEL_EQUATOR = 156543.03392
    private const val TRAIL_WIDTH_SCALE = 0.5f
    private const val MIN_WIDTH_FACTOR = 0.8f
    private const val MAX_WIDTH_FACTOR = 0.42f
    private const val KTS_TO_MS = 0.514444
    private const val MAX_VARIO_KTS = 12.0
    private const val MAX_VARIO_MS = MAX_VARIO_KTS * KTS_TO_MS
    private const val MIN_VARIO_MS = -MAX_VARIO_MS

    fun minTimeMillis(length: TrailLength, nowMillis: Long): Long? = when (length) {
        TrailLength.FULL -> null
        TrailLength.LONG -> nowMillis - 60 * 60_000L
        TrailLength.MEDIUM -> nowMillis - 30 * 60_000L
        TrailLength.SHORT -> nowMillis - 10 * 60_000L
        TrailLength.OFF -> null
    }

    fun filterPoints(
        points: List<TrailPoint>,
        minTimeMillis: Long?,
        currentTimeMillis: Long,
        isCircling: Boolean,
        settings: TrailSettings
    ): List<RenderPoint> {
        if (points.isEmpty()) return emptyList()
        val applyDrift = settings.windDriftEnabled && isCircling

        return points
            .asSequence()
            .filter { minTimeMillis == null || it.timestampMillis >= minTimeMillis }
            .map { point ->
                val driftSeconds = if (applyDrift) {
                    max(0.0, (currentTimeMillis - point.timestampMillis) / 1000.0)
                } else {
                    0.0
                }
                val windValid = applyDrift &&
                    point.windSpeedMs > 0.5 &&
                    point.windDirectionFromDeg.isFinite()
                val (driftLat, driftLon) = if (windValid) {
                    val windToDeg = (point.windDirectionFromDeg + 180.0) % 360.0
                    val (destLat, destLon) = TrailGeo.destinationPoint(
                        point.latitude,
                        point.longitude,
                        windToDeg,
                        point.windSpeedMs
                    )
                    (point.latitude - destLat) to (point.longitude - destLon)
                } else {
                    0.0 to 0.0
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

    fun applyDistanceFilter(points: List<RenderPoint>, minDistanceMeters: Double): List<RenderPoint> {
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

    @Suppress("UNUSED_PARAMETER")
    fun varioRange(points: List<RenderPoint>): Pair<Double, Double> {
        // Fixed scale: 0 = yellow, 12+ kts = top (purple), -12 kts = bottom (navy).
        return MIN_VARIO_MS to MAX_VARIO_MS
    }

    fun altitudeRange(points: List<RenderPoint>): Pair<Double, Double> {
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

    fun varioColorIndex(value: Double, minVal: Double, maxVal: Double): Int {
        val cv = if (value < 0) -value / minVal else value / maxVal
        val idx = (((cv + 1.0) / 2.0) * SnailTrailPalette.NUM_COLORS).toInt()
        return idx.coerceIn(0, SnailTrailPalette.NUM_COLORS - 1)
    }

    fun altitudeColorIndex(value: Double, minVal: Double, maxVal: Double): Int {
        val relative = (value - minVal) / (maxVal - minVal)
        val idx = (relative * (SnailTrailPalette.NUM_COLORS - 1)).toInt()
        return idx.coerceIn(0, SnailTrailPalette.NUM_COLORS - 1)
    }

    fun buildWidths(scalingEnabled: Boolean, density: Float): FloatArray {
        val minWidth = 2f * density * TRAIL_WIDTH_SCALE
        val scaleWidth = 16f * density * TRAIL_WIDTH_SCALE
        val maxWidth = if (scalingEnabled) {
            val peakIndex = (SnailTrailPalette.NUM_COLORS - 1) - SnailTrailPalette.NUM_COLORS / 2f
            max(minWidth, peakIndex * scaleWidth / SnailTrailPalette.NUM_COLORS)
        } else {
            minWidth
        }
        val widthSpan = maxWidth - minWidth
        val widths = FloatArray(SnailTrailPalette.NUM_COLORS)
        for (i in 0 until SnailTrailPalette.NUM_COLORS) {
            val rawWidth = if (i < SnailTrailPalette.NUM_COLORS / 2 || !scalingEnabled) {
                minWidth
            } else {
                max(minWidth, (i - SnailTrailPalette.NUM_COLORS / 2f) * scaleWidth / SnailTrailPalette.NUM_COLORS)
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

    fun minWidth(density: Float): Float {
        return 2f * density * TRAIL_WIDTH_SCALE * MIN_WIDTH_FACTOR
    }

    fun metersPerPixelAtLatitude(latitude: Double, zoom: Float): Double {
        val latRad = Math.toRadians(latitude)
        return METERS_PER_PIXEL_EQUATOR * cos(latRad) / 2.0.pow(zoom.toDouble())
    }

    fun metersPerPixel(
        map: MapLibreMap,
        mapView: MapView?,
        latitude: Double,
        zoom: Float
    ): Double {
        val metersPerPixel = try {
            map.projection?.getMetersPerPixelAtLatitude(latitude)
        } catch (e: Exception) {
            null
        }
        val pixelRatio = mapView?.pixelRatio ?: 0f
        if (metersPerPixel != null && metersPerPixel.isFinite() && metersPerPixel > 0.0) {
            return if (pixelRatio > 0f) metersPerPixel / pixelRatio else metersPerPixel
        }
        return metersPerPixelAtLatitude(latitude, zoom)
    }

    fun clipLineToIconClearance(
        map: MapLibreMap,
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
}
