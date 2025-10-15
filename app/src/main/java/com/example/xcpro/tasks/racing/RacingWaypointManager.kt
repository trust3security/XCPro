package com.example.xcpro.tasks.racing

import com.example.xcpro.SearchWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType

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
        println("🏁 RACING TASK: Adding waypoint '${searchWaypoint.title}', current size: ${currentWaypoints.size}")

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

        println("🚨 DEBUG: Created waypoint '${newWaypoint.title}' with role=${newWaypoint.role}, gateWidth=${newWaypoint.gateWidth}km")

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
                println("🚨 DEBUG: Role change for '${wp.title}': ${wp.role} → ${updatedRole}, old gateWidth=${wp.gateWidth}km")

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
                    customGateWidth = null, // Let role change get new standardized defaults
                    keyholeInnerRadius = wp.keyholeInnerRadius,
                    keyholeAngle = wp.keyholeAngle,
                    faiQuadrantOuterRadius = wp.faiQuadrantOuterRadius
                )

                println("🚨 DEBUG: After role change '${currentWaypoints[index].title}': role=${currentWaypoints[index].role}, gateWidth=${currentWaypoints[index].gateWidth}km")
            }
        }

        println("🏁 RACING TASK: Added waypoint '${searchWaypoint.title}', new size: ${currentWaypoints.size}")
        return currentTask.copy(waypoints = currentWaypoints)
    }

    /**
     * Remove waypoint from Racing task
     */
    fun removeWaypoint(currentTask: SimpleRacingTask, index: Int): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        println("🏁 RACING TASK: Removing waypoint at index $index, current size: ${currentWaypoints.size}")

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

            println("🏁 RACING TASK: Removed waypoint '${removedWaypoint.title}', new size: ${currentWaypoints.size}")
            return currentTask.copy(waypoints = currentWaypoints)
        } else {
            println("🏁 RACING TASK: Cannot remove waypoint - invalid index ($index not in range 0..${currentWaypoints.size-1})")
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
        gateWidth: Double? = null,
        keyholeInnerRadius: Double? = null,
        keyholeAngle: Double? = null
    ): SimpleRacingTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index in currentWaypoints.indices) {
            val waypoint = currentWaypoints[index]
            val oldType = when (waypoint.role) {
                RacingWaypointRole.START -> waypoint.startPointType.displayName
                RacingWaypointRole.FINISH -> waypoint.finishPointType.displayName
                RacingWaypointRole.TURNPOINT -> waypoint.turnPointType.displayName
            }

            // 🚨 DEBUG: Track waypoint update process
            println("🚨 STATE DEBUG: BEFORE UPDATE - waypoint[$index] startPointType = ${waypoint.startPointType}")
            println("🚨 STATE DEBUG: UPDATE REQUEST - startType parameter = $startType")

            // Apply type-specific defaults when switching turnpoint types
            println("🔍 KEYHOLE DEBUG: turnType=$turnType, waypoint.turnPointType=${waypoint.turnPointType}")
            println("🔍 KEYHOLE DEBUG: gateWidth parameter=$gateWidth")
            println("🔍 KEYHOLE DEBUG: type changing? ${turnType != null && turnType != waypoint.turnPointType}")

            val finalGateWidth = gateWidth ?: run {
                // If turnpoint type is changing and no gateWidth provided, use type-specific defaults
                if (turnType != null && turnType != waypoint.turnPointType) {
                    val defaultValue = when (turnType) {
                        RacingTurnPointType.KEYHOLE -> 10.0  // 10km keyhole outer radius default
                        RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 0.5  // 0.5km default
                    }
                    println("🔍 KEYHOLE DEBUG: Applying type-specific default: $defaultValue")
                    defaultValue
                } else {
                    println("🔍 KEYHOLE DEBUG: Keeping existing gateWidth: ${waypoint.gateWidth}")
                    waypoint.gateWidth  // Keep existing value if not changing type
                }
            }
            println("🔍 KEYHOLE DEBUG: finalGateWidth=$finalGateWidth")

            currentWaypoints[index] = waypoint.copy(
                startPointType = startType ?: waypoint.startPointType,
                finishPointType = finishType ?: waypoint.finishPointType,
                turnPointType = turnType ?: waypoint.turnPointType,
                gateWidth = finalGateWidth,
                keyholeInnerRadius = keyholeInnerRadius ?: waypoint.keyholeInnerRadius,
                keyholeAngle = keyholeAngle ?: waypoint.keyholeAngle
            )

            val newWaypoint = currentWaypoints[index]
            println("🔍 KEYHOLE DEBUG: FINAL waypoint gateWidth = ${newWaypoint.gateWidth}")
            println("🚨 STATE DEBUG: AFTER UPDATE - waypoint[$index] startPointType = ${newWaypoint.startPointType}")
            val newType = when (newWaypoint.role) {
                RacingWaypointRole.START -> newWaypoint.startPointType.displayName
                RacingWaypointRole.FINISH -> newWaypoint.finishPointType.displayName
                RacingWaypointRole.TURNPOINT -> newWaypoint.turnPointType.displayName
            }

            println("🏁 RACING TASK: Updated waypoint $index '${waypoint.title}' type: $oldType → $newType")
            println("🏁 RACING TASK: Updated waypoint details: ${currentWaypoints[index]}")
            return currentTask.copy(waypoints = currentWaypoints)
        } else {
            println("🏁 RACING TASK: Cannot update waypoint type - invalid index: $index")
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
                gateWidth = existingWaypoint.gateWidth,
                // FIXED: Preserve keyhole-specific parameters when replacing waypoint
                keyholeInnerRadius = existingWaypoint.keyholeInnerRadius,
                keyholeAngle = existingWaypoint.keyholeAngle
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
                    RacingWaypointRole.START -> 10.0   // 10km start lines/sectors/cylinders
                    RacingWaypointRole.FINISH -> 3.0   // 3km finish cylinders
                    RacingWaypointRole.TURNPOINT -> when (wp.turnPointType) {
                        RacingTurnPointType.KEYHOLE -> 10.0  // 10km keyhole outer radius
                        RacingTurnPointType.TURN_POINT_CYLINDER, RacingTurnPointType.FAI_QUADRANT -> 0.5  // 0.5km default
                    }
                }

                currentWaypoints[index] = wp.copy(
                    role = newRole,
                    gateWidth = newGateWidth
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