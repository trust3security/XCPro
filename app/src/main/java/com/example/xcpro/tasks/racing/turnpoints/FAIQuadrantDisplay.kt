package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.models.RacingWaypoint
import com.example.xcpro.tasks.racing.RacingGeoJSONValidator
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.*

/**
 * FAI Quadrant Display - CORRECTED VERSION
 * 
 * This fixes the critical bug where visual FAI sector orientation didn't match
 * the mathematical calculations in RacingTaskCalculator.kt.
 * 
 * Uses the SAME corrected orientation algorithm as FAIQuadrantCalculator.kt
 */
class FAIQuadrantDisplay : TurnPointDisplay {
    
    companion object {
        private const val DEFAULT_DISPLAY_RADIUS_METERS = 20000.0 // 20km default for FAI quadrant visibility
    }
    
    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        try {
            val previousWaypoint = context.previousWaypoint
            val nextWaypoint = context.nextWaypoint

            // Validate input coordinates first
            if (!waypoint.lat.isFinite() || !waypoint.lon.isFinite()) {
                println("❌ FAI QUADRANT: Invalid input coordinates, using fallback")
                return createFallbackGeometry(waypoint, context)
            }

            // Use configurable display radius from waypoint (convert km to meters)
            val displayRadiusMeters = waypoint.faiQuadrantOuterRadius * 1000.0

            // CRASH FIX: Validate radius
            if (!displayRadiusMeters.isFinite() || displayRadiusMeters <= 0) {
                println("❌ FAI QUADRANT: Invalid radius ${displayRadiusMeters}m, using default")
                val fallbackRadius = DEFAULT_DISPLAY_RADIUS_METERS
                return generateSafeGeometry(waypoint, context, fallbackRadius)
            }

            // Use the CORRECTED sector bisector calculation with validation
            val bisectorBearing = calculateCorrectedSectorBisector(waypoint, previousWaypoint, nextWaypoint)

            // CRASH FIX: Validate bearing calculation
            if (!bisectorBearing.isFinite()) {
                println("❌ FAI QUADRANT: Invalid bisector bearing, using fallback")
                return createFallbackGeometry(waypoint, context)
            }

            // FAI quadrant: 45 degrees each side of bisector
            val startAngle = (bisectorBearing - 45.0 + 360.0) % 360.0
            val endAngle = (bisectorBearing + 45.0) % 360.0

            return generateSafeGeometry(waypoint, context, displayRadiusMeters, startAngle, endAngle, bisectorBearing)

        } catch (e: Exception) {
            println("❌ FAI QUADRANT: Exception in generateVisualGeometry: ${e.message}")
            return createFallbackGeometry(waypoint, context)
        }
    }

    /**
     * Generate safe geometry with full validation
     */
    private fun generateSafeGeometry(
        waypoint: RacingWaypoint,
        context: TaskContext,
        displayRadiusMeters: Double,
        startAngle: Double = 0.0,
        endAngle: Double = 90.0,
        bisectorBearing: Double = 45.0
    ): String {
        try {
            val sectorCoordinates = generateSectorCoordinates(
                waypoint.lat, waypoint.lon,
                displayRadiusMeters, startAngle, endAngle
            )

            // Validate generated coordinates before creating GeoJSON
            if (sectorCoordinates.isEmpty() || sectorCoordinates == "[]") {
                println("⚠️ FAI QUADRANT: Empty sector coordinates generated, trying fallback")
                return createFallbackGeometry(waypoint, context)
            }

            val geoJson = """
            {
                "type": "Feature",
                "properties": {
                    "waypoint_index": ${context.waypointIndex},
                    "type": "racing_fai_quadrant",
                    "role": "turnpoint",
                    "radius": $displayRadiusMeters,
                    "bisector_bearing": $bisectorBearing,
                    "infinite_radius": true
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[$sectorCoordinates]]
                }
            }
            """.trimIndent()

            // Log geometry generation success and return
            println("✅ FAI QUADRANT: Generated GeoJSON for ${waypoint.title} with radius ${displayRadiusMeters}m")
            return geoJson

        } catch (e: Exception) {
            println("❌ FAI QUADRANT: Exception in generateSafeGeometry: ${e.message}")
            return createFallbackGeometry(waypoint, context)
        }
    }

    /**
     * Create fallback geometry for failed cases
     */
    private fun createFallbackGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        // Use validator to create safe fallback
        val fallbackCoordinates = listOf(Pair(waypoint.lat, waypoint.lon))
        return RacingGeoJSONValidator.createValidatedGeoJSON(
            type = "fai_quadrant_fallback",
            coordinates = fallbackCoordinates,
            properties = mapOf(
                "waypoint_index" to context.waypointIndex.toString(),
                "type" to "racing_fai_quadrant",
                "fallback" to "true"
            ),
            geometryType = "Point"
        )
    }
    
    override fun getDisplayRadius(waypoint: RacingWaypoint): Double {
        // Return sector radius for proper map bounds calculation
        // Note: waypoint marker circle size is controlled separately via MapLibre styling
        return waypoint.faiQuadrantOuterRadius * 1000.0 // Use configurable sector radius for map display bounds
    }
    
    override fun getObservationZoneType(): String {
        return "racing_fai_quadrant"
    }
    
    /**
     * CRITICAL FIX: Use the SAME official FAI algorithm as FAIQuadrantCalculator.kt
     * This ensures visual orientation matches mathematical calculations EXACTLY
     */
    private fun calculateCorrectedSectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint?
    ): Double {
        if (nextWaypoint == null) return 0.0
        
        return calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
    }

    /**
     * Calculate FAI sector bisector per official FAI Sporting Code Section 3 Annex A
     * FAI Rule: Bisector perpendicular to track bisector, oriented OUTWARD from course
     * IDENTICAL to FAIQuadrantCalculator.kt to ensure visual matches calculations
     */
    private fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint
    ): Double {
        if (previousWaypoint == null) {
            // If no previous waypoint, point sector opposite to next bearing
            val nextBearing = calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
            return (nextBearing + 180.0) % 360.0
        }

        // Calculate track bisector (bisector of the angle between incoming and outgoing legs)
        val inboundBearing = calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
        val outboundBearing = calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        
        // Track bisector is the angle bisector between the two legs
        val trackBisector = calculateAngleBisector(inboundBearing, outboundBearing)
        
        // FAI sector bisector is perpendicular to track bisector, oriented OUTWARD
        // This means it points away from the inside of the turn
        val turnDirection = calculateTurnDirection(inboundBearing, outboundBearing)
        val sectorBisector = if (turnDirection > 0) {
            // Right turn: sector points to the left of track bisector
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            // Left turn: sector points to the right of track bisector  
            (trackBisector + 90.0) % 360.0
        }
        
        return sectorBisector
    }
    
    /**
     * CRITICAL FIX: Calculate turn direction for proper sector orientation
     * Returns positive for right turn, negative for left turn
     */
    private fun calculateTurnDirection(incomingBearing: Double, outgoingBearing: Double): Double {
        val angleDifference = (outgoingBearing - incomingBearing + 360.0) % 360.0
        return if (angleDifference <= 180.0) angleDifference else angleDifference - 360.0
    }
    
    /**
     * Calculate the angle bisector between two bearings (FAI compliant)
     * This finds the centerline of the angle between inbound and outbound legs
     */
    private fun calculateAngleBisector(bearing1: Double, bearing2: Double): Double {
        // Normalize bearings to 0-360 range
        val b1 = (bearing1 + 360.0) % 360.0
        val b2 = (bearing2 + 360.0) % 360.0
        
        // Calculate the difference between bearings
        val diff = (b2 - b1 + 360.0) % 360.0
        
        // The bisector is halfway between the two bearings
        // Handle the case where we need to go the "short way" around the circle
        val bisector = if (diff <= 180.0) {
            // Short way: add half the difference
            (b1 + diff / 2.0) % 360.0
        } else {
            // Long way: go the other direction
            (b1 - (360.0 - diff) / 2.0 + 360.0) % 360.0
        }
        
        return bisector
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        
        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        
        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360.0) % 360.0
    }
    
    /**
     * Generate sector coordinates for GeoJSON polygon
     * CRASH FIX: Added comprehensive validation and error handling
     */
    private fun generateSectorCoordinates(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean = true
    ): String {
        try {
            // CRASH FIX: Validate all input parameters
            if (!centerLat.isFinite() || !centerLon.isFinite() ||
                !radiusMeters.isFinite() || !startAngle.isFinite() || !endAngle.isFinite()) {
                println("❌ SECTOR COORDINATES: Invalid input parameters - lat:$centerLat, lon:$centerLon, radius:$radiusMeters, start:$startAngle, end:$endAngle")
                return "[]" // Return empty coordinate array
            }

            // CRASH FIX: Validate coordinate bounds
            if (centerLat < -90.0 || centerLat > 90.0 || centerLon < -180.0 || centerLon > 180.0) {
                println("❌ SECTOR COORDINATES: Center coordinates out of bounds - lat:$centerLat, lon:$centerLon")
                return "[]"
            }

            // CRASH FIX: Validate radius
            if (radiusMeters <= 0 || radiusMeters > 100000000) { // Max 100,000km radius
                println("❌ SECTOR COORDINATES: Invalid radius: $radiusMeters meters")
                return "[]"
            }

            val points = 32
            val coordinates = mutableListOf<List<Double>>()

            if (includeCenter) {
                coordinates.add(listOf(centerLon, centerLat))
            }

            // CRASH FIX: Handle angle sweep with additional validation
            val normalizedStart = (startAngle + 360.0) % 360.0
            val normalizedEnd = (endAngle + 360.0) % 360.0

            for (i in 0..points) {
                try {
                    val t = i.toDouble() / points

                    // Handle angle interpolation correctly for sectors crossing 0°
                    val currentAngle = if (normalizedEnd > normalizedStart) {
                        // Normal case: no crossing of 0°
                        normalizedStart + (normalizedEnd - normalizedStart) * t
                    } else {
                        // Crossing 0°: go the "short way" around the circle
                        val angle = normalizedStart + ((normalizedEnd + 360.0) - normalizedStart) * t
                        angle % 360.0
                    }

                    // CRASH FIX: Validate current angle before trigonometric calculations
                    if (!currentAngle.isFinite()) {
                        println("❌ SECTOR COORDINATES: Invalid angle at point $i: $currentAngle")
                        continue // Skip this point
                    }

                    // CRASH FIX: Validate bearing before great circle calculation
                    if (!currentAngle.isFinite()) {
                        println("❌ SECTOR COORDINATES: Invalid bearing angle at point $i: $currentAngle")
                        continue // Skip this point
                    }

                    // Use shared RacingGeometryUtils - single algorithm for display and calculations
                    val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                        centerLat, centerLon,
                        currentAngle, // Already in degrees
                        radiusMeters // RacingGeometryUtils expects meters
                    )

                    // CRASH FIX: Validate calculated coordinates before adding
                    if (lat.isFinite() && lon.isFinite() &&
                        lat >= -90.0 && lat <= 90.0 &&
                        lon >= -180.0 && lon <= 180.0) {
                        coordinates.add(listOf(lon, lat))
                    } else {
                        println("❌ SECTOR COORDINATES: Invalid calculated point at $i - lat:$lat, lon:$lon")
                    }

                } catch (e: Exception) {
                    println("❌ SECTOR COORDINATES: Exception calculating point $i: ${e.message}")
                    continue // Skip this point and continue with next
                }
            }

            // CRASH FIX: Ensure we have minimum required coordinates for a polygon
            if (coordinates.size < 3) {
                println("❌ SECTOR COORDINATES: Insufficient valid coordinates: ${coordinates.size}")
                return "[]"
            }

            if (includeCenter) {
                coordinates.add(listOf(centerLon, centerLat))
            }

            val result = coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
            println("✅ SECTOR COORDINATES: Generated ${coordinates.size} valid coordinates")
            return result

        } catch (e: Exception) {
            println("❌ SECTOR COORDINATES: Fatal exception in generateSectorCoordinates: ${e.message}")
            return "[]" // Return empty coordinates to prevent crash
        }
    }

}