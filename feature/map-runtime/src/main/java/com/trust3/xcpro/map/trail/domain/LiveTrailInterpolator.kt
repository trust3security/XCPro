package com.trust3.xcpro.map.trail.domain

import com.trust3.xcpro.map.trail.TrailGeo
import com.trust3.xcpro.map.trail.TrailSample
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Adds smooth in-memory points for live circling trail updates.
 * Output remains deterministic and does not touch replay pathways.
 */
internal class LiveTrailInterpolator(
    private val stepMillis: Long = DEFAULT_STEP_MS,
    private val turnAngleThresholdDeg: Double = DEFAULT_TURN_ANGLE_THRESHOLD_DEG,
    private val controlDistanceRatio: Double = DEFAULT_CONTROL_DISTANCE_RATIO,
    private val resetDistanceM: Double = DEFAULT_RESET_DISTANCE_M,
    private val resetBackstepMs: Long = DEFAULT_RESET_BACKSTEP_MS
) {
    private data class LocalPoint(
        val x: Double,
        val y: Double
    )

    private var lastSample: TrailSample? = null
    private var lastAdjustedTimestamp: Long? = null
    private var lastRawTimestamp: Long? = null

    fun reset() {
        lastSample = null
        lastAdjustedTimestamp = null
        lastRawTimestamp = null
    }

    fun expand(sample: TrailSample): List<TrailSample> {
        val adjusted = adjustSample(sample)
        val previous = lastSample
        val expanded = ArrayList<TrailSample>(4)

        if (previous != null && adjusted.timestampMillis > previous.timestampMillis) {
            val dt = adjusted.timestampMillis - previous.timestampMillis
            val distance = TrailGeo.distanceMeters(
                previous.latitude,
                previous.longitude,
                adjusted.latitude,
                adjusted.longitude
            )
            if (distance > INTERPOLATION_DISTANCE_MIN_M) {
                val useCurve = shouldUseCurveInterpolation(previous, adjusted)
                val turnDelta = if (useCurve) {
                    abs(angularDistanceDeg(previous.trackDegrees, adjusted.trackDegrees))
                } else {
                    0.0
                }
                val resolvedStep = resolveStepMillis(
                    turnDeltaDeg = turnDelta,
                    baseStepMillis = stepMillis
                )
                val steps = (dt / resolvedStep).coerceAtLeast(1L)
                    .coerceAtMost(MAX_INTERPOLATION_STEPS.toLong())
                    .toInt()
                if (steps > 1) {
                    val total = steps.toFloat()
                    for (i in 1 until steps) {
                        val t = i / total
                        val ts = previous.timestampMillis + (dt * i / steps)
                        val interpolated = if (useCurve) {
                            interpolateCurve(previous, adjusted, t)
                        } else {
                            linearPoint(previous, adjusted, t)
                        }
                        expanded.add(
                            interpolated.copy(
                                timestampMillis = ts,
                                altitudeMeters = lerp(previous.altitudeMeters, adjusted.altitudeMeters, t),
                                varioMs = lerp(previous.varioMs, adjusted.varioMs, t),
                                windSpeedMs = lerp(previous.windSpeedMs, adjusted.windSpeedMs, t),
                                windDirectionFromDeg = lerpAngleDeg(
                                    previous.windDirectionFromDeg,
                                    adjusted.windDirectionFromDeg,
                                    t
                                )
                            )
                        )
                    }
                }
            }
        }

        expanded.add(adjusted)
        lastSample = adjusted
        lastRawTimestamp = sample.timestampMillis
        return expanded
    }

    fun shouldReset(sample: TrailSample): Boolean {
        val last = lastSample ?: return false
        val backstep = last.timestampMillis - sample.timestampMillis
        if (backstep > resetBackstepMs) return true
        val rawBackstep = lastRawTimestamp?.let { it - sample.timestampMillis } ?: 0L
        if (rawBackstep > resetBackstepMs) return true
        val distance = TrailGeo.distanceMeters(
            last.latitude,
            last.longitude,
            sample.latitude,
            sample.longitude
        )
        return distance >= resetDistanceM
    }

    private fun adjustSample(sample: TrailSample): TrailSample {
        val lastAdjusted = lastAdjustedTimestamp
        val adjustedTimestamp = if (lastAdjusted == null) {
            sample.timestampMillis
        } else if (sample.timestampMillis <= lastAdjusted) {
            lastAdjusted + stepMillis
        } else {
            sample.timestampMillis
        }
        lastAdjustedTimestamp = adjustedTimestamp
        return if (adjustedTimestamp == sample.timestampMillis) {
            sample
        } else {
            sample.copy(timestampMillis = adjustedTimestamp)
        }
    }

    private fun lerp(start: Double, end: Double, t: Float): Double =
        start + (end - start) * t

    private fun linearPoint(start: TrailSample, end: TrailSample, t: Float): TrailSample =
        TrailSample(
            latitude = lerp(start.latitude, end.latitude, t),
            longitude = lerp(start.longitude, end.longitude, t),
            timestampMillis = 0L,
            altitudeMeters = start.altitudeMeters,
            varioMs = start.varioMs,
            trackDegrees = start.trackDegrees,
            windSpeedMs = start.windSpeedMs,
            windDirectionFromDeg = start.windDirectionFromDeg
        )

    private fun interpolateCurve(
        start: TrailSample,
        end: TrailSample,
        t: Float
    ): TrailSample {
        val startLocal = toLocal(0.0, 0.0, start.latitude)
        val endLocal = toLocal(end.latitude - start.latitude, end.longitude - start.longitude, start.latitude)

        val distance = TrailGeo.distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
        val controlDistance = distance * controlDistanceRatio
        val startDirection = normalizedBearing(start.trackDegrees)
        val endDirection = normalizedBearing(end.trackDegrees)
        val curveControls = if (startDirection.isFinite() && endDirection.isFinite()) {
            val control1 = LocalPoint(
                x = startLocal.x + vectorDx(startDirection, controlDistance),
                y = startLocal.y + vectorDy(startDirection, controlDistance)
            )
            val control2 = LocalPoint(
                x = endLocal.x + vectorDx((endDirection + 180.0) % 360.0, controlDistance),
                y = endLocal.y + vectorDy((endDirection + 180.0) % 360.0, controlDistance)
            )
            Pair(control1, control2)
        } else {
            return linearPoint(start, end, t)
        }

        val x = cubic(startLocal.x, curveControls.first.x, curveControls.second.x, endLocal.x, t)
        val y = cubic(startLocal.y, curveControls.first.y, curveControls.second.y, endLocal.y, t)
        val (lat, lon) = fromLocal(x, y, start.latitude, start.longitude)

        return TrailSample(
            latitude = lat,
            longitude = lon,
            timestampMillis = 0L,
            altitudeMeters = start.altitudeMeters,
            varioMs = start.varioMs,
            trackDegrees = start.trackDegrees,
            windSpeedMs = start.windSpeedMs,
            windDirectionFromDeg = start.windDirectionFromDeg
        )
    }

    private fun shouldUseCurveInterpolation(start: TrailSample, end: TrailSample): Boolean {
        val distance = TrailGeo.distanceMeters(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude
        )
        if (distance < MIN_CURVE_DISTANCE_METERS) {
            return false
        }
        val startBearing = normalizedBearing(start.trackDegrees)
        val endBearing = normalizedBearing(end.trackDegrees)
        if (!startBearing.isFinite() || !endBearing.isFinite()) return false
        val turnDelta = angularDistanceDeg(startBearing, endBearing)
        return abs(turnDelta) >= turnAngleThresholdDeg
    }

    private fun resolveStepMillis(
        turnDeltaDeg: Double,
        baseStepMillis: Long
    ): Long {
        if (!turnDeltaDeg.isFinite()) {
            return baseStepMillis
        }
        val normalizedTurn = (abs(turnDeltaDeg) / TURN_ANGLE_FOR_DENSE_STEPS_DEG).coerceIn(0.0, 1.0)
        val reduced = baseStepMillis * (1.0 - (1.0 - MIN_TURN_STEP_RATIO) * normalizedTurn)
        return reduced.roundToLong().coerceIn(MIN_STEP_MILLIS, baseStepMillis)
    }

    private fun normalizedBearing(bearing: Double): Double {
        if (!bearing.isFinite()) return Double.NaN
        return ((bearing % 360.0) + 360.0) % 360.0
    }

    private fun angularDistanceDeg(first: Double, second: Double): Double {
        val normalized = ((second - first + 540.0) % 360.0) - 180.0
        return normalized
    }

    private fun toLocal(
        dLatitude: Double,
        dLongitude: Double,
        originLatitude: Double
    ): LocalPoint {
        val dLatMeters = Math.toRadians(dLatitude) * EARTH_RADIUS_M
        val dLonMeters = Math.toRadians(dLongitude) * EARTH_RADIUS_M * kotlin.math.cos(Math.toRadians(originLatitude))
        return LocalPoint(dLonMeters, dLatMeters)
    }

    private fun fromLocal(
        xMeters: Double,
        yMeters: Double,
        originLatitude: Double,
        originLongitude: Double
    ): Pair<Double, Double> {
        val dLat = yMeters / EARTH_RADIUS_M
        val dLon = if (kotlin.math.cos(Math.toRadians(originLatitude)) == 0.0) {
            0.0
        } else {
            xMeters / (EARTH_RADIUS_M * kotlin.math.cos(Math.toRadians(originLatitude)))
        }
        return Pair(
            Math.toDegrees(dLat) + originLatitude,
            Math.toDegrees(dLon) + originLongitude
        )
    }

    private fun vectorDx(bearingDeg: Double, meters: Double): Double {
        val rad = Math.toRadians(bearingDeg)
        return kotlin.math.sin(rad) * meters
    }

    private fun vectorDy(bearingDeg: Double, meters: Double): Double {
        val rad = Math.toRadians(bearingDeg)
        return kotlin.math.cos(rad) * meters
    }

    private fun cubic(start: Double, control1: Double, control2: Double, end: Double, t: Float): Double {
        val oneMinusT = 1.0f - t
        val t2 = t * t
        val t3 = t2 * t
        val omt2 = oneMinusT * oneMinusT
        val omt3 = omt2 * oneMinusT
        return start * omt3 +
            3.0 * control1 * omt2 * t +
            3.0 * control2 * oneMinusT * t2 +
            end * t3
    }

    private fun lerpAngleDeg(start: Double, end: Double, t: Float): Double {
        if (!start.isFinite() || !end.isFinite()) {
            return if (start.isFinite()) start else end
        }
        val delta = ((end - start + 540.0) % 360.0) - 180.0
        return (start + delta * t + 360.0) % 360.0
    }

    private companion object {
        private const val DEFAULT_STEP_MS = 250L
        private const val DEFAULT_TURN_ANGLE_THRESHOLD_DEG = 4.0
        private const val DEFAULT_CONTROL_DISTANCE_RATIO = 0.35
        private const val DEFAULT_RESET_DISTANCE_M = 2_000.0
        private const val DEFAULT_RESET_BACKSTEP_MS = 2_000L
        private const val INTERPOLATION_DISTANCE_MIN_M = 0.5
        private const val MIN_CURVE_DISTANCE_METERS = 0.5
        private const val MIN_TURN_STEP_RATIO = 0.30
        private const val MIN_STEP_MILLIS = 80L
        private const val TURN_ANGLE_FOR_DENSE_STEPS_DEG = 50.0
        private const val MAX_INTERPOLATION_STEPS = 64
        private const val EARTH_RADIUS_M = 6_371_000.0
    }
}
