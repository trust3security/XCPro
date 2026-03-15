// Role: Geographic helpers for trail math (distance and drift).
// Invariants: All inputs/outputs are in degrees and meters.
package com.example.xcpro.map.trail

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.asin
import kotlin.math.pow

internal object TrailGeo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lon < -180.0 || lon > 180.0) return false
        return true
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destinationPoint(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val angularDistance = distanceMeters / EARTH_RADIUS_M
        val bearingRad = Math.toRadians(bearingDeg)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val destLat = asin(
            sin(latRad) * cos(angularDistance) +
                cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )
        val destLon = lonRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(destLat)
        )

        return Math.toDegrees(destLat) to Math.toDegrees(destLon)
    }
}
