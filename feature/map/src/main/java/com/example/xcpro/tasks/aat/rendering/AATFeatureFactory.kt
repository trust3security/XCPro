package com.example.xcpro.tasks.aat.rendering

import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.getAuthorityRadius
import com.example.xcpro.tasks.aat.geometry.AATGeometryGenerator

/**
 * AAT Feature Factory - GeoJSON feature creation for AAT tasks
 *
 * Handles creation of all GeoJSON features for AAT visualization:
 * - Line features (start/finish lines)
 * - Circle features (cylinders, assigned areas)
 * - Sector features (sectors, keyholes)
 *
 * Extracted from AATTaskRenderer.kt for file size compliance.
 *
 * SSOT COMPLIANT: Uses AATGeometryGenerator for all geometry calculations.
 */
internal class AATFeatureFactory(
    private val geometryGenerator: AATGeometryGenerator
) {

    /**
     * Create GeoJSON feature for a start/finish line
     */
    fun createLineFeature(waypoint: AATWaypoint, coordinates: List<List<Double>>, type: String, role: String): String {
        // SSOT FIX: Use authority instead of removed gateWidth property
        val lineWidth = waypoint.getAuthorityRadius()
        return """
        {
            "type": "Feature",
            "properties": {
                "title": "${waypoint.title} ${if (role == "START") "Start" else "Finish"} Line",
                "type": "$type",
                "role": "$role",
                "width": $lineWidth
            },
            "geometry": {
                "type": "LineString",
                "coordinates": [${coordinates.joinToString(", ") { "[${it[0]}, ${it[1]}]" }}]
            }
        }
        """.trimIndent()
    }

    /**
     * Create GeoJSON feature for a circle (cylinder, assigned area)
     */
    fun createCircleFeature(waypoint: AATWaypoint, coordinates: List<List<Double>>, radius: Double, type: String, role: String?): String {
        val roleProperty = role?.let { "\"role\": \"$it\"," } ?: ""
        return """
        {
            "type": "Feature",
            "properties": {
                "title": "${waypoint.title}",
                "type": "$type",
                $roleProperty
                "radius": $radius
            },
            "geometry": {
                "type": "Polygon",
                "coordinates": [[${coordinates.map { "[${it[0]}, ${it[1]}]" }.joinToString(", ")}]]
            }
        }
        """.trimIndent()
    }

    /**
     * Create GeoJSON feature for a sector
     * NEW: Support for AAT sectors and keyholes
     */
    fun createSectorFeature(waypoint: AATWaypoint, coordinates: List<List<Double>>, type: String, role: String?): String {
        val roleProperty = role?.let { "\"role\": \"$it\"," } ?: ""
        val innerRadius = waypoint.assignedArea.innerRadiusMeters / 1000.0
        val outerRadius = waypoint.assignedArea.outerRadiusMeters / 1000.0
        return """
        {
            "type": "Feature",
            "properties": {
                "title": "${waypoint.title}",
                "type": "$type",
                $roleProperty
                "innerRadius": $innerRadius,
                "outerRadius": $outerRadius,
                "startBearing": ${waypoint.assignedArea.startAngleDegrees},
                "endBearing": ${waypoint.assignedArea.endAngleDegrees}
            },
            "geometry": {
                "type": "Polygon",
                "coordinates": [[${coordinates.map { "[${it[0]}, ${it[1]}]" }.joinToString(", ")}]]
            }
        }
        """.trimIndent()
    }

    /**
     * Generate sector/keyhole polygon coordinates
     * FIXED: Proper keyhole shape (cylinder + sector extension)
     */
    fun generateSectorCoordinates(
        centerLat: Double,
        centerLon: Double,
        innerRadiusKm: Double,
        outerRadiusKm: Double,
        startBearingDeg: Double,
        endBearingDeg: Double
    ): List<List<Double>> {
        val coords = mutableListOf<List<Double>>()

        fun sweepAngles(start: Double, end: Double, steps: Int): List<Double> {
            val range = if (end >= start) end - start else 360.0 - start + end
            return (0..steps).map { i -> (start + range * (i.toDouble() / steps)) % 360.0 }
        }

        if (innerRadiusKm > 0.0) {
            // Annular sector ring (outer arc start->end, inner arc end->start) like racing keyhole
            val steps = 64
            sweepAngles(startBearingDeg, endBearingDeg, steps).forEach { ang ->
                val p = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, ang, outerRadiusKm)
                coords.add(listOf(p.second, p.first))
            }
            sweepAngles(endBearingDeg, startBearingDeg, steps).forEach { ang ->
                val p = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, ang, innerRadiusKm)
                coords.add(listOf(p.second, p.first))
            }

        } else {
            // SECTOR: No inner radius - standard sector from center
            val numPoints = 48

            // Start from center
            coords.add(listOf(centerLon, centerLat))

            // Generate outer arc
            sweepAngles(startBearingDeg, endBearingDeg, numPoints).forEach { bearing ->
                val point = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, bearing, outerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Connect back to center
            coords.add(listOf(centerLon, centerLat))
        }

        // Close the polygon
        if (coords.isNotEmpty()) {
            coords.add(coords[0])
        }

        return coords
    }
}
