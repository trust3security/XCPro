package com.example.xcpro.tasks.navigation

import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import javax.inject.Inject

class TaskPerformanceDistanceProjector @Inject constructor() {

    fun startReferenceDistanceMeters(
        taskSnapshot: TaskRuntimeSnapshot,
        acceptedStartFix: RacingNavigationFix
    ): Double? {
        if (taskSnapshot.taskType != TaskType.RACING || taskSnapshot.task.waypoints.size < 2) {
            return null
        }

        val remainingWaypoints = taskSnapshot.task.projectBoundaryAwareRemainingWaypoints(
            RacingNavigationState(
                status = RacingNavigationStatus.STARTED,
                currentLegIndex = minOf(1, taskSnapshot.task.waypoints.lastIndex),
                lastFix = acceptedStartFix
            )
        )
        return routeDistanceMeters(
            startLat = acceptedStartFix.lat,
            startLon = acceptedStartFix.lon,
            remainingWaypoints = remainingWaypoints
        )
    }

    fun remainingDistanceMeters(
        route: NavigationRouteSnapshot,
        currentLat: Double,
        currentLon: Double
    ): Double? {
        if (!route.valid) {
            return null
        }
        return routeDistanceMeters(
            startLat = currentLat,
            startLon = currentLon,
            remainingWaypoints = route.remainingWaypoints
        )
    }

    private fun routeDistanceMeters(
        startLat: Double,
        startLon: Double,
        remainingWaypoints: List<NavigationRoutePoint>
    ): Double? {
        if (remainingWaypoints.isEmpty()) {
            return 0.0
        }

        var totalMeters = 0.0
        var previousLat = startLat
        var previousLon = startLon
        remainingWaypoints.forEach { point ->
            val legDistanceMeters = RacingGeometryUtils.haversineDistanceMeters(
                previousLat,
                previousLon,
                point.lat,
                point.lon
            )
            if (!legDistanceMeters.isFinite() || legDistanceMeters < 0.0) {
                return null
            }
            totalMeters += legDistanceMeters
            previousLat = point.lat
            previousLon = point.lon
        }
        return totalMeters
    }
}
