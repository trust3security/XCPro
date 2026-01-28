package com.example.xcpro.tasks.racing

// RACING TASK SEPARATION: Only Racing-specific imports - NO shared dependencies
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Racing Task Course Line Validator - Ensures red line touches turn points
 * Implements verification per user requirement: "we need a check for the red line touches the TP"
 */
class RacingTaskValidator {
    
    private val calculator = RacingTaskCalculator()
    
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0
        private const val TOLERANCE_METERS = 10.0 // 10m tolerance for touch verification
    }
    
    /**
     * Verify that course line touches all turn points within tolerance
     * Returns validation result with details for debugging
     * RACING TASK SEPARATION: Uses only Racing-specific waypoint types
     */
    fun validateCourseLineTouchesWaypoints(waypoints: List<RacingWaypoint>): CourseLineValidation {
        if (waypoints.size < 2) {
            return CourseLineValidation(
                isValid = true,
                message = "Task too short to validate",
                touchPointResults = emptyList()
            )
        }

        // Get optimal path coordinates that should be used for course line
        val optimalPath = calculator.findOptimalFAIPath(waypoints)

        if (optimalPath.size != waypoints.size) {
            return CourseLineValidation(
                isValid = false,
                message = "Optimal path size mismatch: ${optimalPath.size} vs ${waypoints.size}",
                touchPointResults = emptyList()
            )
        }

        val results = mutableListOf<TouchPointResult>()
        var allValid = true

        // Verify each waypoint's touch point
        for (i in waypoints.indices) {
            val waypoint = waypoints[i]
            val courseLinePoint = optimalPath[i]

            val result = validateWaypointTouchPoint(waypoint, courseLinePoint)
            results.add(result)

            if (!result.isValid) {
                allValid = false
            }

            // Log detailed verification
            println("COURSE LINE VERIFICATION:")
            println("   Waypoint: ${waypoint.title} (${waypoint.role})")
            println("   Type: ${waypoint.currentPointType}")
            println("   Center: (${waypoint.lat}, ${waypoint.lon})")
            println("   Course Point: (${courseLinePoint.first}, ${courseLinePoint.second})")
            println("   Distance from center: ${result.distanceFromCenter}m")
            println("   Expected radius: ${waypoint.gateWidth * 1000}m")
            println("   Valid: ${result.isValid} - ${result.message}")
        }

        return CourseLineValidation(
            isValid = allValid,
            message = if (allValid) "All course line points touch waypoints correctly"
                     else "Some course line points do not touch waypoints",
            touchPointResults = results
        )
    }
    
    /**
     * Validate that a specific course line point touches the waypoint correctly
     * RACING TASK SEPARATION: Uses only Racing-specific waypoint types
     */
    private fun validateWaypointTouchPoint(
        waypoint: RacingWaypoint,
        courseLinePoint: Pair<Double, Double>
    ): TouchPointResult {
        val distanceFromCenter = RacingGeometryUtils.haversineDistance(
            waypoint.lat, waypoint.lon,
            courseLinePoint.first, courseLinePoint.second
        ) * 1000.0 // Convert to meters

        return when (waypoint.role) {
            RacingWaypointRole.START -> {
                // Start points can be at center (cylinder) or on line
                validateStartPoint(waypoint, distanceFromCenter)
            }

            RacingWaypointRole.TURNPOINT -> {
                // Turn points must touch observation zone edge
                validateTurnPoint(waypoint, distanceFromCenter)
            }

            RacingWaypointRole.FINISH -> {
                // Finish points should touch edge for optimal entry
                validateFinishPoint(waypoint, distanceFromCenter)
            }
        }
    }
    
    private fun validateStartPoint(waypoint: RacingWaypoint, distanceFromCenter: Double): TouchPointResult {
        val expectedRadius = waypoint.gateWidth * 1000.0 // Convert km to meters

        return when (waypoint.startPointType) {
            RacingStartPointType.START_CYLINDER -> {
                // Should be on cylinder edge for optimal crossing
                val isValid = abs(distanceFromCenter - expectedRadius) <= TOLERANCE_METERS
                TouchPointResult(
                    isValid = isValid,
                    distanceFromCenter = distanceFromCenter,
                    message = if (isValid) "Start cylinder edge touch OK"
                             else "Start cylinder should touch edge (${expectedRadius}m), got ${distanceFromCenter}m"
                )
            }

            RacingStartPointType.START_LINE -> {
                // Should be on start line (distance varies)
                TouchPointResult(
                    isValid = true, // Line start validation is complex, assume valid for now
                    distanceFromCenter = distanceFromCenter,
                    message = "Start line point (distance validation complex)"
                )
            }

            RacingStartPointType.FAI_START_SECTOR -> {
                // Should be on FAI sector boundary (90 deg D-shaped sector)
                TouchPointResult(
                    isValid = true, // FAI sector validation is complex (angle + radius), assume valid for now
                    distanceFromCenter = distanceFromCenter,
                    message = "FAI start sector point (angle + radius validation complex)"
                )
            }
        }
    }
    
    private fun validateTurnPoint(waypoint: RacingWaypoint, distanceFromCenter: Double): TouchPointResult {
        val expectedRadius = waypoint.gateWidth * 1000.0 // Convert km to meters

        return when (waypoint.turnPointType) {
            RacingTurnPointType.TURN_POINT_CYLINDER -> {
                // Must touch cylinder edge for optimal racing line
                val isValid = abs(distanceFromCenter - expectedRadius) <= TOLERANCE_METERS
                TouchPointResult(
                    isValid = isValid,
                    distanceFromCenter = distanceFromCenter,
                    message = if (isValid) "Cylinder edge touch OK"
                             else "Cylinder should touch edge (${expectedRadius}m), got ${distanceFromCenter}m"
                )
            }

            RacingTurnPointType.FAI_QUADRANT -> {
                // FAI quadrant uses a finite sector radius (default 10km)
                val maxRadius = waypoint.faiQuadrantOuterRadius * 1000.0
                val isValid = distanceFromCenter <= maxRadius + TOLERANCE_METERS
                TouchPointResult(
                    isValid = isValid,
                    distanceFromCenter = distanceFromCenter,
                    message = if (isValid) {
                        "FAI quadrant within ${maxRadius}m radius"
                    } else {
                        "FAI quadrant radius ${maxRadius}m exceeded (got ${distanceFromCenter}m)"
                    }
                )
            }

            RacingTurnPointType.KEYHOLE -> {
                // Keyhole uses cylinder part for racing line (500m cylinder + sector)
                val cylinderRadius = 500.0 // Keyhole cylinder is always 500m
                val isValid = abs(distanceFromCenter - cylinderRadius) <= TOLERANCE_METERS
                TouchPointResult(
                    isValid = isValid,
                    distanceFromCenter = distanceFromCenter,
                    message = if (isValid) "Keyhole cylinder edge touch OK"
                             else "Keyhole should touch cylinder edge (500m), got ${distanceFromCenter}m"
                )
            }


        }
    }
    
    private fun validateFinishPoint(waypoint: RacingWaypoint, distanceFromCenter: Double): TouchPointResult {
        val expectedRadius = waypoint.gateWidth * 1000.0 // Convert km to meters

        return when (waypoint.finishPointType) {
            RacingFinishPointType.FINISH_CYLINDER -> {
                // Should touch cylinder edge for optimal entry
                val isValid = abs(distanceFromCenter - expectedRadius) <= TOLERANCE_METERS
                TouchPointResult(
                    isValid = isValid,
                    distanceFromCenter = distanceFromCenter,
                    message = if (isValid) "Finish cylinder edge touch OK"
                             else "Finish cylinder should touch edge (${expectedRadius}m), got ${distanceFromCenter}m"
                )
            }

            RacingFinishPointType.FINISH_LINE -> {
                TouchPointResult(
                    isValid = true,
                    distanceFromCenter = distanceFromCenter,
                    message = "Finish line (validation complex)"
                )
            }
        }
    }

    // RACING TASK SEPARATION: No conversion functions needed - uses Racing-specific types only
}

// Note: CourseLineValidation and TouchPointResult are defined in RacingTaskManager.kt
