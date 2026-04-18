package com.trust3.xcpro.tasks.aat.calculations

import com.trust3.xcpro.tasks.aat.models.AATLatLng
import kotlin.math.*

/** Bearing between two lat/lon points (degrees 0-360). */
internal fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLon = Math.toRadians(lon2 - lon1)
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)

    val y = sin(dLon) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

    val bearing = atan2(y, x)
    return (Math.toDegrees(bearing) + 360) % 360
}

/** Angle bisector used for optimal turnpoint positioning (degrees). */
internal fun calculateBisectorBearing(
    prevLat: Double, prevLon: Double,
    currentLat: Double, currentLon: Double,
    nextLat: Double, nextLon: Double
): Double {
    val bearing1 = calculateBearing(currentLat, currentLon, prevLat, prevLon)
    val bearing2 = calculateBearing(currentLat, currentLon, nextLat, nextLon)
    val avgBearing = (bearing1 + bearing2) / 2.0
    return (avgBearing + 90) % 360 // Perpendicular for maximum distance
}

private const val EARTH_RADIUS_KM = 6371.0
private const val METERS_PER_KILOMETER = 1000.0
private const val EARTH_RADIUS_METERS = EARTH_RADIUS_KM * METERS_PER_KILOMETER

/** Destination point given start, bearing, distance (meters). */
internal fun calculateDestinationMeters(lat: Double, lon: Double, bearing: Double, distanceMeters: Double): AATLatLng {
    val bearingRad = Math.toRadians(bearing)
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val angularDistance = distanceMeters / EARTH_RADIUS_METERS

    val destLatRad = asin(
        sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )

    val destLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(destLatRad)
    )

    return AATLatLng(
        latitude = Math.toDegrees(destLatRad),
        longitude = Math.toDegrees(destLonRad)
    )
}

/** Haversine distance (meters). */
internal fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}
