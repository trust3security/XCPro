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
        // ✅ SSOT FIX: Use authority instead of removed gateWidth property
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
     * ✅ NEW: Support for AAT sectors and keyholes
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
     * ✅ FIXED: Proper keyhole shape (cylinder + sector extension)
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

        if (innerRadiusKm > 0.0) {
            // ✅ KEYHOLE: Cylinder + sector extension (true keyhole shape 🔑)
            val cylinderPoints = 72
            val sectorPoints = 45

            // Step 1: Draw cylinder outline (the part NOT covered by sector)
            // Start from sector end, go around to sector start
            val startDrawAngle = endBearingDeg
            val endDrawAngle = startBearingDeg + 360.0

            for (i in 0..cylinderPoints) {
                val angleProgress = i.toDouble() / cylinderPoints
                val currentAngle = startDrawAngle + angleProgress * (endDrawAngle - startDrawAngle)
                val normalizedAngle = currentAngle % 360.0

                val point = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, normalizedAngle, innerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Step 2: Connect to sector outer boundary at start angle
            val sectorOuterStart = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, startBearingDeg, outerRadiusKm)
            coords.add(listOf(sectorOuterStart.second, sectorOuterStart.first))

            // Step 3: Draw the sector outer arc
            for (i in 1 until sectorPoints) {
                val angleProgress = i.toDouble() / sectorPoints
                val angle = startBearingDeg + angleProgress * (endBearingDeg - startBearingDeg)
                val point = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, angle, outerRadiusKm)
                coords.add(listOf(point.second, point.first))
            }

            // Step 4: Connect to sector outer boundary at end angle
            val sectorOuterEnd = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, endBearingDeg, outerRadiusKm)
            coords.add(listOf(sectorOuterEnd.second, sectorOuterEnd.first))

            // Step 5: Connect back to cylinder edge at sector end angle (closes the keyhole)
            val cylinderSectorEnd = geometryGenerator.calculateDestinationPoint(centerLat, centerLon, endBearingDeg, innerRadiusKm)
            coords.add(listOf(cylinderSectorEnd.second, cylinderSectorEnd.first))

        } else {
            // ✅ SECTOR: No inner radius - standard sector from center
            val numPoints = 32

            // Calculate sector span
            val sectorSpan = if (endBearingDeg >= startBearingDeg) {
                endBearingDeg - startBearingDeg
            } else {
                360.0 - startBearingDeg + endBearingDeg
            }

            // Start from center
            coords.add(listOf(centerLon, centerLat))

            // Generate outer arc
            for (i in 0..numPoints) {
                val fraction = i.toDouble() / numPoints
                val bearing = if (endBearingDeg >= startBearingDeg) {
                    startBearingDeg + fraction * (endBearingDeg - startBearingDeg)
                } else {
                    (startBearingDeg + fraction * sectorSpan) % 360.0
                }
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
