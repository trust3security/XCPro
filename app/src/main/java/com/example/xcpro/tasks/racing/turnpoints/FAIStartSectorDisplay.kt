package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeoJSONValidator
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * FAI Start Sector Display - Specialized for proper D-shape rendering
 *
 * This creates the correct "D-shaped" sector that "faces away from the first leg"
 * as specified in the Racing Task PRD. This is different from turnpoint FAI quadrants
 * which face toward the bisector of incoming/outgoing legs.
 *
 * START SECTOR ORIENTATION:
 * - 90° D-shaped sector
 * - Faces AWAY from the first leg (opposite direction)
 * - Arc connects the two 45° lines extending from the start point
 */
class FAIStartSectorDisplay : TurnPointDisplay {

    companion object {
        private const val DEFAULT_SECTOR_RADIUS_METERS = 10000.0 // 10km default for start sector
        private const val SECTOR_ANGLE_DEGREES = 90.0 // FAI standard
    }

    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        val nextWaypoint = context.nextWaypoint
        if (nextWaypoint == null) {
            println("⚠️ FAI START SECTOR: No next waypoint found, creating fallback circle")
            return createFallbackCircle(waypoint)
        }

        val radiusMeters = waypoint.gateWidth * 1000.0
        return generateSafeStartSector(waypoint, nextWaypoint, radiusMeters)
    }

    /**
     * Generate start sector with proper D-shape facing away from first leg
     */
    private fun generateSafeStartSector(
        startWaypoint: RacingWaypoint,
        nextWaypoint: RacingWaypoint,
        radiusMeters: Double
    ): String {
        try {
            // Calculate bearing from start to next waypoint (first leg direction)
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val firstLegBearing = RacingGeometryUtils.calculateBearing(
                startWaypoint.lat, startWaypoint.lon,
                nextWaypoint.lat, nextWaypoint.lon
            )

            // CRASH FIX: Validate bearing
            if (!firstLegBearing.isFinite()) {
                println("❌ FAI START SECTOR: Invalid bearing calculation")
                return createFallbackCircle(startWaypoint)
            }

            // FAI Start Sector opens TOWARD first leg direction (toward TP1)
            val sectorCenterBearing = firstLegBearing

            // Create FAI Start Sector: 90° sector opening toward first leg
            val halfSectorAngle = SECTOR_ANGLE_DEGREES / 2.0
            val leftBearing = normalizeAngle(sectorCenterBearing - halfSectorAngle)
            val rightBearing = normalizeAngle(sectorCenterBearing + halfSectorAngle)

            // Generate FAI Start Sector geometry: arc + straight line
            val coordinates = mutableListOf<List<Double>>()

            // Add the straight line edge (at the start waypoint - the "D" flat edge)
            coordinates.add(listOf(startWaypoint.lon, startWaypoint.lat))

            // Generate arc points from left to right bearing
            val arcSteps = 20 // Smooth arc
            for (i in 0..arcSteps) {
                val progress = i.toDouble() / arcSteps
                val currentBearing = leftBearing + (normalizeAngleDiff(rightBearing - leftBearing) * progress)

                // Use shared RacingGeometryUtils - single algorithm for display and calculations
                val arcPoint = RacingGeometryUtils.calculateDestinationPoint(
                    startWaypoint.lat, startWaypoint.lon,
                    currentBearing, radiusMeters
                )

                coordinates.add(listOf(arcPoint.second, arcPoint.first)) // [lon, lat]
            }

            // Close the FAI Start Sector back to start
            coordinates.add(listOf(startWaypoint.lon, startWaypoint.lat))

            // Create GeoJSON Feature with proper type identification
            val geoJsonFeature = """
                {
                    "type": "Feature",
                    "properties": {
                        "type": "racing_fai_start_sector",
                        "radius": $radiusMeters,
                        "role": "start"
                    },
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [[${coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }}]]
                    }
                }
            """.trimIndent()

            // Validate the GeoJSON feature
            if (!RacingGeoJSONValidator.validateGeoJSON(geoJsonFeature, "FAI Start Sector")) {
                println("❌ FAI START SECTOR: Generated invalid geometry, using fallback")
                return createFallbackCircle(startWaypoint)
            }

            return geoJsonFeature

        } catch (e: Exception) {
            println("❌ FAI START SECTOR generation failed: ${e.message}")
            return createFallbackCircle(startWaypoint)
        }
    }

    private fun createFallbackCircle(waypoint: RacingWaypoint): String {
        val radiusMeters = DEFAULT_SECTOR_RADIUS_METERS
        val steps = 16
        val coordinates = mutableListOf<List<Double>>()

        for (i in 0..steps) {
            val angle = (i.toDouble() / steps) * 360.0
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val point = RacingGeometryUtils.calculateDestinationPoint(
                waypoint.lat, waypoint.lon,
                angle, radiusMeters
            )
            coordinates.add(listOf(point.second, point.first)) // [lon, lat]
        }

        return """
            {
                "type": "Polygon",
                "coordinates": [[${coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }}]]
            }
        """.trimIndent()
    }

    // Utility functions for angle normalization
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    private fun normalizeAngleDiff(angleDiff: Double): Double {
        var diff = angleDiff
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return diff
    }

    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        // Convert km to meters for display
        return waypoint.gateWidth * 1000.0
    }

    override fun getObservationZoneType(): String {
        return "FAI Start Sector"
    }
}