package com.example.xcpro.adsb

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class BBox(
    val lamin: Double,
    val lomin: Double,
    val lamax: Double,
    val lomax: Double
)

object AdsbGeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val KM_PER_LAT_DEGREE = 111.0

    fun computeBbox(centerLat: Double, centerLon: Double, radiusKm: Double): BBox {
        val latDelta = radiusKm / KM_PER_LAT_DEGREE
        val cosLat = cos(Math.toRadians(centerLat)).let { value ->
            if (value == 0.0) 1e-6 else kotlin.math.abs(value)
        }
        val lonDelta = radiusKm / (KM_PER_LAT_DEGREE * cosLat)

        val lamin = (centerLat - latDelta).coerceAtLeast(-90.0)
        val lamax = (centerLat + latDelta).coerceAtMost(90.0)

        val rawLomin = centerLon - lonDelta
        val rawLomax = centerLon + lonDelta

        val (lomin, lomax) = if (rawLomin < -180.0 || rawLomax > 180.0) {
            // OpenSky bbox does not support anti-meridian split in one request.
            -180.0 to 180.0
        } else {
            rawLomin to rawLomax
        }

        return BBox(
            lamin = lamin,
            lomin = lomin.coerceIn(-180.0, 180.0),
            lamax = lamax,
            lomax = lomax.coerceIn(-180.0, 180.0)
        )
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radLat1 = Math.toRadians(lat1)
        val radLon1 = Math.toRadians(lon1)
        val radLat2 = Math.toRadians(lat2)
        val radLon2 = Math.toRadians(lon2)
        val dLat = radLat2 - radLat1
        val dLon = radLon2 - radLon1
        val h = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(radLat1) * cos(radLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
        return EARTH_RADIUS_M * c
    }

    fun bearingDegrees(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val fromLatRad = Math.toRadians(fromLat)
        val toLatRad = Math.toRadians(toLat)
        val deltaLonRad = Math.toRadians(toLon - fromLon)

        val y = sin(deltaLonRad) * cos(toLatRad)
        val x = cos(fromLatRad) * sin(toLatRad) -
            sin(fromLatRad) * cos(toLatRad) * cos(deltaLonRad)
        val raw = Math.toDegrees(atan2(y, x))
        return (raw + 360.0) % 360.0
    }

    fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        if (!lat.isFinite() || !lon.isFinite()) return false
        if (kotlin.math.abs(lat) > 90.0) return false
        if (kotlin.math.abs(lon) > 180.0) return false
        return true
    }
}

