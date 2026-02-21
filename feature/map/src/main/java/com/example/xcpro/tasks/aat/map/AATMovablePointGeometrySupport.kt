package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal object AATMovablePointGeometrySupport {

    fun isPointInsideArea(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        return when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE, AATAreaShape.LINE -> {
                val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                val distance = AATMathUtils.calculateDistanceKm(
                    waypoint.lat,
                    waypoint.lon,
                    point.latitude,
                    point.longitude
                )
                distance <= radiusKm
            }

            AATAreaShape.SECTOR -> isPointInSectorOrKeyhole(waypoint, point)
        }
    }

    fun moveTargetPoint(
        waypoint: AATWaypoint,
        newLat: Double,
        newLon: Double
    ): AATWaypoint {
        val center = AATLatLng(waypoint.lat, waypoint.lon)
        val candidate = AATLatLng(newLat, newLon)

        val clamped = when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE, AATAreaShape.LINE -> clampToCircle(
                center = center,
                point = candidate,
                radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
            )

            AATAreaShape.SECTOR -> clampToSectorOrKeyhole(waypoint, candidate)
        }

        return waypoint.copy(
            targetPoint = clamped,
            isTargetPointCustomized = true
        )
    }

    private fun clampToCircle(center: AATLatLng, point: AATLatLng, radiusKm: Double): AATLatLng {
        val distance = AATMathUtils.calculateDistanceKm(
            center.latitude,
            center.longitude,
            point.latitude,
            point.longitude
        )

        if (radiusKm <= 0.0 || distance <= radiusKm) return point
        val bearing = AATMathUtils.calculateBearing(center, point)
        return calculateDestination(center.latitude, center.longitude, bearing, radiusKm)
    }

    private fun clampToSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): AATLatLng {
        val center = AATLatLng(waypoint.lat, waypoint.lon)
        val distanceKm = AATMathUtils.calculateDistanceKm(
            center.latitude,
            center.longitude,
            point.latitude,
            point.longitude
        )

        val innerRadiusKm = max(0.0, waypoint.assignedArea.innerRadiusMeters / 1000.0)
        val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

        if (innerRadiusKm > 0.0 && distanceKm <= innerRadiusKm) {
            return point
        }

        val bearing = AATMathUtils.calculateBearing(center, point)
        val angleInside = isAngleInSector(
            angle = bearing,
            startAngle = waypoint.assignedArea.startAngleDegrees,
            endAngle = waypoint.assignedArea.endAngleDegrees
        )

        val clampedBearing = if (angleInside) {
            bearing
        } else {
            clampAngleToSector(
                angle = bearing,
                startAngle = waypoint.assignedArea.startAngleDegrees,
                endAngle = waypoint.assignedArea.endAngleDegrees
            )
        }

        val clampedDistance = distanceKm.coerceIn(innerRadiusKm, outerRadiusKm)
        return calculateDestination(center.latitude, center.longitude, clampedBearing, clampedDistance)
    }

    private fun clampAngleToSector(angle: Double, startAngle: Double, endAngle: Double): Double {
        val normalizedAngle = normalizeAngle(angle)
        val normalizedStart = normalizeAngle(startAngle)
        val normalizedEnd = normalizeAngle(endAngle)

        if (isAngleInSector(normalizedAngle, normalizedStart, normalizedEnd)) {
            return normalizedAngle
        }

        val distanceToStart = angularDistance(normalizedAngle, normalizedStart)
        val distanceToEnd = angularDistance(normalizedAngle, normalizedEnd)
        return if (distanceToStart <= distanceToEnd) normalizedStart else normalizedEnd
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val diff = abs(a - b)
        return min(diff, 360.0 - diff)
    }

    private fun calculateDestination(lat: Double, lon: Double, bearing: Double, distanceKm: Double): AATLatLng {
        val earthRadiusKm = 6371.0
        val bearingRad = Math.toRadians(bearing)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val angularDistance = distanceKm / earthRadiusKm

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

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    private fun isPointInSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat,
            waypoint.lon,
            point.latitude,
            point.longitude
        )

        val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
        val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

        if (innerRadiusKm > 0.0 && distance <= innerRadiusKm) {
            return true
        }

        if (distance > outerRadiusKm) {
            return false
        }

        val bearingToPoint = AATMathUtils.calculateBearing(
            AATLatLng(waypoint.lat, waypoint.lon),
            point
        )
        return isAngleInSector(
            angle = bearingToPoint,
            startAngle = waypoint.assignedArea.startAngleDegrees,
            endAngle = waypoint.assignedArea.endAngleDegrees
        )
    }

    private fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = normalizeAngle(angle)
        val normalizedStart = normalizeAngle(startAngle)
        val normalizedEnd = normalizeAngle(endAngle)
        val angleTolerance = 5.0

        return if (normalizedEnd >= normalizedStart) {
            normalizedAngle >= normalizedStart - angleTolerance &&
                normalizedAngle <= normalizedEnd + angleTolerance
        } else {
            normalizedAngle >= normalizedStart - angleTolerance ||
                normalizedAngle <= normalizedEnd + angleTolerance
        }
    }
}
