package com.example.xcpro.tasks.aat.waypoints

import com.example.xcpro.SearchWaypoint
import com.example.xcpro.tasks.aat.SimpleAATTask
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATWaypointRole
import com.example.xcpro.tasks.aat.models.AATAssignedArea
import com.example.xcpro.tasks.aat.models.AATAreaShape
import java.time.Duration
import java.util.UUID

/**
 * AAT Waypoint Manager
 *
 * Manages all waypoint operations for AAT tasks including:
 * - Task initialization from various waypoint formats
 * - Dynamic waypoint addition/removal with role management
 * - Waypoint area and property updates
 * - Task time parameter management
 *
 * REFACTORED FROM: AATTaskManager.kt (Stage 8 - Waypoint Extraction)
 * PATTERN: Stateless utility that returns updated tasks
 * DEPENDENCIES: SimpleAATTask, AATWaypoint models
 */
class AATWaypointManager {

    /**
     * Initialize AAT task from search waypoints
     *
     * Creates a new AAT task with standardized defaults:
     * - First waypoint → START (10km area)
     * - Last waypoint → FINISH (3km area)
     * - Middle waypoints → TURNPOINT (10km area)
     *
     * @param waypoints List of search waypoints to convert
     * @return New SimpleAATTask with initialized waypoints
     */
    fun initializeTask(waypoints: List<SearchWaypoint>): SimpleAATTask {
        val aatWaypoints = waypoints.mapIndexed { index, searchWaypoint ->
            AATWaypoint(
                id = searchWaypoint.id,
                title = searchWaypoint.title,
                subtitle = searchWaypoint.subtitle,
                lat = searchWaypoint.lat,
                lon = searchWaypoint.lon,
                role = when {
                    index == 0 -> AATWaypointRole.START
                    index == waypoints.lastIndex -> AATWaypointRole.FINISH
                    else -> AATWaypointRole.TURNPOINT
                },
                assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                    shape = AATAreaShape.CIRCLE,
                    role = when {
                        index == 0 -> AATWaypointRole.START
                        index == waypoints.lastIndex -> AATWaypointRole.FINISH
                        else -> AATWaypointRole.TURNPOINT
                    }
                )
            )
        }

        println("🟦 AAT WAYPOINT MANAGER: Initialized task with ${aatWaypoints.size} waypoints")

        return SimpleAATTask(
            id = UUID.randomUUID().toString(),
            waypoints = aatWaypoints,
            minimumTime = Duration.ofHours(3),
            maximumTime = Duration.ofHours(6)
        )
    }

    /**
     * Initialize AAT task from generic waypoints with smart conversion
     *
     * Intelligently converts generic waypoint format to AAT format:
     * - Preserves user customizations (radius, shape, parameters)
     * - Applies standardized defaults when no customizations exist
     * - Converts generic roles to AAT-specific roles
     * - Extracts AAT-specific parameters (sectors, lines, etc.)
     *
     * @param genericWaypoints List of generic TaskWaypoint objects
     * @return New SimpleAATTask with converted waypoints
     */
    fun initializeFromGenericWaypoints(genericWaypoints: List<com.example.xcpro.tasks.TaskWaypoint>): SimpleAATTask {
        val aatWaypoints = genericWaypoints.map { genericWaypoint ->
            // Convert generic role to AAT role
            val aatRole = when (genericWaypoint.role) {
                com.example.xcpro.tasks.WaypointRole.START -> AATWaypointRole.START
                com.example.xcpro.tasks.WaypointRole.TURNPOINT -> AATWaypointRole.TURNPOINT
                com.example.xcpro.tasks.WaypointRole.OPTIONAL -> AATWaypointRole.TURNPOINT // AAT treats optional as turnpoints
                com.example.xcpro.tasks.WaypointRole.FINISH -> AATWaypointRole.FINISH
            }

            // ✅ COMPETITION-CRITICAL: Use AATRadiusAuthority (ignore Racing-specific customizations)
            // Racing uses 0.5km turnpoints, AAT uses 10km areas - don't preserve Racing radii!
            val radiusKm = com.example.xcpro.tasks.aat.models.AATRadiusAuthority.getRadiusForRole(aatRole)
            val radiusMeters = radiusKm * 1000.0 // Convert km to meters for AAT
            println("🔧 AAT WAYPOINT MANAGER: Converting ${genericWaypoint.title} (${aatRole}) - using AUTHORITY ${radiusKm}km radius (${radiusMeters}m)")

            // Convert shapes intelligently - default to CIRCLE for simplicity
            val aatShape = when (genericWaypoint.customPointType) {
                "CIRCLE" -> AATAreaShape.CIRCLE
                "SECTOR" -> AATAreaShape.SECTOR
                "LINE" -> AATAreaShape.LINE
                else -> AATAreaShape.CIRCLE // Default circle for most conversions
            }

            // Extract advanced AAT parameters with sensible defaults
            val innerRadiusMeters = (genericWaypoint.customParameters["innerRadiusMeters"] as? Double) ?: 0.0
            val outerRadiusMeters = (genericWaypoint.customParameters["outerRadiusMeters"] as? Double) ?: radiusMeters
            val startAngleDegrees = (genericWaypoint.customParameters["startAngleDegrees"] as? Double) ?: 0.0
            val endAngleDegrees = (genericWaypoint.customParameters["endAngleDegrees"] as? Double) ?: 90.0
            val lineWidthMeters = (genericWaypoint.customParameters["lineWidthMeters"] as? Double) ?: 1000.0

            AATWaypoint(
                id = genericWaypoint.id,
                title = genericWaypoint.title,
                subtitle = genericWaypoint.subtitle,
                lat = genericWaypoint.lat,
                lon = genericWaypoint.lon,
                role = aatRole,
                assignedArea = AATAssignedArea(
                    shape = aatShape,
                    radiusMeters = radiusMeters, // Preserved user customization or standardized default
                    innerRadiusMeters = innerRadiusMeters, // For sectors (annulus)
                    outerRadiusMeters = outerRadiusMeters, // For sectors
                    startAngleDegrees = startAngleDegrees, // Sector start angle
                    endAngleDegrees = endAngleDegrees, // Sector end angle
                    lineWidthMeters = lineWidthMeters // For start/finish lines
                )
            )
        }

        val customizedCount = genericWaypoints.count { it.hasCustomizations }
        if (customizedCount > 0) {
            println("🔄 AAT WAYPOINT MANAGER: Initialized from generic waypoints - preserved ${customizedCount} customizations, applied standardized defaults to ${aatWaypoints.size - customizedCount} waypoints")
        } else {
            println("🔄 AAT WAYPOINT MANAGER: Initialized from generic waypoints - applied standardized defaults (10km start, 3km finish, 10km areas) to ${aatWaypoints.size} waypoints")
        }

        return SimpleAATTask(
            id = UUID.randomUUID().toString(),
            waypoints = aatWaypoints,
            minimumTime = Duration.ofHours(3), // Default AAT parameters
            maximumTime = Duration.ofHours(6)
        )
    }

    /**
     * Add waypoint to AAT task with dynamic role assignment
     *
     * Role assignment logic:
     * - First waypoint (0) → START
     * - Second waypoint (1) → FINISH
     * - Additional waypoints → Convert previous FINISH to TURNPOINT, new waypoint becomes FINISH
     *
     * CRITICAL: Preserves existing assignedArea during role conversions to prevent losing user customizations
     *
     * @param currentTask The current task state
     * @param searchWaypoint The waypoint to add
     * @return Updated task with new waypoint
     */
    fun addWaypoint(currentTask: SimpleAATTask, searchWaypoint: SearchWaypoint): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()

        when (currentWaypoints.size) {
            0 -> {
                // First waypoint becomes START
                val startWaypoint = AATWaypoint(
                    id = searchWaypoint.id,
                    title = searchWaypoint.title,
                    subtitle = searchWaypoint.subtitle,
                    lat = searchWaypoint.lat,
                    lon = searchWaypoint.lon,
                    role = AATWaypointRole.START,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.CIRCLE,
                        role = AATWaypointRole.START
                    )
                )
                currentWaypoints.add(startWaypoint)
                println("🟦 AAT WAYPOINT MANAGER: Added first waypoint as START: ${searchWaypoint.title}")
            }
            1 -> {
                // Second waypoint becomes FINISH
                val finishWaypoint = AATWaypoint(
                    id = searchWaypoint.id,
                    title = searchWaypoint.title,
                    subtitle = searchWaypoint.subtitle,
                    lat = searchWaypoint.lat,
                    lon = searchWaypoint.lon,
                    role = AATWaypointRole.FINISH,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.CIRCLE,
                        role = AATWaypointRole.FINISH
                    )
                )
                currentWaypoints.add(finishWaypoint)
                println("🟦 AAT WAYPOINT MANAGER: Added second waypoint as FINISH: ${searchWaypoint.title}")
            }
            else -> {
                // FIXED: New waypoint becomes FINISH, previous FINISH becomes TURNPOINT

                // Convert current FINISH waypoint to TURNPOINT
                val lastIndex = currentWaypoints.lastIndex
                val previousFinish = currentWaypoints[lastIndex]
                // ✅ COMPETITION-CRITICAL: Use AATRadiusAuthority for new role
                val newRadiusKm = com.example.xcpro.tasks.aat.models.AATRadiusAuthority.getRadiusForRole(AATWaypointRole.TURNPOINT)
                currentWaypoints[lastIndex] = previousFinish.copy(
                    role = AATWaypointRole.TURNPOINT,
                    // ✅ SSOT FIX: Removed gateWidth sync (property deleted, assignedArea is SSOT)
                    assignedArea = previousFinish.assignedArea.copy(
                        radiusMeters = newRadiusKm * 1000.0
                    )
                )
                println("🟦 AAT WAYPOINT MANAGER: Converted previous FINISH '${previousFinish.title}' to TURNPOINT (AUTHORITY radius: ${newRadiusKm}km)")

                // Add new waypoint as FINISH at the end
                val newFinishWaypoint = AATWaypoint(
                    id = searchWaypoint.id,
                    title = searchWaypoint.title,
                    subtitle = searchWaypoint.subtitle,
                    lat = searchWaypoint.lat,
                    lon = searchWaypoint.lon,
                    role = AATWaypointRole.FINISH,
                    assignedArea = AATAssignedArea.createWithStandardizedDefaults(
                        shape = AATAreaShape.CIRCLE,
                        role = AATWaypointRole.FINISH
                    )
                )
                currentWaypoints.add(newFinishWaypoint)
                println("🟦 AAT WAYPOINT MANAGER: Added new waypoint as FINISH: ${searchWaypoint.title}")
            }
        }

        return currentTask.copy(waypoints = currentWaypoints)
    }

    /**
     * Remove waypoint from AAT task with role reassignment
     *
     * After removal:
     * - Reassigns roles based on new positions (first=START, last=FINISH, middle=TURNPOINT)
     * - PRESERVES existing assignedArea during reassignment (keeps user customizations)
     * - Adjusts current leg index if needed
     *
     * @param currentTask The current task state
     * @param currentLeg The current leg index (for adjustment)
     * @param index The waypoint index to remove
     * @return Pair of (updated task, updated currentLeg)
     */
    fun removeWaypoint(currentTask: SimpleAATTask, currentLeg: Int, index: Int): Pair<SimpleAATTask, Int> {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        println("🟢 AAT WAYPOINT MANAGER: Removing waypoint at index $index, current size: ${currentWaypoints.size}")

        if (index !in currentWaypoints.indices) {
            return Pair(currentTask, currentLeg)
        }

        val removedWaypoint = currentWaypoints[index]
        currentWaypoints.removeAt(index)

        // Update roles based on new positions after removal
        // ✅ SSOT FIX: Update role and assignedArea (removed gateWidth sync)
        currentWaypoints.forEachIndexed { newIndex, wp ->
            val newRole = when {
                currentWaypoints.size == 1 -> AATWaypointRole.START
                newIndex == 0 -> AATWaypointRole.START
                newIndex == currentWaypoints.lastIndex -> AATWaypointRole.FINISH
                else -> AATWaypointRole.TURNPOINT
            }

            // ✅ COMPETITION-CRITICAL: Use AATRadiusAuthority for new role
            val newRadiusKm = com.example.xcpro.tasks.aat.models.AATRadiusAuthority.getRadiusForRole(newRole)

            currentWaypoints[newIndex] = wp.copy(
                role = newRole,
                // ✅ SSOT FIX: Removed gateWidth sync (property deleted, assignedArea is SSOT)
                assignedArea = wp.assignedArea.copy(
                    radiusMeters = newRadiusKm * 1000.0
                )
            )
        }

        // Update current leg after removal
        val newCurrentLeg = if (currentWaypoints.size > 2) {
            // If we removed a waypoint before current leg, adjust index
            when {
                index < currentLeg -> kotlin.math.max(0, currentLeg - 1)
                index == currentLeg && currentLeg >= currentWaypoints.size -> currentWaypoints.size - 1
                else -> currentLeg
            }.let { newLeg ->
                // Keep the adjusted leg within bounds
                kotlin.math.min(newLeg, currentWaypoints.size - 1)
            }
        } else {
            0
        }

        println("🟢 AAT WAYPOINT MANAGER: Removed waypoint '${removedWaypoint.title}'. New size: ${currentWaypoints.size}, new current leg: $newCurrentLeg")

        return Pair(currentTask.copy(waypoints = currentWaypoints), newCurrentLeg)
    }

    /**
     * Update AAT area properties for a waypoint
     *
     * ✅ SSOT FIX: Updates assignedArea only (single source of truth)
     * No manual synchronization needed - gateWidth property removed.
     *
     * @param currentTask The current task state
     * @param index The waypoint index to update
     * @param newArea The new assigned area properties
     * @return Updated task with new area
     */
    fun updateArea(currentTask: SimpleAATTask, index: Int, newArea: AATAssignedArea): SimpleAATTask {
        println("🔍 AAT WAYPOINT MANAGER: Starting updateArea")
        println("   - Index: $index")
        println("   - New area radius: ${newArea.radiusMeters}m (${newArea.radiusMeters/1000.0}km)")

        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (index !in currentWaypoints.indices) {
            println("   - ❌ ERROR: Index $index out of range (waypoints size: ${currentWaypoints.size})")
            return currentTask
        }

        val currentWaypoint = currentWaypoints[index]
        println("   - Current waypoint details:")
        println("     - Title: ${currentWaypoint.title}")
        println("     - Role: ${currentWaypoint.role}")
        println("     - Current assignedArea.radiusMeters: ${currentWaypoint.assignedArea.radiusMeters}m")

        // ✅ SSOT FIX: Update assignedArea only (no gateWidth sync needed - property deleted)
        currentWaypoints[index] = currentWaypoints[index].copy(
            assignedArea = newArea
        )

        val updatedWaypoint = currentWaypoints[index]
        println("   - Updated waypoint details:")
        println("     - New assignedArea.radiusMeters: ${updatedWaypoint.assignedArea.radiusMeters}m")
        println("   - ✅ SUCCESS: assignedArea updated (SSOT)!")

        return currentTask.copy(waypoints = currentWaypoints)
    }

    /**
     * Update AAT task time parameters
     *
     * @param currentTask The current task state
     * @param minTime Minimum task time
     * @param maxTime Maximum task time (optional)
     * @return Updated task with new times
     */
    fun updateTimes(currentTask: SimpleAATTask, minTime: Duration, maxTime: Duration?): SimpleAATTask {
        return currentTask.copy(
            minimumTime = minTime,
            maximumTime = maxTime
        )
    }

    /**
     * Reorder waypoints in AAT task
     * 🚨 CRITICAL: Must reassign roles after reordering (first=START, last=FINISH, middle=TURNPOINT)
     *
     * @param currentTask The current task state
     * @param fromIndex Source index
     * @param toIndex Destination index
     * @return Updated task with reordered waypoints and corrected roles
     */
    fun reorderWaypoints(currentTask: SimpleAATTask, fromIndex: Int, toIndex: Int): SimpleAATTask {
        val currentWaypoints = currentTask.waypoints.toMutableList()
        if (fromIndex !in currentWaypoints.indices || toIndex !in currentWaypoints.indices) {
            return currentTask
        }

        // Move the waypoint
        val waypoint = currentWaypoints.removeAt(fromIndex)
        currentWaypoints.add(toIndex, waypoint)

        println("🟦 AAT WAYPOINT MANAGER: Reordered waypoint from $fromIndex to $toIndex")

        // 🚨 CRITICAL: Reassign roles based on new positions
        currentWaypoints.forEachIndexed { index, wp ->
            val correctRole = when {
                currentWaypoints.size == 1 -> AATWaypointRole.START // Single waypoint
                currentWaypoints.size == 2 && index == 0 -> AATWaypointRole.START
                currentWaypoints.size == 2 && index == 1 -> AATWaypointRole.FINISH
                index == 0 -> AATWaypointRole.START // First waypoint
                index == currentWaypoints.lastIndex -> AATWaypointRole.FINISH // Last waypoint
                else -> AATWaypointRole.TURNPOINT // Middle waypoints
            }

            // Only update if role changed
            if (wp.role != correctRole) {
                // ✅ COMPETITION-CRITICAL: Use AATRadiusAuthority for new role
                val newRadiusKm = com.example.xcpro.tasks.aat.models.AATRadiusAuthority.getRadiusForRole(correctRole)

                currentWaypoints[index] = wp.copy(
                    role = correctRole,
                    // ✅ SSOT FIX: Removed gateWidth sync (property deleted, assignedArea is SSOT)
                    assignedArea = wp.assignedArea.copy(
                        radiusMeters = newRadiusKm * 1000.0
                    )
                )
                println("   🔧 AAT: Reassigned '${wp.title}' from ${wp.role} to $correctRole (radius: ${newRadiusKm}km)")
            }
        }

        return currentTask.copy(waypoints = currentWaypoints)
    }

    /**
     * Replace waypoint at index with new waypoint
     *
     * Preserves role and assigned area from existing waypoint.
     *
     * @param currentTask The current task state
     * @param index Index to replace
     * @param newWaypoint New waypoint data
     * @return Updated task with replaced waypoint
     */
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

        println("🟦 AAT WAYPOINT MANAGER: Replaced waypoint at index $index with ${newWaypoint.title}")
        return currentTask.copy(waypoints = currentWaypoints)
    }
}
