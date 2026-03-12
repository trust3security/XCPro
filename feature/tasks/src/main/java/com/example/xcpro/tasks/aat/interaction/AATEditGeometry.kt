package com.example.xcpro.tasks.aat.interaction

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometry helpers for AAT edit mode hit testing and overlay generation.
 *
 * AI-NOTE: Keep these pure so rendering and interaction can reuse them without MapLibre deps.
 */
internal object AATEditGeometry {

    private const val EARTH_RADIUS_METERS = 6371000.0

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = (angle + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        return if (normalizedEnd >= normalizedStart) {
            // Normal case: sector doesn't cross 0 deg.
            normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd
        } else {
            // Sector crosses 0 deg (e.g., 350 deg to 10 deg).
            normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
        }
    }

    fun generateCircleCoordinatesMeters(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        points: Int = 64
    ): List<List<Double>> {
        val coords = mutableListOf<List<Double>>()

        for (i in 0 until points) {
            val angle = 2 * PI * i / points
            val lat = centerLat + (radiusMeters / EARTH_RADIUS_METERS) * (180 / PI) * cos(angle)
            val lon = centerLon + (radiusMeters / EARTH_RADIUS_METERS) * (180 / PI) * sin(angle) /
                cos(centerLat * PI / 180)
            coords.add(listOf(lon, lat)) // GeoJSON format: [longitude, latitude]
        }

        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    fun generateSectorCoordinatesMeters(
        centerLat: Double,
        centerLon: Double,
        innerRadiusMeters: Double,
        outerRadiusMeters: Double,
        startBearingDeg: Double,
        endBearingDeg: Double
    ): List<List<Double>> {
        val coords = mutableListOf<List<Double>>()

        if (innerRadiusMeters > 0.0) {
            // Keyhole: cylinder plus sector extension.
            val cylinderPoints = 72
            val sectorPoints = 45

            // Draw cylinder outline (the part NOT covered by sector).
            val startDrawAngle = endBearingDeg
            val endDrawAngle = startBearingDeg + 360.0

            for (i in 0..cylinderPoints) {
                val angleProgress = i.toDouble() / cylinderPoints
                val currentAngle = startDrawAngle + angleProgress * (endDrawAngle - startDrawAngle)
                val normalizedAngle = currentAngle % 360.0

                val point = calculateDestinationPointMeters(centerLat, centerLon, normalizedAngle, innerRadiusMeters)
                coords.add(listOf(point.second, point.first))
            }

            // Connect to sector outer boundary at start angle.
            val sectorOuterStart = calculateDestinationPointMeters(centerLat, centerLon, startBearingDeg, outerRadiusMeters)
            coords.add(listOf(sectorOuterStart.second, sectorOuterStart.first))

            // Draw the sector outer arc.
            for (i in 1 until sectorPoints) {
                val angleProgress = i.toDouble() / sectorPoints
                val angle = startBearingDeg + angleProgress * (endBearingDeg - startBearingDeg)
                val point = calculateDestinationPointMeters(centerLat, centerLon, angle, outerRadiusMeters)
                coords.add(listOf(point.second, point.first))
            }

            // Connect to sector outer boundary at end angle.
            val sectorOuterEnd = calculateDestinationPointMeters(centerLat, centerLon, endBearingDeg, outerRadiusMeters)
            coords.add(listOf(sectorOuterEnd.second, sectorOuterEnd.first))

            // Connect back to cylinder edge at sector end angle (closes the keyhole).
            val cylinderSectorEnd = calculateDestinationPointMeters(centerLat, centerLon, endBearingDeg, innerRadiusMeters)
            coords.add(listOf(cylinderSectorEnd.second, cylinderSectorEnd.first))
        } else {
            // Sector: no inner radius, standard sector from center.
            val numPoints = 32

            val sectorSpan = if (endBearingDeg >= startBearingDeg) {
                endBearingDeg - startBearingDeg
            } else {
                360.0 - startBearingDeg + endBearingDeg
            }

            // Start from center.
            coords.add(listOf(centerLon, centerLat))

            // Generate outer arc.
            for (i in 0..numPoints) {
                val fraction = i.toDouble() / numPoints
                val bearing = if (endBearingDeg >= startBearingDeg) {
                    startBearingDeg + fraction * (endBearingDeg - startBearingDeg)
                } else {
                    (startBearingDeg + fraction * sectorSpan) % 360.0
                }
                val point = calculateDestinationPointMeters(centerLat, centerLon, bearing, outerRadiusMeters)
                coords.add(listOf(point.second, point.first))
            }

            // Connect back to center.
            coords.add(listOf(centerLon, centerLat))
        }

        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }

    fun calculateDestinationPointMeters(
        centerLat: Double,
        centerLon: Double,
        bearingDeg: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        val latRad = Math.toRadians(centerLat)
        val lonRad = Math.toRadians(centerLon)
        val bearingRad = Math.toRadians(bearingDeg)

        val newLatRad = asin(
            sin(latRad) * cos(distanceMeters / EARTH_RADIUS_METERS) +
                cos(latRad) * sin(distanceMeters / EARTH_RADIUS_METERS) * cos(bearingRad)
        )

        val newLonRad = lonRad + atan2(
            sin(bearingRad) * sin(distanceMeters / EARTH_RADIUS_METERS) * cos(latRad),
            cos(distanceMeters / EARTH_RADIUS_METERS) - sin(latRad) * sin(newLatRad)
        )

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLonRad))
    }

    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
