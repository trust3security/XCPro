package com.trust3.xcpro.tasks.racing

import com.trust3.xcpro.common.waypoint.SearchWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.models.RacingWaypointRole
import com.trust3.xcpro.tasks.racing.models.RacingStartPointType
import com.trust3.xcpro.tasks.racing.models.RacingFinishPointType
import com.trust3.xcpro.tasks.racing.models.RacingTurnPointType

/**
 * Racing Waypoint Manager - Manages waypoint collection operations
 *
 * ZERO DEPENDENCIES on AAT or other task modules - maintains complete separation
 * Extracted from RacingTaskManager.kt to reduce file size and improve modularity
 */
class RacingWaypointManager {
    /**
     * Add waypoint to Racing task
     */
    fun addWaypoint(currentTask: SimpleRacingTask, searchWaypoint: SearchWaypoint): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()

        // Determine the role for the new waypoint (will be TURNPOINT initially)
        val newRole = RacingWaypointRole.TURNPOINT

        val newWaypoint = RacingWaypoint.createWithStandardizedDefaults(
            id = searchWaypoint.id,
            title = searchWaypoint.title,
            subtitle = searchWaypoint.subtitle,
            lat = searchWaypoint.lat,
            lon = searchWaypoint.lon,
            role = newRole,
            turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER
        )


        // For racing tasks, add waypoints in order (at the end)
        currentWaypoints.add(newWaypoint)

        // Update roles based on positions after adding waypoint and re-apply standardized defaults
        currentWaypoints.forEachIndexed { index, wp ->
            val updatedRole = when {
                index == 0 -> RacingWaypointRole.START
                index == currentWaypoints.lastIndex -> RacingWaypointRole.FINISH
                else -> RacingWaypointRole.TURNPOINT
            }

            // If the role changed, recreate the waypoint with correct defaults
            if (wp.role != updatedRole) {

                currentWaypoints[index] = RacingWaypoint.createWithStandardizedDefaults(
                    id = wp.id,
                    title = wp.title,
                    subtitle = wp.subtitle,
                    lat = wp.lat,
                    lon = wp.lon,
                    role = updatedRole,
                    startPointType = wp.startPointType,
                    finishPointType = wp.finishPointType,
                    turnPointType = wp.turnPointType,
                    keyholeInnerRadiusMeters = wp.keyholeInnerRadiusMeters,
                    keyholeAngle = wp.keyholeAngle,
                    faiQuadrantOuterRadiusMeters = wp.faiQuadrantOuterRadiusMeters
                )

            }
        }

        return currentTask.copy(waypoints = currentWaypoints)
    }

    /**
     * Remove waypoint from Racing task
     */
    fun removeWaypoint(currentTask: SimpleRacingTask, index: Int): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()

        if (index in currentWaypoints.indices) {
            val removedWaypoint = currentWaypoints[index]
            currentWaypoints.removeAt(index)

            // Update roles based on new positions after removal
            currentWaypoints.forEachIndexed { newIndex, wp ->
                currentWaypoints[newIndex] = wp.copy(
                    role = when {
                        currentWaypoints.size == 1 -> RacingWaypointRole.START // Single waypoint is a start
                        newIndex == 0 -> RacingWaypointRole.START
                        newIndex == currentWaypoints.lastIndex -> RacingWaypointRole.FINISH
                        else -> RacingWaypointRole.TURNPOINT
                    }
                )
            }

            return currentTask.copy(waypoints = currentWaypoints)
        } else {
            return currentTask
        }
    }

    /**
     * Update racing waypoint properties
     */
    fun updateWaypointType(
        currentTask: SimpleRacingTask,
        index: Int,
        startType: RacingStartPointType? = null,
        finishType: RacingFinishPointType? = null,
        turnType: RacingTurnPointType? = null,
        gateWidthMeters: Double? = null,
        keyholeInnerRadiusMeters: Double? = null,
        keyholeAngle: Double? = null,
        faiQuadrantOuterRadiusMeters: Double? = null
    ): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            val waypoint = currentWaypoints[index]
            val oldType = when (waypoint.role) {
                RacingWaypointRole.START -> waypoint.startPointType.displayName
                RacingWaypointRole.FINISH -> waypoint.finishPointType.displayName
                RacingWaypointRole.TURNPOINT -> waypoint.turnPointType.displayName
            }

            //  DEBUG: Track waypoint update process

            // Apply type-specific defaults when switching turnpoint types

            val finalGateWidthMeters = gateWidthMeters ?: run {
                // If turnpoint type is changing and no gateWidth provided, use type-specific defaults
                if (turnType != null && turnType != waypoint.turnPointType) {
                    val defaultValue = when (turnType) {
                        RacingTurnPointType.KEYHOLE -> 10_000.0
                        RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 500.0
                    }
                    defaultValue
                } else {
                    waypoint.gateWidthMeters
                }
            }

            val finalFaiQuadrantOuterRadiusMeters = when {
                faiQuadrantOuterRadiusMeters != null -> faiQuadrantOuterRadiusMeters
                turnType == RacingTurnPointType.FAI_QUADRANT && turnType != waypoint.turnPointType -> 10_000.0
                else -> waypoint.faiQuadrantOuterRadiusMeters
            }
            val finalKeyholeInnerRadiusMeters = keyholeInnerRadiusMeters
                ?: waypoint.keyholeInnerRadiusMeters

            currentWaypoints[index] = waypoint.copy(
                startPointType = startType ?: waypoint.startPointType,
                finishPointType = finishType ?: waypoint.finishPointType,
                turnPointType = turnType ?: waypoint.turnPointType,
                gateWidthMeters = finalGateWidthMeters,
                keyholeInnerRadiusMeters = finalKeyholeInnerRadiusMeters,
                keyholeAngle = keyholeAngle ?: waypoint.keyholeAngle,
                faiQuadrantOuterRadiusMeters = finalFaiQuadrantOuterRadiusMeters
            )

            val newWaypoint = currentWaypoints[index]
            val newType = when (newWaypoint.role) {
                RacingWaypointRole.START -> newWaypoint.startPointType.displayName
                RacingWaypointRole.FINISH -> newWaypoint.finishPointType.displayName
                RacingWaypointRole.TURNPOINT -> newWaypoint.turnPointType.displayName
            }

            return currentTask.copy(waypoints = currentWaypoints)
        } else {
            return currentTask
        }
    }

    /**
     * Replace waypoint in Racing task
     */
    fun replaceWaypoint(currentTask: SimpleRacingTask, index: Int, newWaypoint: SearchWaypoint): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            val existingWaypoint = currentWaypoints[index]
            currentWaypoints[index] = RacingWaypoint(
                id = newWaypoint.id,
                title = newWaypoint.title,
                subtitle = newWaypoint.subtitle,
                lat = newWaypoint.lat,
                lon = newWaypoint.lon,
                role = existingWaypoint.role,
                startPointType = existingWaypoint.startPointType,
                finishPointType = existingWaypoint.finishPointType,
                turnPointType = existingWaypoint.turnPointType,
                gateWidthMeters = existingWaypoint.gateWidthMeters,
                // FIXED: Preserve keyhole-specific parameters when replacing waypoint
                keyholeInnerRadiusMeters = existingWaypoint.keyholeInnerRadiusMeters,
                keyholeAngle = existingWaypoint.keyholeAngle,
                faiQuadrantOuterRadiusMeters = existingWaypoint.faiQuadrantOuterRadiusMeters
            )
            return currentTask.copy(waypoints = currentWaypoints)
        }
        return currentTask
    }

    /**
     * Reorder waypoints in Racing task
     */
    fun reorderWaypoints(currentTask: SimpleRacingTask, fromIndex: Int, toIndex: Int): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (fromIndex in currentWaypoints.indices && toIndex in currentWaypoints.indices) {
            val waypoint = currentWaypoints.removeAt(fromIndex)
            currentWaypoints.add(toIndex, waypoint)

            // Update roles based on new positions and recalculate gateWidth defaults
            currentWaypoints.forEachIndexed { index, wp ->
                val newRole = when {
                    index == 0 -> RacingWaypointRole.START
                    index == currentWaypoints.lastIndex -> RacingWaypointRole.FINISH
                    else -> RacingWaypointRole.TURNPOINT
                }

                // Recalculate gateWidth defaults for the new role
                val newGateWidth = when (newRole) {
                    RacingWaypointRole.START -> 10_000.0
                    RacingWaypointRole.FINISH -> 3_000.0
                    RacingWaypointRole.TURNPOINT -> when (wp.turnPointType) {
                        RacingTurnPointType.KEYHOLE -> 10_000.0
                        RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 500.0
                    }
                }

                currentWaypoints[index] = wp.copy(
                    role = newRole,
                    gateWidthMeters = newGateWidth
                )
            }

            return currentTask.copy(waypoints = currentWaypoints)
        }
        return currentTask
    }

    /**
     * Calculate current leg index after waypoint removal
     * Helper function to adjust current leg when waypoints are removed
     */
    fun calculateLegAfterRemoval(
        originalCurrentLeg: Int,
        removedIndex: Int,
        newWaypointCount: Int
    ): Int {
        return if (newWaypointCount > 2) {
            // If we removed a waypoint before current leg, adjust index
            when {
                removedIndex < originalCurrentLeg -> maxOf(0, originalCurrentLeg - 1)
                removedIndex == originalCurrentLeg && originalCurrentLeg >= newWaypointCount -> newWaypointCount - 1
                else -> originalCurrentLeg
            }.let { newLeg ->
                // Keep the adjusted leg within bounds
                minOf(newLeg, newWaypointCount - 1)
            }
        } else {
            0
        }
    }
}
