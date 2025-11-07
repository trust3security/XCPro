package com.example.xcpro.utils

import kotlin.math.*
import org.maplibre.android.geometry.LatLng

/**
 * Centralized distance calculation utilities for NON-TASK code
 *
 * USAGE RULES:
 * - Use this ONLY for UI, widgets, map overlays, profiles
 * - DO NOT use in Racing tasks (use RacingGeometryUtils instead)
 * - DO NOT use in AAT tasks (use AATMathUtils instead)
 *
 * SSOT: Single implementation for all non-task distance calculations
 */
object DistanceUtils {

    /** Earth's radius in kilometers (WGS84 mean radius) */
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Calculate great-circle distance using Haversine formula
     *
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in kilometers
     */
    fun calculateDistanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_KM * c
    }

    /**
     * Calculate distance between MapLibre LatLng objects
     * Convenience overload for map operations
     */
    fun calculateDistanceKm(from: LatLng, to: LatLng): Double {
        return calculateDistanceKm(
            from.latitude, from.longitude,
            to.latitude, to.longitude
        )
    }

    /**
     * Calculate distance in meters
     * Convenience method for UI that needs meter precision
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }
}
