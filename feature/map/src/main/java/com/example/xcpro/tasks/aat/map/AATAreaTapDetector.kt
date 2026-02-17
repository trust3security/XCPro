package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal object AATAreaTapDetector {

    fun findTappedArea(
        waypoints: List<AATWaypoint>,
        lat: Double,
        lon: Double
    ): Pair<Int, AATWaypoint>? {
        waypoints.forEachIndexed { index, waypoint ->
            val distance = AATMathUtils.calculateDistanceKm(lat, lon, waypoint.lat, waypoint.lon)
            val isInArea = when (waypoint.assignedArea.shape) {
                AATAreaShape.CIRCLE -> {
                    val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                    distance <= radiusKm
                }

                AATAreaShape.SECTOR -> {
                    val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
                    val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

                    if (innerRadiusKm > 0.0) {
                        if (distance <= innerRadiusKm) {
                            true
                        } else if (distance <= outerRadiusKm) {
                            val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                            isAngleInSector(
                                bearing,
                                waypoint.assignedArea.startAngleDegrees,
                                waypoint.assignedArea.endAngleDegrees
                            )
                        } else {
                            false
                        }
                    } else if (distance <= outerRadiusKm) {
                        val bearing = calculateBearing(waypoint.lat, waypoint.lon, lat, lon)
                        isAngleInSector(
                            bearing,
                            waypoint.assignedArea.startAngleDegrees,
                            waypoint.assignedArea.endAngleDegrees
                        )
                    } else {
                        false
                    }
                }

                AATAreaShape.LINE -> {
                    val halfWidth = (waypoint.assignedArea.lineWidthMeters / 1000.0) / 2.0
                    distance <= halfWidth
                }
            }

            if (isInArea) {
                return Pair(index, waypoint)
            }
        }
        return null
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }

    private fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = (angle + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        return if (normalizedEnd >= normalizedStart) {
            normalizedAngle >= normalizedStart && normalizedAngle <= normalizedEnd
        } else {
            normalizedAngle >= normalizedStart || normalizedAngle <= normalizedEnd
        }
    }
}
