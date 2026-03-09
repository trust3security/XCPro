package com.example.xcpro.ogn

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OgnViewportBounds(
    val northLat: Double,
    val southLat: Double,
    val eastLon: Double,
    val westLon: Double
)

object OgnSubscriptionPolicy {
    fun shouldReconnectByCenterMoveMeters(
        previousLat: Double,
        previousLon: Double,
        nextLat: Double,
        nextLon: Double,
        thresholdMeters: Double
    ): Boolean = haversineMeters(previousLat, previousLon, nextLat, nextLon) >= thresholdMeters

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLon1 = Math.toRadians(lon1)
        val radLat2 = Math.toRadians(lat2)
        val radLon2 = Math.toRadians(lon2)
        val dLat = radLat2 - radLat1
        val dLon = radLon2 - radLon1
        val h = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(radLat1) * cos(radLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
        return EARTH_RADIUS_METERS * c
    }

    fun isInViewport(
        latitude: Double,
        longitude: Double,
        bounds: OgnViewportBounds
    ): Boolean {
        val latInside = latitude >= bounds.southLat && latitude <= bounds.northLat
        if (!latInside) return false
        return if (bounds.westLon <= bounds.eastLon) {
            longitude in bounds.westLon..bounds.eastLon
        } else {
            // Anti-meridian crossing.
            longitude >= bounds.westLon || longitude <= bounds.eastLon
        }
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
