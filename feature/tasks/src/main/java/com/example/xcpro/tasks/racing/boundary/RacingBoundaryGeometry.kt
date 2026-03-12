package com.example.xcpro.tasks.racing.boundary

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

object RacingBoundaryGeometry {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun toLocalMeters(center: RacingBoundaryPoint, point: RacingBoundaryPoint): Pair<Double, Double> {
        val lat0Rad = Math.toRadians(center.lat)
        val dLat = Math.toRadians(point.lat - center.lat)
        val dLon = Math.toRadians(point.lon - center.lon)
        val x = dLon * cos(lat0Rad) * EARTH_RADIUS_METERS
        val y = dLat * EARTH_RADIUS_METERS
        return x to y
    }

    fun fromLocalMeters(center: RacingBoundaryPoint, xMeters: Double, yMeters: Double): RacingBoundaryPoint {
        val lat0Rad = Math.toRadians(center.lat)
        val lat = center.lat + Math.toDegrees(yMeters / EARTH_RADIUS_METERS)
        val lon = center.lon + Math.toDegrees(xMeters / (EARTH_RADIUS_METERS * cos(lat0Rad)))
        return RacingBoundaryPoint(lat, lon)
    }

    fun bearingDegrees(center: RacingBoundaryPoint, point: RacingBoundaryPoint): Double {
        return RacingGeometryUtils.calculateBearing(center.lat, center.lon, point.lat, point.lon)
    }

    fun pointOnBearing(center: RacingBoundaryPoint, bearingDegrees: Double, distanceMeters: Double): RacingBoundaryPoint {
        val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
            center.lat,
            center.lon,
            bearingDegrees,
            distanceMeters
        )
        return RacingBoundaryPoint(lat, lon)
    }

    fun anchorsForBoundaryPoint(
        center: RacingBoundaryPoint,
        boundaryPoint: RacingBoundaryPoint,
        radiusMeters: Double,
        epsilonMeters: Double
    ): Pair<RacingBoundaryPoint, RacingBoundaryPoint> {
        val bearing = bearingDegrees(center, boundaryPoint)
        val insideRadius = max(0.0, radiusMeters - epsilonMeters)
        val inside = pointOnBearing(center, bearing, insideRadius)
        val outside = pointOnBearing(center, bearing, radiusMeters + epsilonMeters)
        return inside to outside
    }

    data class SegmentIntersection(
        val t: Double,
        val u: Double,
        val x: Double,
        val y: Double
    )

    fun segmentIntersection(
        p0: Pair<Double, Double>,
        p1: Pair<Double, Double>,
        q0: Pair<Double, Double>,
        q1: Pair<Double, Double>
    ): SegmentIntersection? {
        val r = (p1.first - p0.first) to (p1.second - p0.second)
        val s = (q1.first - q0.first) to (q1.second - q0.second)
        val denom = cross(r, s)
        if (abs(denom) < 1e-9) return null

        val qp = (q0.first - p0.first) to (q0.second - p0.second)
        val t = cross(qp, s) / denom
        val u = cross(qp, r) / denom
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null

        val x = p0.first + t * r.first
        val y = p0.second + t * r.second
        return SegmentIntersection(t = t, u = u, x = x, y = y)
    }

    private fun cross(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        return a.first * b.second - a.second * b.first
    }
}
