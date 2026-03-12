package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.models.RacingWaypoint

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

        val gateWidthMeters = waypoint.gateWidthMeters

        // Calculate direction to next waypoint
        val bearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)

        // Start line is perpendicular to the bearing
        val perpBearing = (bearing + 90.0) % 360.0
        val halfWidth = gateWidthMeters / 2.0

        // Calculate both ends of the start line
        val point1 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, perpBearing, halfWidth)
        val point2 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, (perpBearing + 180.0) % 360.0, halfWidth)

        // Find closest point on line to next waypoint
        return findClosestPointOnLine(point1, point2, nextWaypoint.lat, nextWaypoint.lon)
    }

    override fun calculateDistanceMeters(from: RacingWaypoint, to: RacingWaypoint): Double {
        // For start lines, use haversine distance from center to center as approximation
        return RacingGeometryUtils.haversineDistanceMeters(from.lat, from.lon, to.lat, to.lon)
    }

    override fun isWithinObservationZone(
        position: Pair<Double, Double>,
        waypoint: RacingWaypoint
    ): Boolean {
        // For start lines, check if position is close to the line (within small tolerance)
        val gateWidthMeters = waypoint.gateWidthMeters
        val tolerance = 100.0 // 100 meter tolerance for line crossing detection

        // Calculate line endpoints
        val bearing = 0.0 // We don't have next waypoint context here, use default
        val perpBearing = (bearing + 90.0) % 360.0
        val halfWidth = gateWidthMeters / 2.0

        val point1 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, perpBearing, halfWidth)
        val point2 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, (perpBearing + 180.0) % 360.0, halfWidth)

        // Calculate distance from position to line
        val distanceToLine = distanceFromPointToLine(position, point1, point2)
        return distanceToLine <= tolerance
    }

    override fun getEffectiveRadiusMeters(waypoint: RacingWaypoint): Double? {
        // Start lines have no radius, return half the line length for bounds calculation
        return waypoint.gateWidthMeters / 2.0
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

            val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(sampleLat, sampleLon, targetLat, targetLon)

            if (distanceMeters < minDistance) {
                minDistance = distanceMeters
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
        return RacingGeometryUtils.haversineDistanceMeters(point.first, point.second, closestPoint.first, closestPoint.second)
    }
}
