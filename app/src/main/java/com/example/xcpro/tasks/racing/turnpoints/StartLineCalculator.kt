package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import kotlin.math.*

/**
 * Start Line Calculator for Racing Tasks
 *
 * Implements start line geometry for racing tasks only.
 * Start line is perpendicular to the track to first turnpoint.
 */
class StartLineCalculator : TurnPointCalculator {

    override fun calculateOptimalTouchPoint(waypoint: RacingWaypoint, context: TaskContext): Pair<Double, Double> {
        // For start lines, find the optimal crossing point (closest to next turnpoint)
        val nextWaypoint = context.nextWaypoint
        if (nextWaypoint == null) {
            return Pair(waypoint.lat, waypoint.lon) // Return waypoint center if no next waypoint
        }

        val gateWidthMeters = waypoint.gateWidth * 1000.0

        // Calculate direction to next waypoint
        val bearing = calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)

        // Start line is perpendicular to the bearing
        val perpBearing = (bearing + 90.0) % 360.0
        val halfWidth = gateWidthMeters / 2.0

        // Calculate both ends of the start line
        val point1 = calculateDestination(waypoint.lat, waypoint.lon, halfWidth, perpBearing)
        val point2 = calculateDestination(waypoint.lat, waypoint.lon, halfWidth, (perpBearing + 180.0) % 360.0)

        // Find closest point on line to next waypoint
        return findClosestPointOnLine(point1, point2, nextWaypoint.lat, nextWaypoint.lon)
    }

    override fun calculateDistance(from: RacingWaypoint, to: RacingWaypoint): Double {
        // For start lines, use haversine distance from center to center as approximation
        return calculateHaversineDistance(from.lat, from.lon, to.lat, to.lon)
    }

    override fun isWithinObservationZone(
        position: Pair<Double, Double>,
        waypoint: RacingWaypoint
    ): Boolean {
        // For start lines, check if position is close to the line (within small tolerance)
        val gateWidthMeters = waypoint.gateWidth * 1000.0
        val tolerance = 100.0 // 100 meter tolerance for line crossing detection

        // Calculate line endpoints
        val bearing = 0.0 // We don't have next waypoint context here, use default
        val perpBearing = (bearing + 90.0) % 360.0
        val halfWidth = gateWidthMeters / 2.0

        val point1 = calculateDestination(waypoint.lat, waypoint.lon, halfWidth, perpBearing)
        val point2 = calculateDestination(waypoint.lat, waypoint.lon, halfWidth, (perpBearing + 180.0) % 360.0)

        // Calculate distance from position to line
        val distanceToLine = distanceFromPointToLine(position, point1, point2)
        return distanceToLine <= tolerance
    }

    override fun getEffectiveRadius(waypoint: RacingWaypoint): Double? {
        // Start lines have no radius, return half the line length for bounds calculation
        return waypoint.gateWidth / 2.0 // Return in kilometers
    }

    /**
     * Find closest point on line segment to target point
     */
    private fun findClosestPointOnLine(
        linePoint1: Pair<Double, Double>,
        linePoint2: Pair<Double, Double>,
        targetLat: Double,
        targetLon: Double
    ): Pair<Double, Double> {
        var minDistance = Double.MAX_VALUE
        var closestPoint = linePoint1

        // Sample 100 points along the line to find closest
        for (i in 0..100) {
            val t = i / 100.0
            val sampleLat = linePoint1.first + t * (linePoint2.first - linePoint1.first)
            val sampleLon = linePoint1.second + t * (linePoint2.second - linePoint1.second)

            val distance = calculateHaversineDistance(sampleLat, sampleLon, targetLat, targetLon)

            if (distance < minDistance) {
                minDistance = distance
                closestPoint = Pair(sampleLat, sampleLon)
            }
        }

        return closestPoint
    }

    /**
     * Calculate distance from point to line segment
     */
    private fun distanceFromPointToLine(
        point: Pair<Double, Double>,
        linePoint1: Pair<Double, Double>,
        linePoint2: Pair<Double, Double>
    ): Double {
        val closestPoint = findClosestPointOnLine(linePoint1, linePoint2, point.first, point.second)
        return calculateHaversineDistance(point.first, point.second, closestPoint.first, closestPoint.second) * 1000.0 // Convert to meters
    }

    /**
     * Calculate bearing between two points
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    /**
     * Calculate destination point from start point, bearing, and distance
     */
    private fun calculateDestination(lat: Double, lon: Double, distanceMeters: Double, bearingDegrees: Double): Pair<Double, Double> {
        val R = 6371000.0 // Earth's radius in meters
        val bearing = Math.toRadians(bearingDegrees)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val angularDistance = distanceMeters / R

        val newLatRad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearing))
        val newLonRad = lonRad + atan2(sin(bearing) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(newLatRad))

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    /**
     * Calculate haversine distance in kilometers
     */
    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }
}