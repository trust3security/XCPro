package com.example.xcpro.tasks.racing

import com.example.xcpro.common.waypoint.SearchWaypoint
import com.example.xcpro.tasks.core.TaskWaypoint
import com.example.xcpro.tasks.core.WaypointRole
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import java.util.UUID

/**
 * Racing Task Initializer - Handles all Racing task initialization operations
 *
 * ZERO DEPENDENCIES on AAT or other task modules - maintains complete separation
 * Extracted from RacingTaskManager.kt to reduce file size and improve modularity
 */
class RacingTaskInitializer {

    /**
     * Initialize Racing task with waypoints
     */
    fun initializeRacingTask(waypoints: List<SearchWaypoint>): SimpleRacingTask {
        val racingWaypoints = waypoints.mapIndexed { index, searchWaypoint ->
            val role = when {
                index == 0 -> RacingWaypointRole.START
                index == waypoints.lastIndex -> RacingWaypointRole.FINISH
                else -> RacingWaypointRole.TURNPOINT
            }

            RacingWaypoint.createWithStandardizedDefaults(
                id = searchWaypoint.id,
                title = searchWaypoint.title,
                subtitle = searchWaypoint.subtitle,
                lat = searchWaypoint.lat,
                lon = searchWaypoint.lon,
                role = role,
                startPointType = if (index == 0) RacingStartPointType.START_LINE else RacingStartPointType.START_LINE,
                finishPointType = if (index == waypoints.lastIndex) RacingFinishPointType.FINISH_CYLINDER else RacingFinishPointType.FINISH_CYLINDER,
                turnPointType = RacingTurnPointType.TURN_POINT_CYLINDER // Default - can be changed via UI
            )
        }

        val task = SimpleRacingTask(
            id = UUID.randomUUID().toString(),
            waypoints = racingWaypoints
        )

        println("ðŸ RACING TASK: Initialized with ${racingWaypoints.size} waypoints")
        return task
    }

    /**
     * Initialize Racing task from generic waypoints with smart conversion and value preservation
     * Preserves user customizations while applying standardized defaults (10km start, 3km finish)
     */
    fun initializeFromGenericWaypoints(genericWaypoints: List<TaskWaypoint>): SimpleRacingTask {
        val racingWaypoints = genericWaypoints.map { genericWaypoint ->
            // Convert generic role to Racing role
            val racingRole = when (genericWaypoint.role) {
                WaypointRole.START -> RacingWaypointRole.START
                WaypointRole.TURNPOINT -> RacingWaypointRole.TURNPOINT
                WaypointRole.OPTIONAL -> RacingWaypointRole.TURNPOINT
                WaypointRole.FINISH -> RacingWaypointRole.FINISH
            }

            // Convert point types intelligently
            val startPointType = if (racingRole == RacingWaypointRole.START) {
                when (genericWaypoint.customPointType) {
                    "START_CYLINDER" -> RacingStartPointType.START_CYLINDER
                    else -> RacingStartPointType.START_LINE // Default to start line
                }
            } else RacingStartPointType.START_LINE

            val finishPointType = if (racingRole == RacingWaypointRole.FINISH) {
                when (genericWaypoint.customPointType) {
                    "FINISH_LINE" -> RacingFinishPointType.FINISH_LINE
                    else -> RacingFinishPointType.FINISH_CYLINDER // Default to cylinder
                }
            } else RacingFinishPointType.FINISH_CYLINDER

            val turnPointType = when (genericWaypoint.customPointType) {
                "TURN_POINT_CYLINDER" -> RacingTurnPointType.TURN_POINT_CYLINDER
                "FAI_QUADRANT" -> RacingTurnPointType.FAI_QUADRANT
                "KEYHOLE" -> RacingTurnPointType.KEYHOLE
                else -> RacingTurnPointType.TURN_POINT_CYLINDER // Default cylinder
            }

            // Extract advanced parameters with sensible defaults
            val keyholeInnerRadius = (genericWaypoint.customParameters["keyholeInnerRadius"] as? Double) ?: 0.5
            val keyholeAngle = (genericWaypoint.customParameters["keyholeAngle"] as? Double) ?: 90.0
            val faiQuadrantOuterRadius = (genericWaypoint.customParameters["faiQuadrantOuterRadius"] as? Double) ?: 10.0

            // Get user customized radius if available, otherwise let factory method apply proper defaults
            val customGateWidth = genericWaypoint.customRadius?.takeIf { it > 0.0 }

            // Use factory method with proper role-based defaults instead of direct constructor
            RacingWaypoint.createWithStandardizedDefaults(
                id = genericWaypoint.id,
                title = genericWaypoint.title,
                subtitle = genericWaypoint.subtitle,
                lat = genericWaypoint.lat,
                lon = genericWaypoint.lon,
                role = racingRole,
                startPointType = startPointType,
                finishPointType = finishPointType,
                turnPointType = turnPointType,
                customGateWidth = customGateWidth, // Preserve user customization or apply role-based defaults
                // Advanced parameters - preserved if available
                keyholeInnerRadius = keyholeInnerRadius,
                keyholeAngle = keyholeAngle,
                faiQuadrantOuterRadius = faiQuadrantOuterRadius
            )
        }

        val task = SimpleRacingTask(
            id = UUID.randomUUID().toString(),
            waypoints = racingWaypoints
        )

        val customizedCount = genericWaypoints.count { it.hasCustomizations }
        if (customizedCount > 0) {
            println("ðŸ”„ RACING TASK: Initialized from generic waypoints - preserved ${customizedCount} customizations, applied standardized defaults to ${racingWaypoints.size - customizedCount} waypoints")
        } else {
            println("ðŸ”„ RACING TASK: Initialized from generic waypoints - applied standardized defaults (10km start, 3km finish) to ${racingWaypoints.size} waypoints")
        }

        return task
    }
}
