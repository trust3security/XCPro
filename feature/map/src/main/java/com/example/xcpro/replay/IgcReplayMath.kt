package com.example.xcpro.replay

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal data class MovementSnapshot(
    val speedMs: Double,
    val distanceMeters: Double,
    val east: Double,
    val north: Double
) {
    val bearingDeg: Float
        get() {
            val angle = (Math.toDegrees(atan2(east, north)) + 360.0) % 360.0
            return angle.toFloat()
        }
}

/**
 * Pure helpers for IGC replay processing.
 *
 * Keep this logic out of [IgcReplayController] so the controller stays focused on replay/session control.
 */
internal object IgcReplayMath {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val INTERPOLATION_STEP_MS = 1_000L  // keep interpolation aligned to 1s gaps
    private const val CATMULL_ROM_TIME = 0.98
    private const val SEA_LEVEL_TEMP_K = 288.15
    private const val LAPSE_RATE_K_PER_M = 0.0065
    private const val EXPONENT = 5.255

    fun groundVector(current: IgcPoint, previous: IgcPoint?): MovementSnapshot {
        val prev = previous ?: current
        val distance = haversine(prev.latitude, prev.longitude, current.latitude, current.longitude)
        val dtSeconds = ((current.timestampMillis - prev.timestampMillis) / 1000.0).coerceAtLeast(1.0)
        val speed = distance / dtSeconds
        val bearing = bearing(prev.latitude, prev.longitude, current.latitude, current.longitude)
        val east = speed * sin(Math.toRadians(bearing))
        val north = speed * cos(Math.toRadians(bearing))
        return MovementSnapshot(
            speedMs = speed,
            distanceMeters = distance,
            east = east,
            north = north
        )
    }

    fun verticalSpeed(current: IgcPoint, previous: IgcPoint?): Double {
        val prevAlt = previous?.pressureAltitude ?: current.pressureAltitude ?: current.gpsAltitude
        val prevTime = previous?.timestampMillis ?: current.timestampMillis
        val dtSeconds = ((current.timestampMillis - prevTime) / 1000.0).coerceAtLeast(1.0)
        val altitude = current.pressureAltitude ?: current.gpsAltitude
        return (altitude - prevAlt) / dtSeconds
    }

    fun densifyPoints(original: List<IgcPoint>): List<IgcPoint> {
        if (original.size < 2) return original
        val result = ArrayList<IgcPoint>(original.size)
        for (i in 0 until original.lastIndex) {
            val current = original[i]
            val next = original[i + 1]
            result += current
            val gap = next.timestampMillis - current.timestampMillis
            if (gap > INTERPOLATION_STEP_MS) {
                var timestamp = current.timestampMillis + INTERPOLATION_STEP_MS
                while (timestamp < next.timestampMillis) {
                    val fraction = ((timestamp - current.timestampMillis).toDouble() / gap.toDouble()).coerceIn(0.0, 1.0)
                    result += interpolatePoint(current, next, timestamp, fraction)
                    timestamp += INTERPOLATION_STEP_MS
                }
            }
        }
        result += original.last()
        return result
    }

    fun densifyPoints(
        original: List<IgcPoint>,
        stepMs: Long,
        jitterMs: Long,
        random: Random
    ): List<IgcPoint> {
        if (original.size < 2) return original
        if (stepMs <= 0L) return original
        val result = ArrayList<IgcPoint>(original.size)
        for (i in 0 until original.lastIndex) {
            val current = original[i]
            val next = original[i + 1]
            result += current
            val gap = next.timestampMillis - current.timestampMillis
            if (gap > stepMs) {
                var timestamp = current.timestampMillis + stepMs
                while (timestamp < next.timestampMillis) {
                    val fraction = ((timestamp - current.timestampMillis).toDouble() / gap.toDouble()).coerceIn(0.0, 1.0)
                    result += interpolatePoint(current, next, timestamp, fraction)
                    val jitter = if (jitterMs > 0L) {
                        val span = jitterMs * 2 + 1
                        random.nextLong(span) - jitterMs
                    } else {
                        0L
                    }
                    val nextStep = (stepMs + jitter).coerceAtLeast(1L)
                    timestamp += nextStep
                }
            }
        }
        result += original.last()
        return result
    }

    fun densifyPointsCatmullRom(
        original: List<IgcPoint>,
        stepMs: Long
    ): List<IgcPoint> {
        if (original.size < 2) return original
        if (stepMs <= 0L) return original
        if (original.size < 4) {
            return densifyPoints(original, stepMs, 0L, Random(0))
        }

        val result = ArrayList<IgcPoint>(original.size)
        for (i in 0 until original.lastIndex) {
            val p1 = original[i]
            val p2 = original[i + 1]
            if (result.isEmpty() || result.last().timestampMillis != p1.timestampMillis) {
                result += p1
            }
            val gap = p2.timestampMillis - p1.timestampMillis
            if (gap <= stepMs) continue

            var timestamp = p1.timestampMillis + stepMs
            while (timestamp < p2.timestampMillis) {
                val fraction = ((timestamp - p1.timestampMillis).toDouble() / gap.toDouble()).coerceIn(0.0, 1.0)
                val interpolated = if (i >= 1 && i + 2 < original.size) {
                    interpolateCatmullRom(
                        p0 = original[i - 1],
                        p1 = p1,
                        p2 = p2,
                        p3 = original[i + 2],
                        timestamp = timestamp,
                        fraction = fraction
                    )
                } else {
                    interpolatePoint(p1, p2, timestamp, fraction)
                }
                result += interpolated
                timestamp += stepMs
            }
        }
        val last = original.last()
        if (result.isEmpty() || result.last().timestampMillis != last.timestampMillis) {
            result += last
        }
        return result
    }

    fun altitudeToPressure(altitudeMeters: Double, qnhHpa: Double): Double {
        val ratio = 1 - (LAPSE_RATE_K_PER_M * altitudeMeters) / SEA_LEVEL_TEMP_K
        return qnhHpa * ratio.pow(EXPONENT)
    }

    private fun interpolateCatmullRom(
        p0: IgcPoint,
        p1: IgcPoint,
        p2: IgcPoint,
        p3: IgcPoint,
        timestamp: Long,
        fraction: Double
    ): IgcPoint {
        val coeffs = catmullRomCoefficients(fraction, CATMULL_ROM_TIME)
        fun catmull(a: Double, b: Double, c: Double, d: Double): Double =
            a * coeffs[0] + b * coeffs[1] + c * coeffs[2] + d * coeffs[3]
        fun lerp(a: Double, b: Double): Double = a + (b - a) * fraction
        fun lerpOptional(a: Double?, b: Double?): Double? =
            if (a != null && b != null) lerp(a, b) else null

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
            lerpOptional(p1.pressureAltitude, p2.pressureAltitude)
        }

        return IgcPoint(
            timestampMillis = timestamp,
            latitude = catmull(p0.latitude, p1.latitude, p2.latitude, p3.latitude),
            longitude = catmull(p0.longitude, p1.longitude, p2.longitude, p3.longitude),
            gpsAltitude = gpsAltitude,
            pressureAltitude = pressureAltitude,
            indicatedAirspeedKmh = lerpOptional(p1.indicatedAirspeedKmh, p2.indicatedAirspeedKmh),
            trueAirspeedKmh = lerpOptional(p1.trueAirspeedKmh, p2.trueAirspeedKmh)
        )
    }

    private fun interpolatePoint(
        start: IgcPoint,
        end: IgcPoint,
        timestamp: Long,
        fraction: Double
    ): IgcPoint {
        fun lerp(a: Double, b: Double): Double = a + (b - a) * fraction
        fun lerpOptional(a: Double?, b: Double?): Double? =
            if (a != null && b != null) lerp(a, b) else null

        val pressureAltitude = when {
            start.pressureAltitude != null && end.pressureAltitude != null ->
                lerp(start.pressureAltitude, end.pressureAltitude)
            start.pressureAltitude != null -> start.pressureAltitude
            end.pressureAltitude != null -> end.pressureAltitude
            else -> null
        }

        return IgcPoint(
            timestampMillis = timestamp,
            latitude = lerp(start.latitude, end.latitude),
            longitude = lerp(start.longitude, end.longitude),
            gpsAltitude = lerp(start.gpsAltitude, end.gpsAltitude),
            pressureAltitude = pressureAltitude,
            indicatedAirspeedKmh = lerpOptional(start.indicatedAirspeedKmh, end.indicatedAirspeedKmh),
            trueAirspeedKmh = lerpOptional(start.trueAirspeedKmh, end.trueAirspeedKmh)
        )
    }

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
        val a = sin(dLat / 2).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2.0)
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
}
