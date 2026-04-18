package com.trust3.xcpro.tasks.aat.geometry

import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.getAuthorityRadiusMeters
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
    private companion object {
        const val EARTH_RADIUS_METERS = 6371000.0
    }

    /**
     * Meter-first internal circle generator.
     */
    fun generateCircleCoordinatesMeters(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        points: Int = 64
    ): List<List<Double>> {
        val coords = mutableListOf<List<Double>>()

        for (i in 0 until points) {
            val bearing = 360.0 * i / points
            val point = calculateDestinationPointMeters(
                lat = centerLat,
                lon = centerLon,
                bearing = bearing,
                distanceMeters = radiusMeters
            )
            coords.add(listOf(point.second, point.first))
        }

        // Close the polygon
        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    fun generateStartLineMeters(
        startWaypoint: AATWaypoint,
        nextWaypoint: AATWaypoint?,
        widthMeters: Double
    ): List<List<Double>> {
        val bearing = if (nextWaypoint != null) {
            calculateBearing(startWaypoint.lat, startWaypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        } else {
            0.0 // Default to north if no next waypoint
        }

        // Perpendicular to the bearing (add 90 degrees)
        val perpBearing = (bearing + 90) % 360
        val halfWidthMeters = widthMeters / 2.0
        val point1 = calculateDestinationPointMeters(
            lat = startWaypoint.lat,
            lon = startWaypoint.lon,
            bearing = perpBearing,
            distanceMeters = halfWidthMeters
        )
        val point2 = calculateDestinationPointMeters(
            lat = startWaypoint.lat,
            lon = startWaypoint.lon,
            bearing = (perpBearing + 180.0) % 360.0,
            distanceMeters = halfWidthMeters
        )

        return listOf(
            listOf(point1.second, point1.first),
            listOf(point2.second, point2.first)
        )
    }

    fun generateFinishLineMeters(
        finishWaypoint: AATWaypoint,
        prevWaypoint: AATWaypoint?,
        widthMeters: Double
    ): List<List<Double>> {
        val bearing = if (prevWaypoint != null) {
            calculateBearing(prevWaypoint.lat, prevWaypoint.lon, finishWaypoint.lat, finishWaypoint.lon)
        } else {
            180.0 // Default to south if no previous waypoint
        }

        // Perpendicular to the bearing (add 90 degrees)
        val perpBearing = (bearing + 90) % 360
        val halfWidthMeters = widthMeters / 2.0
        val point1 = calculateDestinationPointMeters(
            lat = finishWaypoint.lat,
            lon = finishWaypoint.lon,
            bearing = perpBearing,
            distanceMeters = halfWidthMeters
        )
        val point2 = calculateDestinationPointMeters(
            lat = finishWaypoint.lat,
            lon = finishWaypoint.lon,
            bearing = (perpBearing + 180.0) % 360.0,
            distanceMeters = halfWidthMeters
        )

        return listOf(
            listOf(point1.second, point1.first),
            listOf(point2.second, point2.first)
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
                com.trust3.xcpro.tasks.aat.models.AATWaypointRole.START,
                com.trust3.xcpro.tasks.aat.models.AATWaypointRole.TURNPOINT -> {
                    // START and TURNPOINTS: Use target points within assigned areas
                    val targetLat = waypoint.targetPoint.latitude
                    val targetLon = waypoint.targetPoint.longitude
                    pathPoints.add(listOf(targetLon, targetLat))
                }
                com.trust3.xcpro.tasks.aat.models.AATWaypointRole.FINISH -> {
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

                        //  SSOT FIX: Calculate finish edge point - use authority radius
                        val radiusMeters = waypoint.getAuthorityRadiusMeters()
                        // Task ends at cylinder edge on approach side (opposite to approach bearing)
                        val finishEdgePoint = calculateDestinationPointMeters(
                            lat = waypoint.lat,
                            lon = waypoint.lon,
                            bearing = (bearing + 180.0) % 360.0,
                            distanceMeters = radiusMeters
                        )
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

    fun calculateDestinationPointMeters(
        lat: Double,
        lon: Double,
        bearing: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val bearingRad = Math.toRadians(bearing)
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
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
