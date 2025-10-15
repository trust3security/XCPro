package com.example.xcpro.tasks.aat.geometry

import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.getAuthorityRadius
import kotlin.math.*

/**
 * AAT Geometry Generator
 *
 * Pure geometry calculations for AAT task visualization.
 * All functions are stateless and perform mathematical calculations only.
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 1 - Geometry Extraction)
 * DEPENDENCIES: None (pure math functions)
 */
class AATGeometryGenerator {

    /**
     * Generate circle coordinates for map polygon
     *
     * @param centerLat Center latitude in degrees
     * @param centerLon Center longitude in degrees
     * @param radiusKm Radius in kilometers
     * @param points Number of points to generate (default 64 for smooth circle)
     * @return List of [longitude, latitude] coordinate pairs in GeoJSON format
     */
    fun generateCircleCoordinates(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        points: Int = 64
    ): List<List<Double>> {
        val earthRadius = 6371.0 // km
        val coords = mutableListOf<List<Double>>()

        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            val lat = centerLat + (radiusKm / earthRadius) * (180 / PI) * cos(angle)
            val lon = centerLon + (radiusKm / earthRadius) * (180 / PI) * sin(angle) / cos(centerLat * PI / 180)
            coords.add(listOf(lon, lat)) // GeoJSON format: [longitude, latitude]
        }

        // Close the polygon
        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    /**
     * Generate perpendicular start line coordinates
     *
     * Creates a line perpendicular to the direction from start to next waypoint.
     * If no next waypoint exists, defaults to east-west orientation.
     *
     * @param startWaypoint The start waypoint
     * @param nextWaypoint The next waypoint (can be null)
     * @param widthKm Width of the start line in kilometers
     * @return List of two [longitude, latitude] coordinate pairs representing line endpoints
     */
    fun generateStartLine(
        startWaypoint: AATWaypoint,
        nextWaypoint: AATWaypoint?,
        widthKm: Double
    ): List<List<Double>> {
        val bearing = if (nextWaypoint != null) {
            calculateBearing(startWaypoint.lat, startWaypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        } else {
            0.0 // Default to north if no next waypoint
        }

        // Perpendicular to the bearing (add 90 degrees)
        val perpBearing = (bearing + 90) % 360
        val perpBearingRad = Math.toRadians(perpBearing)

        val halfWidthKm = widthKm / 2.0
        val earthRadiusKm = 6371.0

        // Calculate two end points of the line
        val angularDistance = halfWidthKm / earthRadiusKm
        val latRad = Math.toRadians(startWaypoint.lat)
        val lonRad = Math.toRadians(startWaypoint.lon)

        // Point 1 (left end)
        val lat1Rad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(perpBearingRad))
        val lon1Rad = lonRad + atan2(sin(perpBearingRad) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(lat1Rad))

        // Point 2 (right end) - opposite direction
        val oppositeBearingRad = Math.toRadians((perpBearing + 180) % 360)
        val lat2Rad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(oppositeBearingRad))
        val lon2Rad = lonRad + atan2(sin(oppositeBearingRad) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(lat2Rad))

        return listOf(
            listOf(Math.toDegrees(lon1Rad), Math.toDegrees(lat1Rad)),
            listOf(Math.toDegrees(lon2Rad), Math.toDegrees(lat2Rad))
        )
    }

    /**
     * Generate perpendicular finish line coordinates
     *
     * Creates a line perpendicular to the direction from previous waypoint to finish.
     * If no previous waypoint exists, defaults to east-west orientation.
     *
     * @param finishWaypoint The finish waypoint
     * @param prevWaypoint The previous waypoint (can be null)
     * @param widthKm Width of the finish line in kilometers
     * @return List of two [longitude, latitude] coordinate pairs representing line endpoints
     */
    fun generateFinishLine(
        finishWaypoint: AATWaypoint,
        prevWaypoint: AATWaypoint?,
        widthKm: Double
    ): List<List<Double>> {
        val bearing = if (prevWaypoint != null) {
            calculateBearing(prevWaypoint.lat, prevWaypoint.lon, finishWaypoint.lat, finishWaypoint.lon)
        } else {
            180.0 // Default to south if no previous waypoint
        }

        // Perpendicular to the bearing (add 90 degrees)
        val perpBearing = (bearing + 90) % 360
        val perpBearingRad = Math.toRadians(perpBearing)

        val halfWidthKm = widthKm / 2.0
        val earthRadiusKm = 6371.0

        // Calculate two end points of the line
        val angularDistance = halfWidthKm / earthRadiusKm
        val latRad = Math.toRadians(finishWaypoint.lat)
        val lonRad = Math.toRadians(finishWaypoint.lon)

        // Point 1 (left end)
        val lat1Rad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(perpBearingRad))
        val lon1Rad = lonRad + atan2(sin(perpBearingRad) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(lat1Rad))

        // Point 2 (right end) - opposite direction
        val oppositeBearingRad = Math.toRadians((perpBearing + 180) % 360)
        val lat2Rad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(oppositeBearingRad))
        val lon2Rad = lonRad + atan2(sin(oppositeBearingRad) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(lat2Rad))

        return listOf(
            listOf(Math.toDegrees(lon1Rad), Math.toDegrees(lat1Rad)),
            listOf(Math.toDegrees(lon2Rad), Math.toDegrees(lat2Rad))
        )
    }

    /**
     * Calculate optimal AAT task path through waypoints
     *
     * AAT tasks use target points within assigned areas for START/TURNPOINT,
     * but finish at the cylinder edge (not center or target).
     *
     * @param waypoints List of AAT waypoints
     * @return List of [longitude, latitude] coordinate pairs representing the optimal path
     */
    fun calculateOptimalAATPath(waypoints: List<AATWaypoint>): List<List<Double>> {
        val pathPoints = mutableListOf<List<Double>>()

        waypoints.forEachIndexed { index, waypoint ->
            when (waypoint.role) {
                com.example.xcpro.tasks.aat.models.AATWaypointRole.START,
                com.example.xcpro.tasks.aat.models.AATWaypointRole.TURNPOINT -> {
                    // START and TURNPOINTS: Use target points within assigned areas
                    val targetLat = waypoint.targetPoint.latitude
                    val targetLon = waypoint.targetPoint.longitude
                    pathPoints.add(listOf(targetLon, targetLat))
                }
                com.example.xcpro.tasks.aat.models.AATWaypointRole.FINISH -> {
                    // FINISH: Task ends at cylinder edge, not center or target point
                    val prevWaypoint = if (index > 0) waypoints[index - 1] else null

                    if (prevWaypoint != null) {
                        // Calculate optimal finish crossing point from previous waypoint
                        val bearing = calculateBearing(
                            prevWaypoint.targetPoint.latitude,
                            prevWaypoint.targetPoint.longitude,
                            waypoint.lat,
                            waypoint.lon
                        )

                        // ✅ SSOT FIX: Calculate finish edge point - use authority radius
                        val radiusKm = waypoint.getAuthorityRadius() / 2.0
                        // Task ends at cylinder edge on approach side (opposite to approach bearing)
                        val finishEdgePoint = calculateDestinationPoint(waypoint.lat, waypoint.lon, (bearing + 180.0) % 360.0, radiusKm)
                        pathPoints.add(listOf(finishEdgePoint.second, finishEdgePoint.first))
                    } else {
                        // Fallback: use finish center if no previous waypoint
                        pathPoints.add(listOf(waypoint.lon, waypoint.lat))
                    }
                }
            }
        }

        return pathPoints
    }

    /**
     * Calculate destination point from bearing and distance
     *
     * Uses great circle navigation to find a point at a given distance and bearing
     * from a start point.
     *
     * @param lat Starting latitude in degrees
     * @param lon Starting longitude in degrees
     * @param bearing Bearing in degrees (0-360, where 0=north, 90=east)
     * @param distance Distance in kilometers
     * @return Pair of (latitude, longitude) in degrees
     */
    fun calculateDestinationPoint(lat: Double, lon: Double, bearing: Double, distance: Double): Pair<Double, Double> {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        val bearingRad = Math.toRadians(bearing)
        val angularDistance = distance / earthRadius
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val newLatRad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearingRad))
        val newLonRad = lonRad + atan2(sin(bearingRad) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(newLatRad))

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    /**
     * Calculate bearing between two points
     *
     * Calculates the initial bearing (forward azimuth) from point 1 to point 2
     * using great circle navigation.
     *
     * @param lat1 Starting latitude in degrees
     * @param lon1 Starting longitude in degrees
     * @param lat2 Destination latitude in degrees
     * @param lon2 Destination longitude in degrees
     * @return Bearing in degrees (0-360, where 0=north, 90=east)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
}
