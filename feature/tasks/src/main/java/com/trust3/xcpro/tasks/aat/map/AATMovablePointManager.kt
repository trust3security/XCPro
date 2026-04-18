package com.trust3.xcpro.tasks.aat.map

import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATWaypoint

/**
 * AAT movable target-point facade. Geometry constraints and strategy policy are
 * split into focused helpers to keep this class stable and easy to review.
 */
class AATMovablePointManager {
    private companion object {
        const val METERS_PER_KILOMETER = 1000.0
    }

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
        val distanceMeters = AATMathUtils.calculateDistanceMeters(
            waypoint.lat,
            waypoint.lon,
            targetPoint.latitude,
            targetPoint.longitude
        )
        return distanceMeters <= waypoint.assignedArea.radiusMeters
    }

    fun getTargetPointOffsetMeters(waypoint: AATWaypoint): Double {
        return AATMathUtils.calculateDistanceMeters(
            waypoint.lat,
            waypoint.lon,
            waypoint.targetPoint.latitude,
            waypoint.targetPoint.longitude
        )
    }

    @Deprecated(
        message = "Use getTargetPointOffsetMeters for explicit units"
    )
    fun getTargetPointOffset(waypoint: AATWaypoint): Double =
        getTargetPointOffsetMeters(waypoint) / METERS_PER_KILOMETER

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
