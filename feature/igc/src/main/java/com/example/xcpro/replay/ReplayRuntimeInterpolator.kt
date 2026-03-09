package com.example.xcpro.replay

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ReplayInterpolatedFix(
    val point: IgcPoint,
    val movement: MovementSnapshot
)

class ReplayRuntimeInterpolator(
    private val points: List<IgcPoint>
) {
    private var segmentIndex = 1

    fun reset() {
        segmentIndex = 1
    }

    fun seekTo(timestampMillis: Long) {
        segmentIndex = findSegmentIndex(timestampMillis)
    }

    fun interpolate(timestampMillis: Long): ReplayInterpolatedFix? {
        if (points.isEmpty()) return null
        if (points.size == 1) {
            val only = points.first()
            return ReplayInterpolatedFix(
                point = only.copy(timestampMillis = timestampMillis),
                movement = MovementSnapshot(0.0, 0.0, 0.0, 0.0)
            )
        }

        val clampedTime = timestampMillis
            .coerceAtLeast(points.first().timestampMillis)
            .coerceAtMost(points.last().timestampMillis)
        segmentIndex = findSegmentIndex(clampedTime)

        val p1 = points[segmentIndex]
        val p2 = points[segmentIndex + 1]
        val p0 = points.getOrNull(segmentIndex - 1)
        val p3 = points.getOrNull(segmentIndex + 2)

        val interpolated = if (p0 != null && p3 != null) {
            interpolateCatmullRom(p0, p1, p2, p3, clampedTime)
        } else {
            interpolateLinear(p1, p2, clampedTime)
        }

        val bearing = computeBearingWithWindow(p0, p1, p2, p3, clampedTime)
        val segmentDistanceMeters = haversine(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        val speedMs = computeSegmentSpeedMetersPerSecond(
            segmentDistanceMeters = segmentDistanceMeters,
            t1Millis = p1.timestampMillis,
            t2Millis = p2.timestampMillis
        )
        val movement = movementFromSpeedBearing(speedMs, bearing, segmentDistanceMeters)

        return ReplayInterpolatedFix(interpolated, movement)
    }

    private fun findSegmentIndex(timestampMillis: Long): Int {
        if (points.size < 2) return 0
        val minIndex = if (points.size >= 4) 1 else 0
        val maxIndex = if (points.size >= 4) points.size - 3 else points.size - 2
        var low = 0
        var high = points.lastIndex - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val t0 = points[mid].timestampMillis
            val t1 = points[mid + 1].timestampMillis
            if (timestampMillis < t0) {
                high = mid - 1
            } else if (timestampMillis >= t1) {
                low = mid + 1
            } else {
                return mid.coerceIn(minIndex, maxIndex)
            }
        }
        return low.coerceIn(minIndex, maxIndex)
    }

    private fun interpolateCatmullRom(
        p0: IgcPoint,
        p1: IgcPoint,
        p2: IgcPoint,
        p3: IgcPoint,
        timestampMillis: Long
    ): IgcPoint {
        val fraction = timeFraction(timestampMillis, p1.timestampMillis, p2.timestampMillis)
        val coeffs = catmullRomCoefficients(fraction, CATMULL_ROM_TIME)

        fun catmull(a: Double, b: Double, c: Double, d: Double): Double =
            a * coeffs[0] + b * coeffs[1] + c * coeffs[2] + d * coeffs[3]

        val gpsAltitude = catmull(p0.gpsAltitude, p1.gpsAltitude, p2.gpsAltitude, p3.gpsAltitude)
        val pressureAltitude = if (p0.pressureAltitude != null &&
            p1.pressureAltitude != null &&
            p2.pressureAltitude != null &&
            p3.pressureAltitude != null
        ) {
            catmull(
                p0.pressureAltitude,
                p1.pressureAltitude,
                p2.pressureAltitude,
                p3.pressureAltitude
            )
        } else {
            lerpOptional(p1.pressureAltitude, p2.pressureAltitude, fraction)
        }

        return IgcPoint(
            timestampMillis = timestampMillis,
            latitude = catmull(p0.latitude, p1.latitude, p2.latitude, p3.latitude),
            longitude = catmull(p0.longitude, p1.longitude, p2.longitude, p3.longitude),
            gpsAltitude = gpsAltitude,
            pressureAltitude = pressureAltitude,
            indicatedAirspeedKmh = lerpOptional(p1.indicatedAirspeedKmh, p2.indicatedAirspeedKmh, fraction),
            trueAirspeedKmh = lerpOptional(p1.trueAirspeedKmh, p2.trueAirspeedKmh, fraction)
        )
    }

    private fun interpolateLinear(
        p1: IgcPoint,
        p2: IgcPoint,
        timestampMillis: Long
    ): IgcPoint {
        val fraction = timeFraction(timestampMillis, p1.timestampMillis, p2.timestampMillis)
        return IgcPoint(
            timestampMillis = timestampMillis,
            latitude = lerp(p1.latitude, p2.latitude, fraction),
            longitude = lerp(p1.longitude, p2.longitude, fraction),
            gpsAltitude = lerp(p1.gpsAltitude, p2.gpsAltitude, fraction),
            pressureAltitude = lerpOptional(p1.pressureAltitude, p2.pressureAltitude, fraction),
            indicatedAirspeedKmh = lerpOptional(p1.indicatedAirspeedKmh, p2.indicatedAirspeedKmh, fraction),
            trueAirspeedKmh = lerpOptional(p1.trueAirspeedKmh, p2.trueAirspeedKmh, fraction)
        )
    }

    private fun computeBearingWithWindow(
        p0: IgcPoint?,
        p1: IgcPoint,
        p2: IgcPoint,
        p3: IgcPoint?,
        timestampMillis: Long
    ): Double {
        if (p0 == null || p3 == null) {
            return bearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        val before = interpolateCatmullRom(p0, p1, p2, p3, timestampMillis - BEARING_SAMPLE_OFFSET_MS)
        val after = interpolateCatmullRom(p0, p1, p2, p3, timestampMillis + BEARING_SAMPLE_OFFSET_MS)
        return bearing(before.latitude, before.longitude, after.latitude, after.longitude)
    }

    private fun computeSegmentSpeedMetersPerSecond(
        segmentDistanceMeters: Double,
        t1Millis: Long,
        t2Millis: Long
    ): Double {
        val dtSeconds = ((t2Millis - t1Millis) / 1000.0).coerceAtLeast(0.001)
        return segmentDistanceMeters / dtSeconds
    }

    private fun movementFromSpeedBearing(
        speedMs: Double,
        bearingDeg: Double,
        segmentDistanceMeters: Double
    ): MovementSnapshot {
        val rad = Math.toRadians(bearingDeg)
        val east = speedMs * sin(rad)
        val north = speedMs * cos(rad)
        return MovementSnapshot(
            speedMs = speedMs,
            distanceMeters = segmentDistanceMeters,
            east = east,
            north = north
        )
    }

    private fun timeFraction(timeMs: Long, t1: Long, t2: Long): Double {
        val denom = (t2 - t1).toDouble()
        if (denom == 0.0) return 0.0
        return (timeMs - t1).toDouble() / denom
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    private fun lerpOptional(a: Double?, b: Double?, t: Double): Double? =
        if (a != null && b != null) lerp(a, b, t) else null

    private fun catmullRomCoefficients(u: Double, time: Double): DoubleArray {
        val u2 = u * u
        val u3 = u2 * u
        val c0 = -time * u3 + 2 * time * u2 - time * u
        val c1 = (2 - time) * u3 + (time - 3) * u2 + 1
        val c2 = (time - 2) * u3 + (3 - 2 * time) * u2 + time * u
        val c3 = time * u3 - time * u2
        return doubleArrayOf(c0, c1, c2, c3)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val CATMULL_ROM_TIME = 0.98
        private const val BEARING_SAMPLE_OFFSET_MS = 50L
    }
}
