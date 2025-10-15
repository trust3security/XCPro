package com.example.xcpro.tasks.racing.turnpoints

import android.util.Log
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Finish Line Display for Racing Tasks
 *
 * Generates visual representation of finish lines for racing tasks.
 * Finish line is perpendicular to the track from previous turnpoint.
 */
class FinishLineDisplay : TurnPointDisplay {

    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        val gateWidthKm = waypoint.gateWidth
        val gateWidthMeters = gateWidthKm * 1000.0

        // Log waypoint information for FAI compliance verification
        Log.d("FinishLineDisplay", "=== FAI FINISH LINE CALCULATION ===")
        Log.d("FinishLineDisplay", "Finish waypoint: ${waypoint.title} at (${waypoint.lat}, ${waypoint.lon})")
        Log.d("FinishLineDisplay", "Gate width: ${gateWidthKm}km (${gateWidthMeters}m)")

        if (context.previousWaypoint != null) {
            Log.d("FinishLineDisplay", "Previous waypoint: ${context.previousWaypoint.title} at (${context.previousWaypoint.lat}, ${context.previousWaypoint.lon})")
        } else {
            Log.w("FinishLineDisplay", "No previous waypoint found - using default bearing 0°")
        }

        // Calculate direction from previous waypoint (final task leg)
        val bearing = if (context.previousWaypoint != null) {
            val calculatedBearing = RacingGeometryUtils.calculateBearing(context.previousWaypoint.lat, context.previousWaypoint.lon, waypoint.lat, waypoint.lon)
            Log.d("FinishLineDisplay", "Final task leg bearing: ${String.format("%.2f", calculatedBearing)}° (from previous to finish)")
            calculatedBearing
        } else {
            Log.w("FinishLineDisplay", "Using default bearing: 0° (no previous waypoint)")
            0.0 // Default direction if no previous waypoint
        }

        // Finish line is perpendicular to the bearing (FAI Rule: perpendicular to final task leg)
        val perpBearing = (bearing + 90.0) % 360.0
        Log.d("FinishLineDisplay", "Perpendicular bearing: ${String.format("%.2f", perpBearing)}° (${String.format("%.2f", bearing)} + 90°)")
        Log.d("FinishLineDisplay", "FAI Compliance: ✓ Finish line perpendicular to final task leg")

        val halfWidth = gateWidthMeters / 2.0
        Log.d("FinishLineDisplay", "Half width: ${halfWidth}m")

        // Calculate both ends of the finish line
        val point1 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, perpBearing, halfWidth)
        val point2 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, (perpBearing + 180.0) % 360.0, halfWidth)

        Log.d("FinishLineDisplay", "Finish line Point1: (${String.format("%.6f", point1.first)}, ${String.format("%.6f", point1.second)})")
        Log.d("FinishLineDisplay", "Finish line Point2: (${String.format("%.6f", point2.first)}, ${String.format("%.6f", point2.second)})")

        // Validate line length
        val calculatedLength = RacingGeometryUtils.haversineDistance(point1.first, point1.second, point2.first, point2.second) * 1000.0 // Convert km to meters
        Log.d("FinishLineDisplay", "Calculated line length: ${String.format("%.2f", calculatedLength)}m (expected: ${gateWidthMeters}m)")

        if (Math.abs(calculatedLength - gateWidthMeters) < 1.0) {
            Log.d("FinishLineDisplay", "FAI Compliance: ✓ Line length matches gate width")
        } else {
            Log.w("FinishLineDisplay", "FAI Warning: Line length mismatch - difference: ${String.format("%.2f", Math.abs(calculatedLength - gateWidthMeters))}m")
        }

        Log.d("FinishLineDisplay", "=== END FAI FINISH LINE CALCULATION ===\n")

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
        return waypoint.gateWidth * 1000.0 / 2.0 // Convert km to meters and take half
    }

    override fun getObservationZoneType(): String {
        return "Finish Line"
    }
}