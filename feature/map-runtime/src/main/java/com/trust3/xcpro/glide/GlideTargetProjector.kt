package com.trust3.xcpro.glide

import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.RacingFinishCustomParams
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.navigation.NavigationRouteInvalidReason
import com.trust3.xcpro.tasks.navigation.NavigationRoutePoint
import com.trust3.xcpro.tasks.navigation.NavigationRouteSnapshot
import javax.inject.Inject

// Phase-3 policy owner: finish-rule mapping and glide-status projection live
// here. The canonical remaining route continues to come from the task-owned
// NavigationRouteSnapshot seam.
class GlideTargetProjector @Inject constructor() {
    fun project(
        taskSnapshot: TaskRuntimeSnapshot,
        route: NavigationRouteSnapshot
    ): GlideTargetSnapshot {
        val task = taskSnapshot.task
        if (taskSnapshot.taskType != TaskType.RACING || task.waypoints.size < 2) {
            return GlideTargetSnapshot()
        }

        val finishWaypoint = task.waypoints.last()
        val label = route.label.ifBlank { finishWaypoint.title.ifBlank { "Finish" } }
        val remainingWaypoints = route.remainingWaypoints
        val finishConstraint = RacingFinishCustomParams.from(finishWaypoint.customParameters)
            .toGlideFinishConstraintOrNull()

        return when {
            route.invalidReason == NavigationRouteInvalidReason.PRESTART -> invalidGlideTarget(
                label = label,
                remainingWaypoints = remainingWaypoints,
                finishConstraint = finishConstraint,
                invalidReason = GlideInvalidReason.PRESTART
            )

            route.invalidReason == NavigationRouteInvalidReason.FINISHED -> invalidGlideTarget(
                label = label,
                remainingWaypoints = emptyList(),
                finishConstraint = finishConstraint,
                invalidReason = GlideInvalidReason.FINISHED
            )

            route.invalidReason == NavigationRouteInvalidReason.INVALID -> invalidGlideTarget(
                label = label,
                remainingWaypoints = remainingWaypoints,
                finishConstraint = finishConstraint,
                invalidReason = GlideInvalidReason.INVALID
            )

            route.valid -> {
                if (finishConstraint == null) {
                    invalidGlideTarget(
                        label = label,
                        remainingWaypoints = remainingWaypoints,
                        finishConstraint = null,
                        invalidReason = GlideInvalidReason.NO_FINISH_ALTITUDE
                    )
                } else {
                    GlideTargetSnapshot(
                        kind = GlideTargetKind.TASK_FINISH,
                        label = label,
                        remainingWaypoints = remainingWaypoints,
                        finishConstraint = finishConstraint,
                        valid = true
                    )
                }
            }

            route.invalidReason == NavigationRouteInvalidReason.INVALID_ROUTE -> invalidGlideTarget(
                label = label,
                remainingWaypoints = remainingWaypoints,
                finishConstraint = finishConstraint,
                invalidReason = if (finishConstraint == null) {
                    GlideInvalidReason.NO_FINISH_ALTITUDE
                } else {
                    GlideInvalidReason.INVALID_ROUTE
                }
            )

            else -> invalidGlideTarget(
                label = label,
                remainingWaypoints = remainingWaypoints,
                finishConstraint = finishConstraint,
                invalidReason = GlideInvalidReason.NO_TASK
            )
        }
    }
}

private fun invalidGlideTarget(
    label: String,
    remainingWaypoints: List<NavigationRoutePoint>,
    finishConstraint: GlideFinishConstraint?,
    invalidReason: GlideInvalidReason
): GlideTargetSnapshot = GlideTargetSnapshot(
    kind = GlideTargetKind.TASK_FINISH,
    label = label,
    remainingWaypoints = remainingWaypoints,
    finishConstraint = finishConstraint,
    valid = false,
    invalidReason = invalidReason
)
