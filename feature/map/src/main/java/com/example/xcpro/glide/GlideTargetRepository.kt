package com.example.xcpro.glide

import com.example.xcpro.tasks.TaskNavigationController
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.tasks.TaskRuntimeSnapshot
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.core.RacingFinishCustomParams
import com.example.xcpro.tasks.core.TaskType
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.racing.navigation.RacingNavigationState
import com.example.xcpro.tasks.racing.navigation.RacingNavigationStatus
import javax.inject.Inject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class GlideTargetKind {
    TASK_FINISH
}

internal enum class GlideInvalidReason {
    NO_TASK,
    PRESTART,
    NO_FINISH_ALTITUDE,
    NO_POSITION,
    NO_ALTITUDE,
    NO_POLAR,
    INVALID_ROUTE,
    INVALID_SPEED,
    FINISHED,
    INVALID
}

internal data class GlideRoutePoint(
    val lat: Double,
    val lon: Double,
    val label: String
)

internal data class GlideFinishConstraint(
    val requiredAltitudeMeters: Double,
    val altitudeReference: RacingAltitudeReference
)

internal data class GlideTargetSnapshot(
    val kind: GlideTargetKind? = null,
    val label: String = "",
    val remainingWaypoints: List<GlideRoutePoint> = emptyList(),
    val finishConstraint: GlideFinishConstraint? = null,
    val valid: Boolean = false,
    val invalidReason: GlideInvalidReason = GlideInvalidReason.NO_TASK
)

@ViewModelScoped
class GlideTargetRepository @Inject constructor(
    taskManager: TaskManagerCoordinator,
    taskNavigationController: TaskNavigationController
) {
    internal val finishTarget: Flow<GlideTargetSnapshot> = combine(
        taskManager.taskSnapshotFlow,
        taskNavigationController.racingState,
        ::resolveFinishTarget
    ).distinctUntilChanged()
}

internal fun resolveFinishTarget(
    taskSnapshot: TaskRuntimeSnapshot,
    navigationState: RacingNavigationState
): GlideTargetSnapshot {
    val task = taskSnapshot.task
    if (taskSnapshot.taskType != TaskType.RACING || task.waypoints.size < 2) {
        return GlideTargetSnapshot()
    }

    val finishWaypoint = task.waypoints.last()
    val finishRules = RacingFinishCustomParams.from(finishWaypoint.customParameters)
    val finishConstraint = finishRules.toConstraintOrNull()
    val label = finishWaypoint.title.ifBlank { "Finish" }
    val remainingWaypoints = task.waypoints.remainingWaypointsFrom(navigationState.currentLegIndex)

    return when (navigationState.status) {
        RacingNavigationStatus.PENDING_START -> GlideTargetSnapshot(
            kind = GlideTargetKind.TASK_FINISH,
            label = label,
            remainingWaypoints = remainingWaypoints,
            finishConstraint = finishConstraint,
            valid = false,
            invalidReason = GlideInvalidReason.PRESTART
        )

        RacingNavigationStatus.FINISHED -> GlideTargetSnapshot(
            kind = GlideTargetKind.TASK_FINISH,
            label = label,
            remainingWaypoints = emptyList(),
            finishConstraint = finishConstraint,
            valid = false,
            invalidReason = GlideInvalidReason.FINISHED
        )

        RacingNavigationStatus.INVALIDATED -> GlideTargetSnapshot(
            kind = GlideTargetKind.TASK_FINISH,
            label = label,
            remainingWaypoints = remainingWaypoints,
            finishConstraint = finishConstraint,
            valid = false,
            invalidReason = GlideInvalidReason.INVALID
        )

        RacingNavigationStatus.STARTED,
        RacingNavigationStatus.IN_PROGRESS -> {
            if (finishConstraint == null) {
                GlideTargetSnapshot(
                    kind = GlideTargetKind.TASK_FINISH,
                    label = label,
                    remainingWaypoints = remainingWaypoints,
                    finishConstraint = null,
                    valid = false,
                    invalidReason = GlideInvalidReason.NO_FINISH_ALTITUDE
                )
            } else {
                GlideTargetSnapshot(
                    kind = GlideTargetKind.TASK_FINISH,
                    label = label,
                    remainingWaypoints = remainingWaypoints,
                    finishConstraint = finishConstraint,
                    valid = remainingWaypoints.isNotEmpty(),
                    invalidReason = if (remainingWaypoints.isNotEmpty()) {
                        GlideInvalidReason.NO_TASK
                    } else {
                        GlideInvalidReason.INVALID_ROUTE
                    }
                )
            }
        }
    }
}

private fun List<TaskWaypoint>.remainingWaypointsFrom(currentLegIndex: Int): List<GlideRoutePoint> {
    if (isEmpty()) return emptyList()
    val startIndex = currentLegIndex.coerceIn(1, lastIndex)
    return subList(startIndex, size).map { waypoint ->
        GlideRoutePoint(
            lat = waypoint.lat,
            lon = waypoint.lon,
            label = waypoint.title.ifBlank { waypoint.id }
        )
    }
}

private fun RacingFinishCustomParams.toConstraintOrNull(): GlideFinishConstraint? {
    val requiredAltitudeMeters = minAltitudeMeters?.takeIf { it.isFinite() } ?: return null
    return GlideFinishConstraint(
        requiredAltitudeMeters = requiredAltitudeMeters,
        altitudeReference = altitudeReference
    )
}
