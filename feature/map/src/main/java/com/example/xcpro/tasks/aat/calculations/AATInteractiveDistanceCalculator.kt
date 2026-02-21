package com.example.xcpro.tasks.aat.calculations
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole

internal class AATInteractiveDistanceCalculator {

    fun calculateInteractiveTaskDistance(waypoints: List<AATWaypoint>): AATInteractiveTaskDistance {
        if (waypoints.isEmpty()) {
            return AATInteractiveTaskDistance(0.0, emptyList())
        }

        if (waypoints.size == 1) {
            return calculateSingleWaypointInteractiveTask(waypoints[0])
        }

        val segments = mutableListOf<AATInteractiveDistanceSegment>()
        var totalDistance = 0.0

        for (i in 0 until waypoints.size - 1) {
            val fromWaypoint = waypoints[i]
            val toWaypoint = waypoints[i + 1]

            val segment = calculateInteractiveSegmentDistance(
                fromWaypoint = fromWaypoint,
                toWaypoint = toWaypoint,
                fromIndex = i,
                toIndex = i + 1
            )

            segments.add(segment)
            totalDistance += segment.distance
        }

        return AATInteractiveTaskDistance(
            totalDistance = totalDistance,
            segments = segments,
            calculationTime = 0L
        )
    }

    fun calculateDistanceUpdate(
        waypoints: List<AATWaypoint>,
        changedWaypointIndex: Int,
        newTargetPoint: AATLatLng
    ): AATInteractiveTaskDistance {
        val updatedWaypoints = waypoints.toMutableList()
        updatedWaypoints[changedWaypointIndex] = updatedWaypoints[changedWaypointIndex].copy(
            targetPoint = newTargetPoint
        )
        return calculateInteractiveTaskDistance(updatedWaypoints)
    }

    fun optimizeTargetPointsForMaxDistance(waypoints: List<AATWaypoint>): List<AATWaypoint> {
        if (waypoints.size < 2) {
            return waypoints
        }

        val optimized = waypoints.toMutableList()
        for (i in waypoints.indices) {
            val waypoint = waypoints[i]
            val prevWaypoint = if (i > 0) waypoints[i - 1] else null
            val nextWaypoint = if (i < waypoints.size - 1) waypoints[i + 1] else null

            val optimalTarget = calculateOptimalTargetForMaxDistance(
                waypoint = waypoint,
                prevWaypoint = prevWaypoint,
                nextWaypoint = nextWaypoint
            )

            optimized[i] = waypoint.copy(targetPoint = optimalTarget)
        }
        return optimized
    }

    private fun calculateSingleWaypointInteractiveTask(waypoint: AATWaypoint): AATInteractiveTaskDistance {
        val distance = haversineDistance(
            waypoint.lat,
            waypoint.lon,
            waypoint.targetPoint.latitude,
            waypoint.targetPoint.longitude
        )

        val segment = AATInteractiveDistanceSegment(
            fromPoint = AATLatLng(waypoint.lat, waypoint.lon),
            toPoint = waypoint.targetPoint,
            distance = distance,
            segmentType = AATInteractiveSegmentType.CENTER_TO_TARGET,
            fromWaypointIndex = 0,
            toWaypointIndex = 0
        )

        return AATInteractiveTaskDistance(
            totalDistance = distance,
            segments = listOf(segment)
        )
    }

    private fun calculateInteractiveSegmentDistance(
        fromWaypoint: AATWaypoint,
        toWaypoint: AATWaypoint,
        fromIndex: Int,
        toIndex: Int
    ): AATInteractiveDistanceSegment {
        val fromPoint = fromWaypoint.targetPoint
        val toPoint = toWaypoint.targetPoint
        val distance = haversineDistance(
            fromPoint.latitude,
            fromPoint.longitude,
            toPoint.latitude,
            toPoint.longitude
        )

        val segmentType = when {
            fromWaypoint.role == AATWaypointRole.START && toWaypoint.role == AATWaypointRole.FINISH ->
                AATInteractiveSegmentType.START_TO_FINISH
            fromWaypoint.role == AATWaypointRole.START ->
                AATInteractiveSegmentType.START_TO_TURNPOINT
            toWaypoint.role == AATWaypointRole.FINISH ->
                AATInteractiveSegmentType.TURNPOINT_TO_FINISH
            else ->
                AATInteractiveSegmentType.TURNPOINT_TO_TURNPOINT
        }

        return AATInteractiveDistanceSegment(
            fromPoint = fromPoint,
            toPoint = toPoint,
            distance = distance,
            segmentType = segmentType,
            fromWaypointIndex = fromIndex,
            toWaypointIndex = toIndex
        )
    }

    private fun calculateOptimalTargetForMaxDistance(
        waypoint: AATWaypoint,
        prevWaypoint: AATWaypoint?,
        nextWaypoint: AATWaypoint?
    ): AATLatLng {
        val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

        return when {
            prevWaypoint == null && nextWaypoint != null -> {
                val bearing = calculateBearing(
                    nextWaypoint.lat,
                    nextWaypoint.lon,
                    waypoint.lat,
                    waypoint.lon
                )
                calculateDestination(waypoint.lat, waypoint.lon, bearing, areaRadiusKm * 0.8)
            }
            prevWaypoint != null && nextWaypoint == null -> {
                val bearing = calculateBearing(
                    prevWaypoint.lat,
                    prevWaypoint.lon,
                    waypoint.lat,
                    waypoint.lon
                )
                calculateDestination(waypoint.lat, waypoint.lon, bearing, areaRadiusKm * 0.8)
            }
            prevWaypoint != null && nextWaypoint != null -> {
                val bisectorBearing = calculateBisectorBearing(
                    prevWaypoint.lat,
                    prevWaypoint.lon,
                    waypoint.lat,
                    waypoint.lon,
                    nextWaypoint.lat,
                    nextWaypoint.lon
                )
                calculateDestination(waypoint.lat, waypoint.lon, bisectorBearing, areaRadiusKm * 0.8)
            }
            else -> AATLatLng(waypoint.lat, waypoint.lon)
        }
    }
}
