package com.example.xcpro.tasks.racing

import kotlin.math.*

/**
 * Racing-specific geometry utilities
 *
 * ZERO DEPENDENCIES on AAT or DHT modules - maintains complete separation
 * All geometry calculations are Racing-task specific
 */
object RacingGeometryUtils {

    /**
     * Generate circle coordinates for GeoJSON - SSOT Implementation
     * Uses calculateDestinationPoint() as single source of truth for all circle generation
     */
    fun generateCircleCoordinates(lat: Double, lon: Double, radiusMeters: Double): String {
        // CRASH FIX: Validate inputs before generating coordinates
        if (!lat.isFinite() || !lon.isFinite() || !radiusMeters.isFinite()) {
            println("❌ CIRCLE COORDS: Invalid inputs - lat:$lat, lon:$lon, radius:$radiusMeters")
            return "[]" // Return empty coordinate array
        }

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            println("❌ CIRCLE COORDS: Coordinates out of bounds - lat:$lat, lon:$lon")
            return "[]"
        }

        if (radiusMeters <= 0 || radiusMeters > 100000000) { // Max 100,000km
            println("❌ CIRCLE COORDS: Invalid radius: $radiusMeters meters")
            return "[]"
        }

        println("🔍 CIRCLE COORDS: Generating circle at ($lat, $lon) with radius ${radiusMeters}m")

        val points = mutableListOf<String>()

        // SSOT: Use calculateDestinationPoint() - single algorithm for ALL circle generation
        for (i in 0..360 step 10) {
            try {
                val bearing = i.toDouble()
                val (newLat, newLon) = calculateDestinationPoint(lat, lon, bearing, radiusMeters)

                // CRASH FIX: Validate calculated coordinates
                if (newLat.isFinite() && newLon.isFinite() &&
                    newLat >= -90.0 && newLat <= 90.0 &&
                    newLon >= -180.0 && newLon <= 180.0) {
                    points.add("[$newLon, $newLat]")
                } else {
                    println("⚠️ CIRCLE COORDS: Skipping invalid point at bearing $i° - lat:$newLat, lon:$newLon")
                }
            } catch (e: Exception) {
                println("❌ CIRCLE COORDS: Exception at bearing $i°: ${e.message}")
            }
        }

        if (points.isEmpty()) {
            println("❌ CIRCLE COORDS: No valid points generated for circle")
            return "[]"
        }

        println("✅ CIRCLE COORDS: Generated ${points.size} valid points for circle")
        return points.joinToString(", ")
    }

    /**
     * Generate circle coordinates as array - SSOT Implementation for GSON serialization
     * Uses calculateDestinationPoint() as single source of truth
     *
     * @param numPoints Number of points to generate (default 64 for smooth circles)
     * @param reverse If true, generate coordinates in reverse order (for GeoJSON polygon holes)
     * @return List of [lon, lat] coordinate pairs
     */
    fun generateCircleCoordinatesArray(
        lat: Double,
        lon: Double,
        radiusMeters: Double,
        numPoints: Int = 64,
        reverse: Boolean = false
    ): List<List<Double>> {
        // Validate inputs
        if (!lat.isFinite() || !lon.isFinite() || !radiusMeters.isFinite()) {
            println("❌ CIRCLE ARRAY: Invalid inputs - lat:$lat, lon:$lon, radius:$radiusMeters")
            return emptyList()
        }

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            println("❌ CIRCLE ARRAY: Coordinates out of bounds - lat:$lat, lon:$lon")
            return emptyList()
        }

        if (radiusMeters <= 0 || radiusMeters > 100000000) {
            println("❌ CIRCLE ARRAY: Invalid radius: $radiusMeters meters")
            return emptyList()
        }

        val coordinates = mutableListOf<List<Double>>()

        // SSOT: Use calculateDestinationPoint() - single algorithm for ALL circle generation
        val range = if (reverse) (0..numPoints).reversed() else (0..numPoints)
        for (i in range) {
            val bearing = 360.0 * i / numPoints
            val (newLat, newLon) = calculateDestinationPoint(lat, lon, bearing, radiusMeters)

            // Validate and add coordinate
            if (newLat.isFinite() && newLon.isFinite() &&
                newLat >= -90.0 && newLat <= 90.0 &&
                newLon >= -180.0 && newLon <= 180.0) {
                coordinates.add(listOf(newLon, newLat))
            }
        }

        return coordinates
    }

    /**
     * Generate sector geometry for Racing tasks
     */
    fun generateSectorGeometry(
        centerLat: Double,
        centerLon: Double,
        centerBearing: Double,
        angleDegrees: Double,
        radiusMeters: Double,
        title: String,
        type: String
    ): String {
        val points = mutableListOf<String>()
        val earthRadius = 6371000.0

        // Add center point
        points.add("[$centerLon, $centerLat]")

        // Add arc points
        val startBearing = centerBearing - angleDegrees / 2
        val endBearing = centerBearing + angleDegrees / 2

        for (angle in startBearing.toInt()..endBearing.toInt() step 5) {
            val bearing = Math.toRadians(angle.toDouble())
            val angularDistance = radiusMeters / earthRadius

            val latRad = Math.toRadians(centerLat)
            val lonRad = Math.toRadians(centerLon)

            val newLatRad = asin(sin(latRad) * cos(angularDistance) + cos(latRad) * sin(angularDistance) * cos(bearing))
            val newLonRad = lonRad + atan2(sin(bearing) * sin(angularDistance) * cos(latRad), cos(angularDistance) - sin(latRad) * sin(newLatRad))

            val newLat = Math.toDegrees(newLatRad)
            val newLon = Math.toDegrees(newLonRad)

            points.add("[$newLon, $newLat]")
        }

        // Close the polygon by returning to center
        points.add("[$centerLon, $centerLat]")

        return """
        {
            "type": "Feature",
            "geometry": {
                "type": "Polygon",
                "coordinates": [[${points.joinToString(", ")}]]
            },
            "properties": {
                "type": "$type",
                "title": "$title",
                "angle": $angleDegrees,
                "radius": $radiusMeters
            }
        }
        """.trimIndent()
    }

    /**
     * Calculate destination point from bearing and distance
     */
    fun calculateDestinationPoint(lat: Double, lon: Double, bearing: Double, distance: Double): Pair<Double, Double> {
        val earthRadius = 6371000.0 // Earth's radius in meters
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

    /**
     * Calculate haversine distance between two points
     */
    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Calculate bisector bearing for symmetric quadrants (Racing-specific)
     */
    fun calculateBisectorBearing(
        prevLat: Double, prevLon: Double,
        currentLat: Double, currentLon: Double,
        nextLat: Double, nextLon: Double
    ): Double {
        val bearing1 = calculateBearing(prevLat, prevLon, currentLat, currentLon)
        val bearing2 = calculateBearing(currentLat, currentLon, nextLat, nextLon)
        return (bearing1 + bearing2) / 2
    }

    /**
     * Calculate angle bisector between two bearings
     * Handles proper wrapping at 0°/360° boundary
     */
    fun calculateAngleBisector(bearing1: Double, bearing2: Double): Double {
        val b1 = (bearing1 + 360.0) % 360.0
        val b2 = (bearing2 + 360.0) % 360.0
        val diff = (b2 - b1 + 360.0) % 360.0
        return if (diff <= 180.0) {
            (b1 + diff / 2.0) % 360.0
        } else {
            (b1 - (360.0 - diff) / 2.0 + 360.0) % 360.0
        }
    }

    /**
     * Calculate turn direction between two bearings
     * @return Positive for right turn, negative for left turn
     */
    fun calculateTurnDirection(inboundBearing: Double, outboundBearing: Double): Double {
        val angleDifference = (outboundBearing - inboundBearing + 360.0) % 360.0
        return if (angleDifference <= 180.0) angleDifference else angleDifference - 360.0
    }

    /**
     * Calculate optimal crossing point on a start/finish line
     * Returns the optimal lat/lon on the line perpendicular to the leg
     */
    fun calculateOptimalLineCrossingPoint(
        lineLat: Double,
        lineLon: Double,
        targetLat: Double,
        targetLon: Double,
        lineWidth: Double
    ): Pair<Double, Double> {
        // Calculate bearing from line center to target
        val bearing = calculateBearing(lineLat, lineLon, targetLat, targetLon)

        // Line is perpendicular to the bearing
        val lineBearing = (bearing + 90) % 360

        // Calculate the two endpoints of the line
        val halfWidth = lineWidth / 2.0
        val endpoint1 = calculateDestinationPoint(lineLat, lineLon, lineBearing, halfWidth)
        val endpoint2 = calculateDestinationPoint(lineLat, lineLon, (lineBearing + 180) % 360, halfWidth)

        // Find which endpoint is closer to the target
        val dist1 = haversineDistance(endpoint1.first, endpoint1.second, targetLat, targetLon)
        val dist2 = haversineDistance(endpoint2.first, endpoint2.second, targetLat, targetLon)

        return if (dist1 < dist2) endpoint1 else endpoint2
    }
}