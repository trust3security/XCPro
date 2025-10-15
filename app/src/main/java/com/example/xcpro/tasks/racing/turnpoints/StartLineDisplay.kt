package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Start Line Display for Racing Tasks
 *
 * Generates FAI-compliant visual representation of start lines for racing tasks.
 * Start line is a pure perpendicular line to the track to first turnpoint.
 * No D-shape or arc - complies with start line geometry rules.
 */
class StartLineDisplay : TurnPointDisplay {

    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        val gateWidthKm = waypoint.gateWidth
        val gateWidthMeters = gateWidthKm * 1000.0

        // Calculate direction to next waypoint
        val bearing = if (context.nextWaypoint != null) {
            RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, context.nextWaypoint.lat, context.nextWaypoint.lon)
        } else {
            0.0 // Default direction if no next waypoint
        }

        // Start line is perpendicular to the bearing
        val perpBearing = (bearing + 90.0) % 360.0
        val halfWidth = gateWidthMeters / 2.0

        // Calculate both ends of the start line (perpendicular to task direction)
        val point1 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, perpBearing, halfWidth)
        val point2 = RacingGeometryUtils.calculateDestinationPoint(waypoint.lat, waypoint.lon, (perpBearing + 180.0) % 360.0, halfWidth)

        // Create pure perpendicular start line (FAI compliant - no arc!)
        val startLineCoordinates = listOf(
            listOf(point1.second, point1.first), // [lon, lat] for GeoJSON
            listOf(point2.second, point2.first)  // [lon, lat] for GeoJSON
        )

        // Use GSON to properly serialize coordinate arrays
        val gson = com.google.gson.Gson()

        // Create pure perpendicular start line (FAI compliant)
        return """
        {
            "type": "Feature",
            "properties": {
                "waypoint_index": ${context.waypointIndex},
                "type": "racing_start_line",
                "length": $gateWidthMeters,
                "bearing": $bearing,
                "perpendicular_bearing": $perpBearing,
                "role": "start"
            },
            "geometry": {
                "type": "LineString",
                "coordinates": ${gson.toJson(startLineCoordinates)}
            }
        }
        """.trimIndent()
    }

    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        // For start lines, use half the line length as the "radius" for bounds calculation
        return waypoint.gateWidth * 1000.0 / 2.0 // Convert km to meters and take half
    }

    override fun getObservationZoneType(): String {
        return "Start Line"
    }
}
