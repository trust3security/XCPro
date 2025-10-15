package com.example.xcpro.tasks.aat.calculations

import com.example.xcpro.tasks.aat.models.AATLatLng
import kotlin.math.*

/**
 * Autonomous math utilities for AAT (Area Assignment Task) calculations.
 * This module is completely self-contained with ZERO dependencies on other task modules.
 * All math functions are implemented from scratch for AAT use only.
 */
object AATMathUtils {
    
    /**
     * Earth radius in kilometers - AAT's own constant (matching Racing for consistency)
     */
    const val AAT_EARTH_RADIUS_KM = 6371.0
    
    /**
     * Extension functions for AATLatLng to add math utilities
     */
    private fun AATLatLng.latitudeRadians(): Double = Math.toRadians(latitude)
    private fun AATLatLng.longitudeRadians(): Double = Math.toRadians(longitude)
    
    /**
     * Calculate distance between two points using haversine formula.
     * AAT's own implementation - completely autonomous.
     *
     * @param from Starting coordinate
     * @param to Ending coordinate
     * @return Distance in kilometers (matching Racing for consistency)
     */
    fun calculateDistance(from: AATLatLng, to: AATLatLng): Double {
        val lat1Rad = from.latitudeRadians()
        val lon1Rad = from.longitudeRadians()
        val lat2Rad = to.latitudeRadians()
        val lon2Rad = to.longitudeRadians()

        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad

        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * asin(sqrt(a))

        return AAT_EARTH_RADIUS_KM * c
    }

    /**
     * Calculate distance between two points (simple lat/lon parameters).
     * Convenience method for routing from TaskManagerCoordinator.
     *
     * @return Distance in kilometers
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistance(AATLatLng(lat1, lon1), AATLatLng(lat2, lon2))
    }
    
    /**
     * Calculate bearing from one point to another.
     * AAT's own implementation for autonomous operation.
     * 
     * @param from Starting coordinate
     * @param to Ending coordinate
     * @return Bearing in degrees (0-360, where 0 is north)
     */
    fun calculateBearing(from: AATLatLng, to: AATLatLng): Double {
        val lat1Rad = from.latitudeRadians()
        val lon1Rad = from.longitudeRadians()
        val lat2Rad = to.latitudeRadians()
        val lon2Rad = to.longitudeRadians()
        
        val deltaLon = lon2Rad - lon1Rad
        
        val y = sin(deltaLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - 
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLon)
        
        val bearing = atan2(y, x)
        
        // Convert to degrees and normalize to 0-360
        return (Math.toDegrees(bearing) + 360) % 360
    }
    
    /**
     * Calculate initial bearing from one point to another (same as calculateBearing).
     * Provided for clarity in AAT context.
     */
    fun calculateInitialBearing(from: AATLatLng, to: AATLatLng): Double {
        return calculateBearing(from, to)
    }
    
    /**
     * Interpolate between two points by a given fraction.
     * AAT's own implementation for path calculations.
     * 
     * @param p1 First point
     * @param p2 Second point
     * @param fraction Interpolation fraction (0.0 = p1, 1.0 = p2)
     * @return Interpolated position
     */
    fun interpolatePosition(p1: AATLatLng, p2: AATLatLng, fraction: Double): AATLatLng {
        val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
        val lon = p1.longitude + (p2.longitude - p1.longitude) * fraction
        return AATLatLng(lat, lon)
    }
    
    /**
     * Calculate a point at a specific distance and bearing from a starting point.
     * AAT's own implementation for area boundary calculations.
     *
     * @param from Starting coordinate
     * @param bearing Bearing in degrees (0 = north)
     * @param distance Distance in kilometers
     * @return Calculated position
     */
    fun calculatePointAtBearing(
        from: AATLatLng,
        bearing: Double,
        distance: Double
    ): AATLatLng {
        val lat1Rad = from.latitudeRadians()
        val lon1Rad = from.longitudeRadians()
        val bearingRad = Math.toRadians(bearing)
        val angularDistance = distance / AAT_EARTH_RADIUS_KM
        
        val lat2Rad = asin(
            sin(lat1Rad) * cos(angularDistance) +
            cos(lat1Rad) * sin(angularDistance) * cos(bearingRad)
        )
        
        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(lat1Rad),
            cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad)
        )
        
        return AATLatLng(
            Math.toDegrees(lat2Rad),
            Math.toDegrees(lon2Rad)
        )
    }
    
    /**
     * Normalize angle to 0-360 degrees.
     * AAT internal utility function.
     */
    fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }
    
    /**
     * Calculate the difference between two angles, accounting for wrap-around.
     * Result is always in range [-180, 180].
     * AAT internal utility for sector calculations.
     */
    fun angleDifference(angle1: Double, angle2: Double): Double {
        var diff = angle2 - angle1
        while (diff > 180) diff -= 360
        while (diff <= -180) diff += 360
        return diff
    }
    
    /**
     * Check if an angle is between two other angles (handles wrap-around).
     * AAT internal utility for sector area calculations.
     * 
     * @param angle The angle to test
     * @param start Start of the arc
     * @param end End of the arc
     * @return true if angle is between start and end (clockwise)
     */
    fun isAngleBetween(angle: Double, start: Double, end: Double): Boolean {
        val normalizedAngle = normalizeAngle(angle)
        val normalizedStart = normalizeAngle(start)
        val normalizedEnd = normalizeAngle(end)
        
        return if (normalizedStart <= normalizedEnd) {
            // No wrap around
            normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd
        } else {
            // Wrap around case
            normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
        }
    }
    
    /**
     * Calculate the cross track distance (perpendicular distance) from a point to a line.
     * AAT's own implementation for track analysis.
     *
     * @param point The point to measure from
     * @param lineStart Start of the line
     * @param lineEnd End of the line
     * @return Cross track distance in kilometers (positive = right of track)
     */
    fun calculateCrossTrackDistance(
        point: AATLatLng,
        lineStart: AATLatLng,
        lineEnd: AATLatLng
    ): Double {
        val distanceStartToPoint = calculateDistance(lineStart, point) / AAT_EARTH_RADIUS_KM
        val bearingStartToPoint = Math.toRadians(calculateBearing(lineStart, point))
        val bearingStartToEnd = Math.toRadians(calculateBearing(lineStart, lineEnd))

        val crossTrackDistanceRadians = asin(
            sin(distanceStartToPoint) * sin(bearingStartToPoint - bearingStartToEnd)
        )

        return crossTrackDistanceRadians * AAT_EARTH_RADIUS_KM
    }
    
    /**
     * Calculate along track distance (distance along the line) from line start to the point
     * perpendicular to the given point.
     * AAT's own implementation for track analysis.
     *
     * @return Along track distance in kilometers
     */
    fun calculateAlongTrackDistance(
        point: AATLatLng,
        lineStart: AATLatLng,
        lineEnd: AATLatLng
    ): Double {
        val distanceStartToPoint = calculateDistance(lineStart, point) / AAT_EARTH_RADIUS_KM
        val crossTrackDistance = calculateCrossTrackDistance(point, lineStart, lineEnd) / AAT_EARTH_RADIUS_KM

        val alongTrackDistanceRadians = acos(
            cos(distanceStartToPoint) / cos(crossTrackDistance)
        )

        return alongTrackDistanceRadians * AAT_EARTH_RADIUS_KM
    }
    
    /**
     * Convert from external coordinate system to AAT coordinate system.
     * This handles any external LatLng class conversions.
     */
    fun fromExternalLatLng(latitude: Double, longitude: Double): AATLatLng {
        return AATLatLng(latitude, longitude)
    }
    
    /**
     * Convert AAT coordinate to external coordinate system (for display/integration).
     */
    fun toExternalLatLng(point: AATLatLng): Pair<Double, Double> {
        return Pair(point.latitude, point.longitude)
    }
}