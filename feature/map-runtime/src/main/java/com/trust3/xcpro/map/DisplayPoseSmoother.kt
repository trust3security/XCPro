package com.trust3.xcpro.map

import com.trust3.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng
import kotlin.math.*

/**
 * Lightweight pose smoother inspired by a legacy glide computer:
 * - Dead-reckons forward between GPS fixes (up to a short limit)
 * - Low-passes position and heading with short time constants
 * - Scales smoothing with reported accuracy to damp noisy fixes
 *
 * All smoothing is visual-only; raw fixes are untouched elsewhere.
 */
class DisplayPoseSmoother(
    private val minSpeedForHeadingMs: Double = DEFAULT_MIN_SPEED_FOR_HEADING_MS,
    private val minSpeedForPredictionMs: Double = DEFAULT_MIN_SPEED_FOR_HEADING_MS,
    private val config: DisplayPoseSmoothingConfig = DisplayPoseSmoothingConfig(),
    private val adaptiveSmoothingEnabled: Boolean = false
) {

    data class RawFix(
        val latitude: Double,
        val longitude: Double,
        val speedMs: Double,
        val trackDeg: Double,
        val headingDeg: Double,
        val accuracyM: Double,
        val bearingAccuracyDeg: Double?,
        val speedAccuracyMs: Double?,
        val timestampMs: Long,
        val orientationMode: MapOrientationMode
    )

    data class DisplayPose(
        val location: LatLng,
        val trackDeg: Double,
        val headingDeg: Double,
        val orientationMode: MapOrientationMode,
        val accuracyM: Double,
        val bearingAccuracyDeg: Double?,
        val speedAccuracyMs: Double?,
        val speedMs: Double,
        val updatedAtMs: Long
    )

    private var lastRaw: RawFix? = null
    private var lastDisplay: DisplayPose? = null
    private var lastTickMs: Long = 0L

    fun reset() {
        lastRaw = null
        lastDisplay = null
        lastTickMs = 0L
    }

    fun pushRawFix(raw: RawFix) {
        val previousRaw = lastRaw
        if (shouldReanchorForLargeFixGap(previousRaw, raw) ||
            shouldReanchorForStaleRenderGap(raw)
        ) {
            // Re-anchor after long fix gaps or stale render gaps so foreground
            // resume/doze recovery does not crawl from an old visual pose.
            clearVisualContinuity()
        }
        lastRaw = raw
    }

    fun tick(nowMs: Long): DisplayPose? {
        val raw = lastRaw ?: return null
        lastTickMs = nowMs

        val effectiveConfig = if (adaptiveSmoothingEnabled) {
            DisplayPoseAdaptiveSmoothing.effectiveConfig(config, raw.speedMs, raw.accuracyM)
        } else {
            config
        }

        val rawAgeMs = (nowMs - raw.timestampMs).coerceAtLeast(0L)
        if (rawAgeMs > effectiveConfig.staleFixTimeoutMs) {
            // Keep showing the last pose rather than jumping to stale data
            return lastDisplay
        }

        val targetLocation = predictLocation(raw, rawAgeMs, effectiveConfig.deadReckonLimitMs)

        val dtMs = if (lastDisplay == null) {
            effectiveConfig.posSmoothMs.toLong()
        } else {
            (nowMs - lastDisplay!!.updatedAtMs).coerceAtLeast(1L)
        }
        val posAlpha = positionAlpha(dtMs, raw.accuracyM, effectiveConfig.posSmoothMs)

        val clampedTarget = lastDisplay?.let { previous ->
            clampTarget(
                previous = previous.location,
                target = targetLocation,
                speedMs = raw.speedMs,
                speedAccuracyMs = raw.speedAccuracyMs,
                accuracyM = raw.accuracyM,
                dtMs = dtMs
            )
        } ?: targetLocation

        val newLocation = if (lastDisplay == null) {
            clampedTarget
        } else {
            val prev = lastDisplay!!.location
            LatLng(
                lerp(prev.latitude, clampedTarget.latitude, posAlpha),
                lerp(prev.longitude, clampedTarget.longitude, posAlpha)
            )
        }

        val headingAlpha = headingAlpha(
            dtMs,
            raw.speedMs,
            raw.accuracyM,
            raw.bearingAccuracyDeg,
            effectiveConfig.headingSmoothMs
        )
        val newTrack = if (lastDisplay == null) raw.trackDeg else lerpAngle(lastDisplay!!.trackDeg, raw.trackDeg, headingAlpha)

        val pose = DisplayPose(
            location = newLocation,
            trackDeg = newTrack,
            headingDeg = raw.headingDeg,
            orientationMode = raw.orientationMode,
            accuracyM = raw.accuracyM,
            bearingAccuracyDeg = raw.bearingAccuracyDeg,
            speedAccuracyMs = raw.speedAccuracyMs,
            speedMs = raw.speedMs,
            updatedAtMs = nowMs
        )

        lastDisplay = pose
        return pose
    }

    private fun positionAlpha(dtMs: Long, accuracyM: Double, posSmoothMs: Double): Double {
        val base = (dtMs.toDouble() / posSmoothMs).coerceIn(0.0, 1.0)
        val accuracyScale = when {
            accuracyM > 20.0 -> 0.25
            accuracyM > 12.0 -> 0.4
            accuracyM > 8.0 -> 0.6
            accuracyM > 5.0 -> 0.8
            else -> 1.0
        }
        return base * accuracyScale
    }

    private fun headingAlpha(
        dtMs: Long,
        speedMs: Double,
        accuracyM: Double,
        bearingAccuracyDeg: Double?,
        headingSmoothMs: Double
    ): Double {
        val base = (dtMs.toDouble() / headingSmoothMs).coerceIn(0.0, 1.0)
        val speedGate = if (speedMs < minSpeedForHeadingMs) 0.25 else 1.0
        val accuracyGate = if (accuracyM > 15.0) 0.5 else 1.0
        val bearingGate = bearingAccuracyDeg
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { accuracyDeg ->
                val clamped = accuracyDeg.coerceIn(1.0, 30.0)
                (1.0 - ((clamped - 1.0) / 29.0)) * 0.8 + 0.2
            } ?: 1.0
        return base * speedGate * accuracyGate * bearingGate
    }

    private fun predictLocation(
        raw: RawFix,
        rawAgeMs: Long,
        deadReckonLimitMs: Long
    ): LatLng {
        if (raw.speedMs < minSpeedForPredictionMs) {
            return LatLng(raw.latitude, raw.longitude)
        }
        val speedAccuracy = raw.speedAccuracyMs
            ?.takeIf { it.isFinite() && it >= 0.0 }
        if (speedAccuracy != null && speedAccuracy > SPEED_ACCURACY_POOR_MS) {
            return LatLng(raw.latitude, raw.longitude)
        }

        val bearingScale = bearingPredictionScale(raw.bearingAccuracyDeg)
        val effectiveAgeMs = (rawAgeMs.coerceAtMost(deadReckonLimitMs).toDouble() * bearingScale)
            .coerceAtLeast(0.0)
        val travelTimeS = effectiveAgeMs / 1000.0
        if (travelTimeS <= 0.0 || raw.speedMs <= 0.0) {
            return LatLng(raw.latitude, raw.longitude)
        }

        val distance = raw.speedMs * travelTimeS
        val (lat, lon) = project(raw.latitude, raw.longitude, raw.trackDeg, distance)
        return LatLng(lat, lon)
    }

    private fun bearingPredictionScale(bearingAccuracyDeg: Double?): Double {
        val accuracy = bearingAccuracyDeg?.takeIf { it.isFinite() && it >= 0.0 } ?: return 1.0
        val clamped = accuracy.coerceIn(BEARING_ACCURACY_MIN_DEG, BEARING_ACCURACY_BAD_DEG)
        val t = (clamped - BEARING_ACCURACY_MIN_DEG) / (BEARING_ACCURACY_BAD_DEG - BEARING_ACCURACY_MIN_DEG)
        return (1.0 - t) * 1.0 + t * PREDICTION_SCALE_MIN
    }

    private fun clampTarget(
        previous: LatLng,
        target: LatLng,
        speedMs: Double,
        speedAccuracyMs: Double?,
        accuracyM: Double,
        dtMs: Long
    ): LatLng {
        val distance = distanceMeters(previous.latitude, previous.longitude, target.latitude, target.longitude)
        if (!distance.isFinite()) return target

        val cappedDtMs = dtMs.coerceAtMost(CLAMP_MAX_DT_MS)
        val dtSec = cappedDtMs.toDouble() / 1000.0

        val safeAccuracyM = accuracyM.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val accuracyTerm = safeAccuracyM * CLAMP_ACCURACY_MULTIPLIER
        val speedTerm = if (speedMs.isFinite() && speedMs > 0.0) speedMs * dtSec * CLAMP_SPEED_MULTIPLIER else 0.0
        val speedAccuracy = speedAccuracyMs?.takeIf { it.isFinite() && it >= 0.0 }
        val speedAccuracyPoor = speedAccuracy != null && speedAccuracy > SPEED_ACCURACY_POOR_MS

        val allowed = max(
            CLAMP_MIN_METERS,
            if (speedAccuracyPoor) accuracyTerm else accuracyTerm + speedTerm
        )

        if (distance <= allowed) return target

        val bearing = bearingDegrees(previous.latitude, previous.longitude, target.latitude, target.longitude)
        if (!bearing.isFinite()) return target
        val (lat, lon) = project(previous.latitude, previous.longitude, bearing, allowed)
        return LatLng(lat, lon)
    }

    private fun shouldReanchorForLargeFixGap(
        previous: RawFix?,
        current: RawFix
    ): Boolean {
        val previousFix = previous ?: return false
        val gapMs = current.timestampMs - previousFix.timestampMs
        if (gapMs <= 0L) {
            return false
        }
        return gapMs > LARGE_FIX_GAP_REANCHOR_MS
    }

    private fun shouldReanchorForStaleRenderGap(current: RawFix): Boolean {
        if (lastDisplay == null) {
            return false
        }
        val lastRenderedTickMs = lastTickMs
        if (lastRenderedTickMs <= 0L) {
            return false
        }
        val gapMs = current.timestampMs - lastRenderedTickMs
        if (gapMs <= 0L) {
            return false
        }
        return gapMs > config.staleFixTimeoutMs
    }

    private fun clearVisualContinuity() {
        lastDisplay = null
        lastTickMs = 0L
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun project(latDeg: Double, lonDeg: Double, trackDeg: Double, distanceM: Double): Pair<Double, Double> {
        val lat1 = Math.toRadians(latDeg)
        val lon1 = Math.toRadians(lonDeg)
        val bearing = Math.toRadians(trackDeg)
        val angDist = distanceM / EARTH_RADIUS_M

        val sinLat1 = sin(lat1)
        val cosLat1 = cos(lat1)
        val sinAng = sin(angDist)
        val cosAng = cos(angDist)

        val lat2 = asin(sinLat1 * cosAng + cosLat1 * sinAng * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sinAng * cosLat1,
            cosAng - sinLat1 * sin(lat2)
        )

        val lonNorm = ((Math.toDegrees(lon2) + 540.0) % 360.0) - 180.0
        return Pair(Math.toDegrees(lat2), lonNorm)
    }

    private fun lerp(a: Double, b: Double, alpha: Double): Double = a + (b - a) * alpha

    private fun lerpAngle(fromDeg: Double, toDeg: Double, alpha: Double): Double {
        val diff = ((toDeg - fromDeg + 540.0) % 360.0) - 180.0
        return (fromDeg + diff * alpha + 360.0) % 360.0
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val DEFAULT_MIN_SPEED_FOR_HEADING_MS = 2.0 // below this, heading is noisy
        private const val BEARING_ACCURACY_MIN_DEG = 1.0
        private const val BEARING_ACCURACY_BAD_DEG = 20.0
        private const val PREDICTION_SCALE_MIN = 0.2
        private const val SPEED_ACCURACY_POOR_MS = 2.5
        private const val CLAMP_MIN_METERS = 5.0
        private const val CLAMP_ACCURACY_MULTIPLIER = 3.0
        private const val CLAMP_SPEED_MULTIPLIER = 1.5
        private const val CLAMP_MAX_DT_MS = 2_000L
        private const val LARGE_FIX_GAP_REANCHOR_MS = 5_000L
    }
}
