package com.trust3.xcpro.livefollow.data.task

import com.trust3.xcpro.livefollow.model.LiveFollowTaskPoint
import com.trust3.xcpro.livefollow.model.LiveFollowTaskSnapshot
import com.trust3.xcpro.tasks.TaskManagerCoordinator
import com.trust3.xcpro.tasks.TaskRuntimeSnapshot
import com.trust3.xcpro.tasks.core.AATWaypointCustomParams
import com.trust3.xcpro.tasks.core.RacingWaypointCustomParams
import com.trust3.xcpro.tasks.core.TaskType
import com.trust3.xcpro.tasks.core.TaskWaypoint
import com.trust3.xcpro.tasks.core.WaypointRole
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class TaskCoordinatorLiveFollowTaskSnapshotSource @Inject constructor(
    private val taskCoordinator: TaskManagerCoordinator
) : LiveFollowTaskSnapshotSource {
    override val taskSnapshot: Flow<LiveFollowTaskSnapshot?> =
        taskCoordinator.taskSnapshotFlow
            .map(::toLiveFollowTaskSnapshot)
            .distinctUntilChanged()
}

private fun toLiveFollowTaskSnapshot(
    snapshot: TaskRuntimeSnapshot
): LiveFollowTaskSnapshot? {
    val points = snapshot.task.waypoints.mapIndexedNotNull { index, waypoint ->
        waypoint.toLiveFollowTaskPoint(
            taskType = snapshot.taskType,
            order = index
        )
    }
    if (points.size < 2) {
        return null
    }
    return LiveFollowTaskSnapshot(
        taskName = snapshot.task.id.trim().takeIf { it.isNotEmpty() },
        points = points
    )
}

private fun TaskWaypoint.toLiveFollowTaskPoint(
    taskType: TaskType,
    order: Int
): LiveFollowTaskPoint? {
    if (!latitudeLongitudeIsValid()) {
        return null
    }
    return LiveFollowTaskPoint(
        order = order,
        latitudeDeg = lat,
        longitudeDeg = lon,
        radiusMeters = resolvedRadiusMeters(taskType),
        name = title.takeIf { it.isNotBlank() }
            ?: subtitle.takeIf { it.isNotBlank() }
            ?: id.trim().takeIf { it.isNotEmpty() },
        type = customPointType?.trim()?.takeIf { it.isNotEmpty() } ?: role.name
    )
}

private fun TaskWaypoint.latitudeLongitudeIsValid(): Boolean {
    if (!lat.isFinite() || !lon.isFinite()) {
        return false
    }
    return lat in -90.0..90.0 && lon in -180.0..180.0
}

private fun TaskWaypoint.resolvedRadiusMeters(
    taskType: TaskType
): Double? {
    val pointType = customPointType
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase(Locale.US)
    val explicitRadiusMeters = resolvedCustomRadiusMeters()?.takeIf { it > 0.0 }
    return when (taskType) {
        TaskType.RACING -> when {
            pointType?.contains("FAI_QUADRANT") == true -> {
                RacingWaypointCustomParams.from(customParameters)
                    .faiQuadrantOuterRadiusMeters
                    .takeIf { it > 0.0 }
                    ?: explicitRadiusMeters
                    ?: defaultRacingRadiusMeters(role, pointType)
            }

            else -> explicitRadiusMeters ?: defaultRacingRadiusMeters(role, pointType)
        }

        TaskType.AAT -> when {
            pointType?.contains("SECTOR") == true || pointType?.contains("KEYHOLE") == true -> {
                AATWaypointCustomParams.from(
                    source = customParameters,
                    fallbackLat = lat,
                    fallbackLon = lon,
                    fallbackRadiusMeters = explicitRadiusMeters
                        ?: defaultAatRadiusMeters(role)
                ).outerRadiusMeters.takeIf { it > 0.0 }
                    ?: explicitRadiusMeters
                    ?: defaultAatRadiusMeters(role)
            }

            else -> explicitRadiusMeters ?: defaultAatRadiusMeters(role)
        }
    }
}

private fun defaultRacingRadiusMeters(
    role: WaypointRole,
    pointType: String?
): Double {
    return when (role) {
        WaypointRole.START -> 10_000.0
        WaypointRole.FINISH -> 3_000.0
        WaypointRole.TURNPOINT,
        WaypointRole.OPTIONAL -> {
            if (pointType?.contains("KEYHOLE") == true || pointType?.contains("FAI_QUADRANT") == true) {
                10_000.0
            } else {
                500.0
            }
        }
    }
}

private fun defaultAatRadiusMeters(role: WaypointRole): Double {
    return when (role) {
        WaypointRole.START -> 10_000.0
        WaypointRole.FINISH -> 3_000.0
        WaypointRole.TURNPOINT,
        WaypointRole.OPTIONAL -> 10_000.0
    }
}
