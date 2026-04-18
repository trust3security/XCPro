package com.trust3.xcpro.tasks.racing.turnpoints

import com.trust3.xcpro.tasks.racing.models.RacingWaypoint
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Cylinder Display - Extracted from TaskManager.kt
 * 
 * Generates circular GeoJSON geometry for cylinder turnpoints.
 * Uses configurable radius from waypoint gateWidth property.
 */
class CylinderDisplay : TurnPointDisplay {
    
    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        val radiusMeters = waypoint.gateWidthMeters

        // SSOT: Use RacingGeometryUtils - single source of truth for all circle generation
        val circleCoordinates = RacingGeometryUtils.generateCircleCoordinatesArray(
            waypoint.lat,
            waypoint.lon,
            radiusMeters,
            numPoints = 64
        )

        // Use GSON to properly serialize coordinate arrays
        val gson = com.google.gson.Gson()
        val coordinatesJson = gson.toJson(listOf(circleCoordinates))

        return """
        {
            "type": "Feature",
            "properties": {
                "waypoint_index": ${context.waypointIndex},
                "type": "racing_cylinder",
                "radius": $radiusMeters,
                "role": "turnpoint"
            },
            "geometry": {
                "type": "Polygon",
                "coordinates": $coordinatesJson
            }
        }
        """.trimIndent()
    }

    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        return waypoint.gateWidthMeters
    }

    override fun getObservationZoneType(): String {
        return "racing_cylinder"
    }
}
