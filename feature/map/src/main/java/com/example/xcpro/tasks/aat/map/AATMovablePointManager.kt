package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint

/**
 * AAT movable target-point facade. Geometry constraints and strategy policy are
 * split into focused helpers to keep this class stable and easy to review.
 */
class AATMovablePointManager {

    fun isPointInsideArea(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        return AATMovablePointGeometrySupport.isPointInsideArea(waypoint, point)
    }

    fun moveTargetPoint(
        waypoint: AATWaypoint,
        newLat: Double,
        newLon: Double
    ): AATWaypoint {
        return AATMovablePointGeometrySupport.moveTargetPoint(waypoint, newLat, newLon)
    }

    fun calculateOptimalPosition(
        waypoint: AATWaypoint,
        windDirection: Double,
        windSpeed: Double,
        nextWaypoint: AATWaypoint? = null
    ): AATLatLng {
        return AATMovablePointStrategySupport.calculateOptimalPosition(
            waypoint = waypoint,
            windDirection = windDirection,
            windSpeed = windSpeed,
            nextWaypoint = nextWaypoint
        )
    }

    fun isTargetPointValid(waypoint: AATWaypoint, targetPoint: AATLatLng): Boolean {
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat,
            waypoint.lon,
            targetPoint.latitude,
            targetPoint.longitude
        )
        return distance <= (waypoint.assignedArea.radiusMeters / 1000.0)
    }

    fun getTargetPointOffset(waypoint: AATWaypoint): Double {
        return AATMathUtils.calculateDistanceKm(
            waypoint.lat,
            waypoint.lon,
            waypoint.targetPoint.latitude,
            waypoint.targetPoint.longitude
        )
    }

    fun resetToCenter(waypoint: AATWaypoint): AATWaypoint {
        return waypoint.copy(
            targetPoint = AATLatLng(waypoint.lat, waypoint.lon),
            isTargetPointCustomized = false
        )
    }

    fun getRecommendedPosition(
        waypoint: AATWaypoint,
        flightPhase: AATFlightPhase,
        windDirection: Double,
        windSpeed: Double
    ): AATLatLng {
        return AATMovablePointStrategySupport.getRecommendedPosition(
            waypoint = waypoint,
            flightPhase = flightPhase,
            windDirection = windDirection,
            windSpeed = windSpeed
        )
    }

    @Suppress("unused")
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

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }
}

/**
 * Flight phases for strategic positioning.
 */
enum class AATFlightPhase {
    EARLY_TASK,
    MID_TASK,
    LATE_TASK
}
