package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng
import kotlin.math.*

/**
 * Lightweight pose smoother inspired by XCSoar:
 * - Dead-reckons forward between GPS fixes (up to a short limit)
 * - Low-passes position and heading with short time constants
 * - Scales smoothing with reported accuracy to damp noisy fixes
 *
 * All smoothing is visual-only; raw fixes are untouched elsewhere.
 */
class DisplayPoseSmoother {

    data class RawFix(
        val latitude: Double,
        val longitude: Double,
        val speedMs: Double,
        val trackDeg: Double,
        val magneticHeading: Double,
        val accuracyM: Double,
        val timestampMs: Long,
        val orientationMode: MapOrientationMode
    )

    data class DisplayPose(
        val location: LatLng,
        val trackDeg: Double,
        val magneticHeading: Double,
        val orientationMode: MapOrientationMode,
        val accuracyM: Double,
        val speedMs: Double,
        val updatedAtMs: Long
    )

    private var lastRaw: RawFix? = null
    private var lastDisplay: DisplayPose? = null
    private var lastTickMs: Long = 0L

    fun pushRawFix(raw: RawFix) {
        lastRaw = raw
    }

    fun tick(nowMs: Long): DisplayPose? {
        val raw = lastRaw ?: return null
        lastTickMs = nowMs

        val rawAgeMs = (nowMs - raw.timestampMs).coerceAtLeast(0L)
        if (rawAgeMs > STALE_FIX_TIMEOUT_MS) {
            // Keep showing the last pose rather than jumping to stale data
            return lastDisplay
        }

        val targetLocation = predictLocation(raw, rawAgeMs)

        val dtMs = if (lastDisplay == null) POS_SMOOTH_MS.toLong() else (nowMs - lastDisplay!!.updatedAtMs).coerceAtLeast(1L)
        val posAlpha = positionAlpha(dtMs, raw.accuracyM)

        val newLocation = if (lastDisplay == null) {
            targetLocation
        } else {
            val prev = lastDisplay!!.location
            LatLng(
                lerp(prev.latitude, targetLocation.latitude, posAlpha),
                lerp(prev.longitude, targetLocation.longitude, posAlpha)
            )
        }

        val headingAlpha = headingAlpha(dtMs, raw.speedMs, raw.accuracyM)
        val newTrack = if (lastDisplay == null) raw.trackDeg else lerpAngle(lastDisplay!!.trackDeg, raw.trackDeg, headingAlpha)

        val pose = DisplayPose(
            location = newLocation,
            trackDeg = newTrack,
            magneticHeading = raw.magneticHeading,
            orientationMode = raw.orientationMode,
            accuracyM = raw.accuracyM,
            speedMs = raw.speedMs,
            updatedAtMs = nowMs
        )

        lastDisplay = pose
        return pose
    }

    private fun positionAlpha(dtMs: Long, accuracyM: Double): Double {
        val base = (dtMs.toDouble() / POS_SMOOTH_MS).coerceIn(0.0, 1.0)
        val accuracyScale = when {
            accuracyM > 20.0 -> 0.25
            accuracyM > 12.0 -> 0.4
            accuracyM > 8.0 -> 0.6
            accuracyM > 5.0 -> 0.8
            else -> 1.0
        }
        return base * accuracyScale
    }

    private fun headingAlpha(dtMs: Long, speedMs: Double, accuracyM: Double): Double {
        val base = (dtMs.toDouble() / HEADING_SMOOTH_MS).coerceIn(0.0, 1.0)
        val speedGate = if (speedMs < MIN_SPEED_FOR_HEADING_MS) 0.25 else 1.0
        val accuracyGate = if (accuracyM > 15.0) 0.5 else 1.0
        return base * speedGate * accuracyGate
    }

    private fun predictLocation(raw: RawFix, rawAgeMs: Long): LatLng {
        val travelTimeS = (rawAgeMs.coerceAtMost(DEAD_RECKON_LIMIT_MS)).toDouble() / 1000.0
        if (travelTimeS <= 0.0 || raw.speedMs <= 0.0) {
            return LatLng(raw.latitude, raw.longitude)
        }

        val distance = raw.speedMs * travelTimeS
        val (lat, lon) = project(raw.latitude, raw.longitude, raw.trackDeg, distance)
        return LatLng(lat, lon)
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
        private const val DEAD_RECKON_LIMIT_MS = 500L          // how far ahead we predict
        private const val STALE_FIX_TIMEOUT_MS = 2_000L        // stop updating after this
        private const val POS_SMOOTH_MS = 300.0                // position low-pass time constant
        private const val HEADING_SMOOTH_MS = 250.0            // heading low-pass time constant
        private const val MIN_SPEED_FOR_HEADING_MS = 2.0       // below this, heading is noisy
    }
}
