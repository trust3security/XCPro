package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.core.common.logging.AppLogger
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import kotlin.math.*

/**
 * Finish Line Display for Racing Tasks
 *
 * Generates visual representation of finish lines for racing tasks.
 * Finish line is perpendicular to the track from previous turnpoint.
 */
class FinishLineDisplay : TurnPointDisplay {
    companion object {
        private const val TAG = "FinishLineDisplay"
    }

    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        val gateWidthMeters = waypoint.gateWidthMeters

        if (context.previousWaypoint == null) {
            AppLogger.w(TAG, "No previous waypoint found; using default finish-line bearing")
        }

        // Calculate direction from previous waypoint (final task leg)
        val bearing = if (context.previousWaypoint != null) {
            val calculatedBearing = RacingGeometryUtils.calculateBearing(
                context.previousWaypoint.lat,
                context.previousWaypoint.lon,
                waypoint.lat,
                waypoint.lon
            )
            calculatedBearing
        } else {
            0.0 // Default direction if no previous waypoint
        }

        // Finish line is perpendicular to the bearing (FAI Rule: perpendicular to final task leg)
        val perpBearing = (bearing + 90.0) % 360.0

        val halfWidth = gateWidthMeters / 2.0

        // Calculate both ends of the finish line
        val point1 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, perpBearing, halfWidth)
        val point2 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, (perpBearing + 180.0) % 360.0, halfWidth)

        // Validate line length
        val calculatedLength = RacingGeometryUtils.haversineDistanceMeters(point1.first, point1.second, point2.first, point2.second)
        val lengthDeltaMeters = abs(calculatedLength - gateWidthMeters)

        if (lengthDeltaMeters >= 1.0) {
            AppLogger.w(
                TAG,
                "Finish line length mismatch: delta=${String.format("%.2f", lengthDeltaMeters)}m"
            )
        }

        AppLogger.dRateLimited(TAG, "finish_line_geometry", 5_000L) {
            "Finish line geometry computed: gateWidth=${String.format("%.2f", gateWidthMeters)}m, " +
                "hasPrevious=${context.previousWaypoint != null}, " +
                "bearing=${String.format("%.2f", bearing)}, " +
                "perpendicular=${String.format("%.2f", perpBearing)}, " +
                "length=${String.format("%.2f", calculatedLength)}m, " +
                "lengthDelta=${String.format("%.2f", lengthDeltaMeters)}m"
        }

        return """
        {
            "type": "Feature",
            "properties": {
                "waypoint_index": ${context.waypointIndex},
                "type": "racing_finish_line",
                "role": "finish",
                "length": $gateWidthMeters,
                "bearing": $bearing,
                "perpendicular_bearing": $perpBearing,
                "fai_compliance": {
                    "perpendicular_to_final_leg": true,
                    "final_leg_bearing": $bearing,
                    "line_bearing": $perpBearing,
                    "angle_difference": 90.0,
                    "calculated_length": ${String.format("%.2f", calculatedLength)},
                    "expected_length": $gateWidthMeters
                },
                "debug_info": {
                    "previous_waypoint": "${context.previousWaypoint?.title ?: "none"}",
                    "finish_waypoint": "${waypoint.title}",
                    "calculation_method": "bearing + 90 degrees"
                }
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [
                    [${point1.second}, ${point1.first}],
                    [${point2.second}, ${point2.first}]
                ]
            }
        }
        """.trimIndent()
    }

    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        // For finish lines, use half the line length as the "radius" for bounds calculation
        return waypoint.gateWidthMeters / 2.0
    }

    override fun getObservationZoneType(): String {
        return "Finish Line"
    }
}
