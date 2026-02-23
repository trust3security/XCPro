package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object AATMovablePointGeometrySupport {

    fun isPointInsideArea(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        return when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE, AATAreaShape.LINE -> {
                val radiusMeters = waypoint.assignedArea.radiusMeters
                val distanceMeters = AATMathUtils.calculateDistanceMeters(
                    waypoint.lat,
                    waypoint.lon,
                    point.latitude,
                    point.longitude
                )
                distanceMeters <= radiusMeters
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
                radiusMeters = waypoint.assignedArea.radiusMeters
            )

            AATAreaShape.SECTOR -> clampToSectorOrKeyhole(waypoint, candidate)
        }

        return waypoint.copy(
            targetPoint = clamped,
            isTargetPointCustomized = true
        )
    }

    private fun clampToCircle(center: AATLatLng, point: AATLatLng, radiusMeters: Double): AATLatLng {
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            center.latitude,
            center.longitude,
            point.latitude,
            point.longitude
        )

        if (radiusMeters <= 0.0 || distanceMeters <= radiusMeters) return point
        val bearing = AATMathUtils.calculateBearing(center, point)
        return calculateDestination(center.latitude, center.longitude, bearing, radiusMeters)
    }

    private fun clampToSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): AATLatLng {
        val center = AATLatLng(waypoint.lat, waypoint.lon)
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            center.latitude,
            center.longitude,
            point.latitude,
            point.longitude
        )

        val innerRadiusMeters = max(0.0, waypoint.assignedArea.innerRadiusMeters)
        val outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters

        if (innerRadiusMeters > 0.0 && distanceMeters <= innerRadiusMeters) {
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

        val clampedDistanceMeters = distanceMeters.coerceIn(innerRadiusMeters, outerRadiusMeters)
        return calculateDestination(center.latitude, center.longitude, clampedBearing, clampedDistanceMeters)
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

    private fun calculateDestination(lat: Double, lon: Double, bearing: Double, distanceMeters: Double): AATLatLng {
        return AATMathUtils.calculatePointAtBearingMeters(
            from = AATLatLng(latitude = lat, longitude = lon),
            bearing = bearing,
            distanceMeters = distanceMeters
        )
    }

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    private fun isPointInSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            waypoint.lat,
            waypoint.lon,
            point.latitude,
            point.longitude
        )

        val innerRadiusMeters = waypoint.assignedArea.innerRadiusMeters
        val outerRadiusMeters = waypoint.assignedArea.outerRadiusMeters

        if (innerRadiusMeters > 0.0 && distanceMeters <= innerRadiusMeters) {
            return true
        }

        if (distanceMeters > outerRadiusMeters) {
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
