package com.example.xcpro.map

import com.example.xcpro.common.orientation.MapOrientationMode
import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class DisplayPoseRenderSnapshot(
    val location: LatLng,
    val trackDeg: Double,
    val headingDeg: Double,
    val headingValid: Boolean,
    val bearingAccuracyDeg: Double?,
    val speedAccuracyMs: Double?,
    val mapBearingDeg: Double,
    val cameraTargetBearingDeg: Double,
    val orientationMode: MapOrientationMode,
    val speedMs: Double,
    val timeBase: DisplayClock.TimeBase?
)

internal object DisplayPoseFrameDiffPolicy {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val LOCATION_EPS_METERS = 0.75
    private const val ANGLE_EPS_DEG = 0.5
    private const val SPEED_EPS_MS = 0.1
    private const val ACCURACY_EPS = 0.25

    fun isNoOp(
        previous: DisplayPoseRenderSnapshot?,
        current: DisplayPoseRenderSnapshot
    ): Boolean {
        val last = previous ?: return false
        if (last.orientationMode != current.orientationMode) return false
        if (last.timeBase != current.timeBase) return false
        if (last.headingValid != current.headingValid) return false
        if (!sameLocation(last.location, current.location)) return false
        if (!sameAngle(last.trackDeg, current.trackDeg)) return false
        if (!sameAngle(last.mapBearingDeg, current.mapBearingDeg)) return false
        if (!sameAngle(last.cameraTargetBearingDeg, current.cameraTargetBearingDeg)) return false
        if (!sameSpeed(last.speedMs, current.speedMs)) return false
        if (!sameAccuracy(last.speedAccuracyMs, current.speedAccuracyMs)) return false
        if (current.headingValid) {
            if (!sameAngle(last.headingDeg, current.headingDeg)) return false
            if (!sameAccuracy(last.bearingAccuracyDeg, current.bearingAccuracyDeg)) return false
        }
        return true
    }

    private fun sameLocation(first: LatLng, second: LatLng): Boolean =
        distanceMeters(first, second) <= LOCATION_EPS_METERS

    private fun sameAngle(first: Double, second: Double): Boolean =
        angularDistanceDeg(first, second) <= ANGLE_EPS_DEG

    private fun sameSpeed(first: Double, second: Double): Boolean =
        kotlin.math.abs(first - second) <= SPEED_EPS_MS

    private fun sameAccuracy(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) {
            return first == second
        }
        return kotlin.math.abs(first - second) <= ACCURACY_EPS
    }

    private fun angularDistanceDeg(first: Double, second: Double): Double {
        val normalized = ((second - first + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(normalized)
    }

    private fun distanceMeters(first: LatLng, second: LatLng): Double {
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val rLat1 = Math.toRadians(first.latitude)
        val rLat2 = Math.toRadians(second.latitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }
}
