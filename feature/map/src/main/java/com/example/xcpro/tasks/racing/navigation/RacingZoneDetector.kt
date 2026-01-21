package com.example.xcpro.tasks.racing.navigation

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import kotlin.math.abs

internal data class NavPoint(val lat: Double, val lon: Double)

internal class RacingZoneDetector {

    companion object {
        private const val START_SECTOR_ANGLE_DEGREES = 90.0
        private const val FAI_SECTOR_ANGLE_DEGREES = 90.0
        private const val LINE_SECTOR_HALF_ANGLE_DEGREES = 90.0
    }

    fun isInsideStartZone(position: NavPoint, start: RacingWaypoint, next: RacingWaypoint?): Boolean {
        return when (start.startPointType) {
            RacingStartPointType.START_LINE -> {
                val sectorBearing = startLineSectorBearing(start, next) ?: return false
                val radiusKm = lineSectorRadiusKm(start) ?: return false
                isInsideLineSector(position, start, sectorBearing, radiusKm)
            }
            RacingStartPointType.START_CYLINDER -> {
                distanceKm(position, start) <= start.gateWidth
            }
            RacingStartPointType.FAI_START_SECTOR -> {
                if (next == null) return false
                val bearingToNext = RacingGeometryUtils.calculateBearing(start.lat, start.lon, next.lat, next.lon)
                isWithinSector(position, start, bearingToNext, START_SECTOR_ANGLE_DEGREES / 2.0, start.gateWidth)
            }
        }
    }

    fun isInsideTurnZone(position: NavPoint, turn: RacingWaypoint, previous: RacingWaypoint?, next: RacingWaypoint?): Boolean {
        return when (turn.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                distanceKm(position, turn) <= turn.gateWidth
            }
            RacingTurnPointType.FAI_QUADRANT -> {
                if (previous == null || next == null) return false
                val sectorBisector = calculateFAISectorBisector(turn, previous, next)
                isWithinSector(position, turn, sectorBisector, FAI_SECTOR_ANGLE_DEGREES / 2.0, turn.faiQuadrantOuterRadius)
            }
            RacingTurnPointType.KEYHOLE -> {
                val distanceKm = distanceKm(position, turn)
                if (distanceKm <= turn.keyholeInnerRadius) {
                    true
                } else {
                    if (previous == null || next == null) return false
                    val sectorBisector = calculateFAISectorBisector(turn, previous, next)
                    val halfAngle = turn.normalizedKeyholeAngle / 2.0
                    isWithinSector(position, turn, sectorBisector, halfAngle, turn.gateWidth)
                }
            }
        }
    }

    fun isInsideFinishZone(position: NavPoint, finish: RacingWaypoint, previous: RacingWaypoint?): Boolean {
        return when (finish.finishPointType) {
            RacingFinishPointType.FINISH_LINE -> {
                val sectorBearing = finishLineSectorBearing(finish, previous) ?: return false
                val radiusKm = lineSectorRadiusKm(finish) ?: return false
                isInsideLineSector(position, finish, sectorBearing, radiusKm)
            }
            RacingFinishPointType.FINISH_CYLINDER -> {
                distanceKm(position, finish) <= finish.gateWidth
            }
        }
    }

    fun isLineTransitionAllowed(previous: NavPoint, current: NavPoint, center: RacingWaypoint): Boolean {
        val radiusKm = lineSectorRadiusKm(center) ?: return false
        return isWithinCircle(previous, center, radiusKm) &&
            isWithinCircle(current, center, radiusKm)
    }

    private fun distanceKm(position: NavPoint, waypoint: RacingWaypoint): Double {
        return RacingGeometryUtils.haversineDistance(
            position.lat,
            position.lon,
            waypoint.lat,
            waypoint.lon
        )
    }

    private fun isWithinSector(
        position: NavPoint,
        center: RacingWaypoint,
        sectorCenterBearing: Double,
        halfAngleDeg: Double,
        radiusKm: Double?
    ): Boolean {
        val distanceKm = distanceKm(position, center)
        if (radiusKm != null && distanceKm > radiusKm) {
            return false
        }

        val bearingToPosition = RacingGeometryUtils.calculateBearing(
            center.lat,
            center.lon,
            position.lat,
            position.lon
        )

        val angleDiff = angleDifferenceDegrees(bearingToPosition, sectorCenterBearing)
        return angleDiff <= halfAngleDeg
    }

    private fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint
    ): Double {
        val inboundBearing = RacingGeometryUtils.calculateBearing(
            previousWaypoint.lat,
            previousWaypoint.lon,
            waypoint.lat,
            waypoint.lon
        )
        val outboundBearing = RacingGeometryUtils.calculateBearing(
            waypoint.lat,
            waypoint.lon,
            nextWaypoint.lat,
            nextWaypoint.lon
        )

        val trackBisector = RacingGeometryUtils.calculateAngleBisector(inboundBearing, outboundBearing)
        val turnDirection = RacingGeometryUtils.calculateTurnDirection(inboundBearing, outboundBearing)
        return if (turnDirection > 0) {
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            (trackBisector + 90.0) % 360.0
        }
    }

    private fun angleDifferenceDegrees(a: Double, b: Double): Double {
        val diff = (a - b + 540.0) % 360.0 - 180.0
        return abs(diff)
    }

    private fun startLineSectorBearing(start: RacingWaypoint, next: RacingWaypoint?): Double? {
        if (next == null) return null
        val bearing = RacingGeometryUtils.calculateBearing(start.lat, start.lon, next.lat, next.lon)
        return (bearing + 180.0) % 360.0
    }

    private fun finishLineSectorBearing(finish: RacingWaypoint, previous: RacingWaypoint?): Double? {
        if (previous == null) return null
        return RacingGeometryUtils.calculateBearing(previous.lat, previous.lon, finish.lat, finish.lon)
    }

    private fun lineSectorRadiusKm(waypoint: RacingWaypoint): Double? {
        if (waypoint.gateWidth <= 0.0) return null
        return waypoint.gateWidth / 2.0
    }

    private fun isInsideLineSector(
        position: NavPoint,
        center: RacingWaypoint,
        sectorBearing: Double,
        radiusKm: Double
    ): Boolean {
        return isWithinSector(position, center, sectorBearing, LINE_SECTOR_HALF_ANGLE_DEGREES, radiusKm)
    }

    private fun isWithinCircle(position: NavPoint, center: RacingWaypoint, radiusKm: Double): Boolean {
        return distanceKm(position, center) <= radiusKm
    }
}
