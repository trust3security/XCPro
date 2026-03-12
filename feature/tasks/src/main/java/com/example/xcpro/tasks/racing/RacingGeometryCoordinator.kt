package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.racing.models.*
import com.example.xcpro.tasks.racing.turnpoints.*

/**
 * Racing Geometry Coordinator - Routes geometry generation to specialized display classes
 *
 * Delegates to turnpoint-specific display classes (FAIQuadrantDisplay, CylinderDisplay, etc.)
 * to ensure visual geometry uses the SAME algorithms as mathematical calculations.
 *
 * Extracted from RacingTaskDisplay.kt for file size compliance.
 */
class RacingGeometryCoordinator(
    private val faiQuadrantDisplay: FAIQuadrantDisplay,
    private val cylinderDisplay: CylinderDisplay,
    private val keyholeDisplay: KeyholeDisplay,
    private val startLineDisplay: StartLineDisplay,
    private val finishLineDisplay: FinishLineDisplay,
    private val faiStartSectorDisplay: FAIStartSectorDisplay
) {

    /**
     * Generate start point geometry
     */
    fun generateStartGeometry(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String? {
        return when (waypoint.startPointType) {
            RacingStartPointType.START_LINE -> {
                generateStartLine(index, waypoint, allWaypoints)
            }
            RacingStartPointType.START_CYLINDER -> {
                generateCylinder(waypoint, waypoint.gateWidthMeters, "start")
            }
            RacingStartPointType.FAI_START_SECTOR -> {
                // Use dedicated FAIStartSectorDisplay class instead of inline geometry
                val context = TaskContext(
                    waypointIndex = index,
                    allWaypoints = allWaypoints,
                    previousWaypoint = null, // Start has no previous waypoint
                    nextWaypoint = if (index < allWaypoints.size - 1) allWaypoints[index + 1] else null
                )
                faiStartSectorDisplay.generateVisualGeometry(waypoint, context)
            }
        }
    }

    /**
     * Generate start line geometry
     */
    private fun generateStartLine(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String {
        // Use StartLineDisplay for consistent geometry with calculations
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = null, // Start has no previous waypoint
            nextWaypoint = if (index < allWaypoints.size - 1) allWaypoints[index + 1] else null
        )
        return startLineDisplay.generateVisualGeometry(waypoint, context)
    }

    /**
     * Generate finish point geometry
     */
    fun generateFinishGeometry(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String? {
        return when (waypoint.finishPointType) {
            RacingFinishPointType.FINISH_LINE -> {
                generateFinishLine(index, waypoint, allWaypoints)
            }
            RacingFinishPointType.FINISH_CYLINDER -> {
                generateCylinder(waypoint, waypoint.gateWidthMeters, "finish")
            }
        }
    }

    /**
     * Generate finish line geometry
     */
    private fun generateFinishLine(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String {
        // Use FinishLineDisplay for consistent geometry with calculations
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = if (index > 0) allWaypoints[index - 1] else null,
            nextWaypoint = null // Finish has no next waypoint
        )
        return finishLineDisplay.generateVisualGeometry(waypoint, context)
    }

    /**
     * Generate turnpoint geometry
     */
    fun generateTurnpointGeometry(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String? {
        return when (waypoint.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                generateCylinder(waypoint, waypoint.gateWidthMeters, "turnpoint")
            }
            RacingTurnPointType.FAI_QUADRANT -> {
                generateFAIQuadrant(index, waypoint, allWaypoints)
            }
            RacingTurnPointType.KEYHOLE -> {
                generateKeyhole(index, waypoint, allWaypoints)
            }
        }
    }

    /**
     * Generate cylinder geometry - delegate to RacingGeometryUtils (SSOT)
     */
    private fun generateCylinder(waypoint: RacingWaypoint, radiusMeters: Double, role: String = "turnpoint"): String {
        val circleCoordinates = RacingGeometryUtils.generateCircleCoordinates(waypoint.lat, waypoint.lon, radiusMeters)

        return """
        {
            "type": "Feature",
            "geometry": {
                "type": "Polygon",
                "coordinates": [[$circleCoordinates]]
            },
            "properties": {
                "type": "racing_cylinder",
                "title": "${waypoint.title}",
                "radius": $radiusMeters,
                "role": "$role"
            }
        }
        """.trimIndent()
    }

    /**
     * Generate FAI quadrant geometry
     */
    private fun generateFAIQuadrant(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String {
        // Use FAIQuadrantDisplay for consistent geometry with calculations
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = if (index > 0) allWaypoints[index - 1] else null,
            nextWaypoint = if (index < allWaypoints.size - 1) allWaypoints[index + 1] else null
        )
        return faiQuadrantDisplay.generateVisualGeometry(waypoint, context)
    }

    /**
     * Generate keyhole geometry
     */
    private fun generateKeyhole(index: Int, waypoint: RacingWaypoint, allWaypoints: List<RacingWaypoint>): String {
        // Use KeyholeDisplay to generate proper keyhole shape (cylinder + 90 sector)
        val context = TaskContext(
            waypointIndex = index,
            allWaypoints = allWaypoints,
            previousWaypoint = if (index > 0) allWaypoints[index - 1] else null,
            nextWaypoint = if (index < allWaypoints.size - 1) allWaypoints[index + 1] else null
        )
        return keyholeDisplay.generateVisualGeometry(waypoint, context)
    }
}
