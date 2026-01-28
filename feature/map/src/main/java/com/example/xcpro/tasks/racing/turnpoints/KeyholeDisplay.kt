package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * Configurable Keyhole Display - Enhanced flexibility for racing tasks
 *
 * Configurable Keyhole specification:
 * - Inner radius: user sets via keyholeInnerRadius (cylinder part)
 * - Outer radius: user sets via gateWidth (sector outer radius)
 * - Angle: user sets via keyholeAngle (sector angle, default 90 deg)
 * - Sector oriented perpendicular to track bisector, pointing outward
 * - Much more flexible than fixed FAI implementation
 */
class KeyholeDisplay : TurnPointDisplay {
    
    
    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        return try {
            println("KEYHOLE DEBUG: Starting keyhole generation for waypoint ${waypoint.title}")

            // Configurable Keyhole: Use flexible parameters
            val cylinderRadiusMeters = waypoint.keyholeInnerRadius * 1000.0 // Inner cylinder radius in meters
            val sectorRadiusMeters = waypoint.gateWidth * 1000.0 // Outer sector radius in meters
            val sectorAngleDegrees = waypoint.keyholeAngle.let { angle ->
                if (abs(angle - 90.0) < 1e-3) 90.0 else angle
            } // Clean up float precision (89.9999 -> 90)

            println("KEYHOLE DEBUG: Params - Inner:${cylinderRadiusMeters}m, Outer:${sectorRadiusMeters}m, Angle:${sectorAngleDegrees} deg")

            // Use sector calculation with configurable angle
            val bisectorBearing = KeyholeGeometry.calculateSectorBisector(waypoint, context)
            val halfAngle = sectorAngleDegrees / 2.0
            val startAngle = (bisectorBearing - halfAngle + 360.0) % 360.0
            val endAngle = (bisectorBearing + halfAngle) % 360.0

            println("KEYHOLE DEBUG: Angles - Bisector:${bisectorBearing} deg, Start:${startAngle} deg, End:${endAngle} deg")

            // Build one closed ring matching the keyhole shape (outer arc + inner arc), no separate shapes
            val outerArc = KeyholeGeometry.generateSectorCoordinatesArray(
                waypoint.lat,
                waypoint.lon,
                sectorRadiusMeters,
                startAngle,
                endAngle,
                includeCenter = false
            )
            val innerArc = KeyholeGeometry.generateSectorCoordinatesArray(
                waypoint.lat,
                waypoint.lon,
                cylinderRadiusMeters,
                endAngle,
                startAngle,
                includeCenter = false
            )
            val ring = mutableListOf<List<Double>>().apply {
                addAll(outerArc)
                addAll(innerArc)
                if (isNotEmpty() && first() != last()) add(first())
            }

            val gson = com.google.gson.Gson()
            val ringJson = gson.toJson(listOf(ring))

            println("dY` KEYHOLE DEBUG: Ring pts=${ring.size} (outer=${outerArc.size} inner=${innerArc.size})")

            // Single Feature Polygon (annular sector) so renderer sees one geometry
            val geoJson = """
            {
              "type": "Feature",
              "properties": {
                "waypoint_index": ${context.waypointIndex},
                "type": "racing_keyhole",
                "cylinder_radius": $cylinderRadiusMeters,
                "sector_radius": $sectorRadiusMeters,
                "sector_angle": $sectorAngleDegrees,
                "bisector_bearing": $bisectorBearing,
                "role": "turnpoint"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": $ringJson
              }
            }
            """.trimIndent()

            println("KEYHOLE DEBUG: Generated GeoJSON successfully")
            geoJson
        } catch (e: Exception) {
            println("KEYHOLE ERROR: ${e.message}")
            println("KEYHOLE ERROR: Stack trace: ${e.stackTrace.contentToString()}")
            // Return a simple cylinder as fallback
            """
            {
                "type": "Feature",
                "properties": {
                    "waypoint_index": ${context.waypointIndex},
                    "type": "racing_cylinder_fallback"
                },
                "geometry": {
                    "type": "Point",
                    "coordinates": [${waypoint.lon}, ${waypoint.lat}]
                }
            }
            """.trimIndent()
        }
    }
    
    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        // Configurable Keyhole: Return outer sector radius for display bounds
        return waypoint.gateWidth * 1000.0 // Convert km to meters
    }
    
    override fun getObservationZoneType(): String {
        return "racing_keyhole"
    }
}

