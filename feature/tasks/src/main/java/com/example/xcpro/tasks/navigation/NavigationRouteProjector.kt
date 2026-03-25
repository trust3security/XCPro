package com.example.xcpro.tasks.navigation

import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus

internal fun projectNavigationRoute(
    taskSnapshot: TaskRuntimeSnapshot,
    navigationState: RacingNavigationState
): NavigationRouteSnapshot {
    val task = taskSnapshot.task
    if (taskSnapshot.taskType != TaskType.RACING || task.waypoints.size < 2) {
        return NavigationRouteSnapshot()
    }

    val finishWaypoint = task.waypoints.last()
    val label = finishWaypoint.title.ifBlank { "Finish" }
    val remainingWaypoints = task.projectBoundaryAwareRemainingWaypoints(navigationState)

    return when (navigationState.status) {
        RacingNavigationStatus.PENDING_START -> NavigationRouteSnapshot(
            kind = NavigationRouteKind.TASK_FINISH,
            label = label,
            remainingWaypoints = remainingWaypoints,
            valid = false,
            invalidReason = NavigationRouteInvalidReason.PRESTART
        )

        RacingNavigationStatus.FINISHED -> NavigationRouteSnapshot(
            kind = NavigationRouteKind.TASK_FINISH,
            label = label,
            remainingWaypoints = emptyList(),
            valid = false,
            invalidReason = NavigationRouteInvalidReason.FINISHED
        )

        RacingNavigationStatus.INVALIDATED -> NavigationRouteSnapshot(
            kind = NavigationRouteKind.TASK_FINISH,
            label = label,
            remainingWaypoints = remainingWaypoints,
            valid = false,
            invalidReason = NavigationRouteInvalidReason.INVALID
        )

        RacingNavigationStatus.STARTED,
        RacingNavigationStatus.IN_PROGRESS -> NavigationRouteSnapshot(
            kind = NavigationRouteKind.TASK_FINISH,
            label = label,
            remainingWaypoints = remainingWaypoints,
            valid = remainingWaypoints.isNotEmpty(),
            invalidReason = if (remainingWaypoints.isNotEmpty()) {
                NavigationRouteInvalidReason.NO_TASK
            } else {
                NavigationRouteInvalidReason.INVALID_ROUTE
            }
        )
    }
}
