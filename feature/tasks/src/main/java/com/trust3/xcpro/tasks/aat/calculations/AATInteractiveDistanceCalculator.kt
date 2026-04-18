package com.trust3.xcpro.tasks.aat.calculations
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import com.trust3.xcpro.tasks.aat.models.AATWaypoint
import com.trust3.xcpro.tasks.aat.models.AATWaypointRole

internal class AATInteractiveDistanceCalculator {

    fun calculateInteractiveTaskDistance(waypoints: List<AATWaypoint>): AATInteractiveTaskDistance {
        if (waypoints.isEmpty()) {
            return AATInteractiveTaskDistance(0.0, emptyList())
        }

        if (waypoints.size == 1) {
            return calculateSingleWaypointInteractiveTask(waypoints[0])
        }

        val segments = mutableListOf<AATInteractiveDistanceSegment>()
        var totalDistanceMeters = 0.0

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
            totalDistanceMeters += segment.distanceMeters
        }

        return AATInteractiveTaskDistance(
            totalDistanceMeters = totalDistanceMeters,
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
        val distanceMeters = haversineDistanceMeters(
            waypoint.lat,
            waypoint.lon,
            waypoint.targetPoint.latitude,
            waypoint.targetPoint.longitude
        )

        val segment = AATInteractiveDistanceSegment(
            fromPoint = AATLatLng(waypoint.lat, waypoint.lon),
            toPoint = waypoint.targetPoint,
            distanceMeters = distanceMeters,
            segmentType = AATInteractiveSegmentType.CENTER_TO_TARGET,
            fromWaypointIndex = 0,
            toWaypointIndex = 0
        )

        return AATInteractiveTaskDistance(
            totalDistanceMeters = distanceMeters,
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
        val distanceMeters = haversineDistanceMeters(
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
            distanceMeters = distanceMeters,
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
        val areaRadiusMeters = waypoint.assignedArea.radiusMeters

        return when {
            prevWaypoint == null && nextWaypoint != null -> {
                val bearing = calculateBearing(
                    nextWaypoint.lat,
                    nextWaypoint.lon,
                    waypoint.lat,
                    waypoint.lon
                )
                calculateDestinationMeters(waypoint.lat, waypoint.lon, bearing, areaRadiusMeters * 0.8)
            }
            prevWaypoint != null && nextWaypoint == null -> {
                val bearing = calculateBearing(
                    prevWaypoint.lat,
                    prevWaypoint.lon,
                    waypoint.lat,
                    waypoint.lon
                )
                calculateDestinationMeters(waypoint.lat, waypoint.lon, bearing, areaRadiusMeters * 0.8)
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
                calculateDestinationMeters(waypoint.lat, waypoint.lon, bisectorBearing, areaRadiusMeters * 0.8)
            }
            else -> AATLatLng(waypoint.lat, waypoint.lon)
        }
    }
}
