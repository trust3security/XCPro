package com.example.xcpro.tasks.aat.waypoints

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATRadiusAuthority
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import java.time.Duration

internal object AATWaypointMutationSupport {

    fun addWaypoint(currentTask: SimpleAATTask, searchWaypoint: SearchWaypoint): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()

        when (currentWaypoints.size) {
            0 -> {
                currentWaypoints.add(
                    newSearchWaypointAsRole(searchWaypoint, AATWaypointRole.START)
                )
            }

            1 -> {
                currentWaypoints.add(
                    newSearchWaypointAsRole(searchWaypoint, AATWaypointRole.FINISH)
                )
            }

            else -> {
                val lastIndex = currentWaypoints.lastIndex
                val previousFinish = currentWaypoints[lastIndex]
                val newRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(AATWaypointRole.TURNPOINT)
                currentWaypoints[lastIndex] = previousFinish.copy(
                    role = AATWaypointRole.TURNPOINT,
                    assignedArea = previousFinish.assignedArea.copy(
                        radiusMeters = newRadiusMeters
                    )
                )

                currentWaypoints.add(
                    newSearchWaypointAsRole(searchWaypoint, AATWaypointRole.FINISH)
                )
            }
        }

        return currentTask.copy(waypoints = currentWaypoints)
    }

    fun removeWaypoint(currentTask: SimpleAATTask, currentLeg: Int, index: Int): Pair<SimpleAATTask, Int> {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index !in currentWaypoints.indices) {
            return Pair(currentTask, currentLeg)
        }

        currentWaypoints.removeAt(index)
        reassignRolesAndRadii(currentWaypoints)

        val newCurrentLeg = if (currentWaypoints.size > 2) {
            when {
                index < currentLeg -> kotlin.math.max(0, currentLeg - 1)
                index == currentLeg && currentLeg >= currentWaypoints.size -> currentWaypoints.size - 1
                else -> currentLeg
            }.let { kotlin.math.min(it, currentWaypoints.size - 1) }
        } else {
            0
        }

        return Pair(currentTask.copy(waypoints = currentWaypoints), newCurrentLeg)
    }

    fun updateArea(currentTask: SimpleAATTask, index: Int, newArea: AATAssignedArea): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index !in currentWaypoints.indices) {
            return currentTask
        }
        currentWaypoints[index] = currentWaypoints[index].copy(assignedArea = newArea)
        return currentTask.copy(waypoints = currentWaypoints)
    }

    fun updateTimes(currentTask: SimpleAATTask, minTime: Duration, maxTime: Duration?): SimpleAATTask {
        return currentTask.copy(minimumTime = minTime, maximumTime = maxTime)
    }

    fun reorderWaypoints(currentTask: SimpleAATTask, fromIndex: Int, toIndex: Int): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (fromIndex !in currentWaypoints.indices || toIndex !in currentWaypoints.indices) {
            return currentTask
        }

        val waypoint = currentWaypoints.removeAt(fromIndex)
        currentWaypoints.add(toIndex, waypoint)
        reassignRolesAndRadii(currentWaypoints)
        return currentTask.copy(waypoints = currentWaypoints)
    }

    fun replaceWaypoint(currentTask: SimpleAATTask, index: Int, newWaypoint: SearchWaypoint): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index !in currentWaypoints.indices) {
            return currentTask
        }

        val existingWaypoint = currentWaypoints[index]
        currentWaypoints[index] = AATWaypoint(
            id = newWaypoint.id,
            title = newWaypoint.title,
            subtitle = newWaypoint.subtitle,
            lat = newWaypoint.lat,
            lon = newWaypoint.lon,
            role = existingWaypoint.role,
            assignedArea = existingWaypoint.assignedArea
        )

        return currentTask.copy(waypoints = currentWaypoints)
    }

    private fun reassignRolesAndRadii(waypoints: MutableList<AATWaypoint>) {
        waypoints.forEachIndexed { index, waypoint ->
            val newRole = when {
                waypoints.size == 1 -> AATWaypointRole.START
                index == 0 -> AATWaypointRole.START
                index == waypoints.lastIndex -> AATWaypointRole.FINISH
                else -> AATWaypointRole.TURNPOINT
            }
            val newRadiusMeters = AATRadiusAuthority.getRadiusMetersForRole(newRole)
            waypoints[index] = waypoint.copy(
                role = newRole,
                assignedArea = waypoint.assignedArea.copy(
                    radiusMeters = newRadiusMeters
                )
            )
        }
    }

    private fun newSearchWaypointAsRole(
        searchWaypoint: SearchWaypoint,
        role: AATWaypointRole
    ): AATWaypoint {
        return AATWaypoint(
            id = searchWaypoint.id,
            title = searchWaypoint.title,
            subtitle = searchWaypoint.subtitle,
            lat = searchWaypoint.lat,
            lon = searchWaypoint.lon,
            role = role,
            assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                shape = AATAreaShape.CIRCLE,
                role = role
            )
        )
    }
}
