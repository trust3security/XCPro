package com.example.xcpro.tasks.racing

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.models.RacingWaypointRole
import com.example.xcpro.tasks.racing.models.RacingStartPointType
import com.example.xcpro.tasks.racing.models.RacingTurnPointType
import com.example.xcpro.tasks.racing.models.RacingFinishPointType
import com.example.xcpro.tasks.racing.turnpoints.*
import kotlin.math.*

/**
 * FAI Racing Task Calculator - Coordinator for turnpoint-specific calculators
 * Implements racing_task_spec.md requirements and CLAUDE.md separation
 * 
 * REFACTORED: Now delegates to specialized turnpoint calculators for each type
 */
class RacingTaskCalculator {
    
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0 // FAI Earth model
    }
    
    // Turnpoint calculators - each type has its own specialized implementation
    private val faiQuadrantCalculator = FAIQuadrantCalculator()
    private val cylinderCalculator = CylinderCalculator()
    private val keyholeCalculator = KeyholeCalculator()
    private val startLineCalculator = StartLineCalculator()
    
    /**
     * Calculate optimal FAI racing path - FIXED VERSION
     * Properly handles ALL turn point types, not just cylinders
     */
    fun findOptimalFAIPath(waypoints: List<RacingWaypoint>): List<Pair<Double, Double>> {
        val routePoints = mutableListOf<Pair<Double, Double>>()
        
        for (i in 0 until waypoints.size) {
            val currentWaypoint = waypoints[i]
            val nextWaypoint = if (i < waypoints.lastIndex) waypoints[i + 1] else null
            
            when {
                // START_LINE: Calculate optimal crossing point
                i == 0 && currentWaypoint.role == RacingWaypointRole.START &&
                currentWaypoint.startPointType == RacingStartPointType.START_LINE && nextWaypoint != null -> {
                    val context = TaskContext(
                        waypointIndex = i,
                        allWaypoints = waypoints,
                        previousWaypoint = null,
                        nextWaypoint = nextWaypoint
                    )
                    val optimalPoint = startLineCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                    routePoints.add(optimalPoint)
                }

                // START_CYLINDER: Use cylinder calculator
                i == 0 && currentWaypoint.role == RacingWaypointRole.START &&
                currentWaypoint.startPointType == RacingStartPointType.START_CYLINDER && nextWaypoint != null -> {
                    val context = TaskContext(
                        waypointIndex = i,
                        allWaypoints = waypoints,
                        previousWaypoint = null,
                        nextWaypoint = nextWaypoint
                    )
                    val optimalPoint = cylinderCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                    routePoints.add(optimalPoint)
                }

                
                // ALL TURNPOINT TYPES: Delegate to specialized calculators
                currentWaypoint.role == RacingWaypointRole.TURNPOINT && nextWaypoint != null -> {
                    val context = TaskContext(
                        waypointIndex = i,
                        allWaypoints = waypoints,
                        previousWaypoint = if (i > 0) waypoints[i - 1] else null,
                        nextWaypoint = nextWaypoint
                    )
                    
                    val touchPoint = when (currentWaypoint.turnPointType) {
                        RacingTurnPointType.TURN_POINT_CYLINDER -> {
                            // Use cylinder calculator to find optimal edge touch point
                            cylinderCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                        }
                        RacingTurnPointType.FAI_QUADRANT -> {
                            // Use FAI quadrant calculator to find optimal sector touch point
                            faiQuadrantCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                        }
                        RacingTurnPointType.KEYHOLE -> {
                            // Use keyhole calculator to find optimal touch point
                            keyholeCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                        }
                    }
                    routePoints.add(touchPoint)
                }
                
                // FINISH: Calculate optimal entry point - FIXED FOR EDGE ENTRY
                currentWaypoint.role == RacingWaypointRole.FINISH -> {
                    val previousWaypoint = if (i > 0) waypoints[i - 1] else null
                    val finishPoint = if (previousWaypoint != null) {
                        when (currentWaypoint.finishPointType) {
                            RacingFinishPointType.FINISH_CYLINDER -> {
                                // CRITICAL FIX: Use optimal entry point calculation for finish cylinders
                                // This places the finish at the edge of the cylinder, not center
                                cylinderCalculator.calculateOptimalEntryPoint(currentWaypoint, previousWaypoint)
                            }
                            RacingFinishPointType.FINISH_LINE -> {
                                // For finish line, use center point for now
                                Pair(currentWaypoint.lat, currentWaypoint.lon)
                            }
                            else -> Pair(currentWaypoint.lat, currentWaypoint.lon)
                        }
                    } else {
                        // No previous waypoint, use center
                        Pair(currentWaypoint.lat, currentWaypoint.lon)
                    }
                    routePoints.add(finishPoint)
                }
                
                // OTHER: Use center point
                else -> {
                    routePoints.add(Pair(currentWaypoint.lat, currentWaypoint.lon))
                }
            }
        }
        
        return routePoints
    }

    /**
     * Calculate distance from current GPS position to optimal entry point of target waypoint
     * Uses the SAME geometry calculators as the visual display to ensure accuracy
     *
     * CRITICAL FOR COMPETITION SAFETY: Must match visual display distance exactly!
     *
     * @param gpsLat Current GPS latitude
     * @param gpsLon Current GPS longitude
     * @param waypointIndex Index of target waypoint in task
     * @param waypoints Complete list of task waypoints
     * @return Distance in kilometers to optimal entry point, or null if calculation fails
     */
    fun calculateDistanceToOptimalEntry(
        gpsLat: Double,
        gpsLon: Double,
        waypointIndex: Int,
        waypoints: List<RacingWaypoint>
    ): Double? {
        if (waypointIndex < 0 || waypointIndex >= waypoints.size) {
            return null
        }

        val currentWaypoint = waypoints[waypointIndex]
        val previousWaypoint = if (waypointIndex > 0) waypoints[waypointIndex - 1] else null
        val nextWaypoint = if (waypointIndex < waypoints.lastIndex) waypoints[waypointIndex + 1] else null

        // Calculate optimal entry point based on waypoint type
        val optimalEntryPoint: Pair<Double, Double>? = when {
            // START_LINE: Calculate optimal crossing point
            currentWaypoint.role == RacingWaypointRole.START &&
            currentWaypoint.startPointType == RacingStartPointType.START_LINE && nextWaypoint != null -> {
                val context = TaskContext(
                    waypointIndex = waypointIndex,
                    allWaypoints = waypoints,
                    previousWaypoint = null,
                    nextWaypoint = nextWaypoint
                )
                startLineCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
            }

            // START_CYLINDER: Use cylinder calculator
            currentWaypoint.role == RacingWaypointRole.START &&
            currentWaypoint.startPointType == RacingStartPointType.START_CYLINDER && nextWaypoint != null -> {
                val context = TaskContext(
                    waypointIndex = waypointIndex,
                    allWaypoints = waypoints,
                    previousWaypoint = null,
                    nextWaypoint = nextWaypoint
                )
                cylinderCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
            }

            // TURNPOINT: Delegate to specialized calculator based on type
            currentWaypoint.role == RacingWaypointRole.TURNPOINT && nextWaypoint != null -> {
                val context = TaskContext(
                    waypointIndex = waypointIndex,
                    allWaypoints = waypoints,
                    previousWaypoint = previousWaypoint,
                    nextWaypoint = nextWaypoint
                )

                when (currentWaypoint.turnPointType) {
                    RacingTurnPointType.TURN_POINT_CYLINDER -> {
                        cylinderCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                    }
                    RacingTurnPointType.FAI_QUADRANT -> {
                        faiQuadrantCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                    }
                    RacingTurnPointType.KEYHOLE -> {
                        keyholeCalculator.calculateOptimalTouchPoint(currentWaypoint, context)
                    }
                }
            }

            // FINISH: Calculate optimal entry point
            currentWaypoint.role == RacingWaypointRole.FINISH && previousWaypoint != null -> {
                when (currentWaypoint.finishPointType) {
                    RacingFinishPointType.FINISH_CYLINDER -> {
                        cylinderCalculator.calculateOptimalEntryPoint(currentWaypoint, previousWaypoint)
                    }
                    RacingFinishPointType.FINISH_LINE -> {
                        Pair(currentWaypoint.lat, currentWaypoint.lon)
                    }
                    else -> Pair(currentWaypoint.lat, currentWaypoint.lon)
                }
            }

            // Fallback: Use waypoint center
            else -> Pair(currentWaypoint.lat, currentWaypoint.lon)
        }

        // Calculate haversine distance from GPS to optimal entry point
        return optimalEntryPoint?.let { (entryLat, entryLon) ->
            RacingGeometryUtils.haversineDistance(gpsLat, gpsLon, entryLat, entryLon)
        }
    }

}