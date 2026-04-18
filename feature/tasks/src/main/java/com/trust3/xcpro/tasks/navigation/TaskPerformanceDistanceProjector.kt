package com.trust3.xcpro.tasks.navigation

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.navigation.RacingCreditedBoundaryHit
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationState
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationStatus
import javax.inject.Inject

class TaskPerformanceDistanceProjector @Inject constructor() {

    fun startReferenceDistanceMeters(
        taskSnapshot: TaskRuntimeSnapshot,
        creditedStart: RacingCreditedBoundaryHit
    ): Double? {
        if (taskSnapshot.taskType != TaskType.RACING || taskSnapshot.task.waypoints.size < 2) {
            return null
        }
        val crossingPoint = creditedStart.crossingEvidence.crossingPoint

        val remainingWaypoints = taskSnapshot.task.projectBoundaryAwareRemainingWaypoints(
            RacingNavigationState(
                status = RacingNavigationStatus.STARTED,
                currentLegIndex = minOf(1, taskSnapshot.task.waypoints.lastIndex),
                lastFix = com.trust3.xcpro.tasks.racing.navigation.RacingNavigationFix(
                    lat = crossingPoint.lat,
                    lon = crossingPoint.lon,
                    timestampMillis = creditedStart.timestampMillis
                )
            )
        )
        return routeDistanceMeters(
            startLat = crossingPoint.lat,
            startLon = crossingPoint.lon,
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
