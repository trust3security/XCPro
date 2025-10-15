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
 * - Angle: user sets via keyholeAngle (sector angle, default 90°)
 * - Sector oriented perpendicular to track bisector, pointing outward
 * - Much more flexible than fixed FAI implementation
 */
class KeyholeDisplay : TurnPointDisplay {
    
    
    override fun generateVisualGeometry(waypoint: RacingWaypoint, context: TaskContext): String {
        return try {
            println("🔑 KEYHOLE DEBUG: Starting keyhole generation for waypoint ${waypoint.title}")

            // Configurable Keyhole: Use flexible parameters
            val cylinderRadiusMeters = waypoint.keyholeInnerRadius * 1000.0 // Inner cylinder radius in meters
            val sectorRadiusMeters = waypoint.gateWidth * 1000.0 // Outer sector radius in meters
            val sectorAngleDegrees = waypoint.keyholeAngle // Configurable angle

            println("🔑 KEYHOLE DEBUG: Params - Inner:${cylinderRadiusMeters}m, Outer:${sectorRadiusMeters}m, Angle:${sectorAngleDegrees}°")

            // Use sector calculation with configurable angle
            val bisectorBearing = calculateSectorBisector(waypoint, context)
            val halfAngle = sectorAngleDegrees / 2.0
            val startAngle = (bisectorBearing - halfAngle) % 360.0
            val endAngle = (bisectorBearing + halfAngle) % 360.0

            println("🔑 KEYHOLE DEBUG: Angles - Bisector:${bisectorBearing}°, Start:${startAngle}°, End:${endAngle}°")

            // FIXED: Generate proper keyhole geometry with GSON coordinate arrays
            val keyholeCoords = generateFAIKeyholeShapeArray(
                waypoint.lat, waypoint.lon,
                cylinderRadiusMeters, // User's inner radius
                sectorRadiusMeters,   // User's outer radius
                startAngle, endAngle
            )

            // Use GSON to properly serialize coordinate arrays
            val gson = com.google.gson.Gson()
            val coordinatesJson = gson.toJson(listOf(keyholeCoords))

            println("🔑 KEYHOLE DEBUG: Generated ${keyholeCoords.size} keyhole coordinate points using GSON")

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
                    "coordinates": $coordinatesJson
                }
            }
            """.trimIndent()

            println("🔑 KEYHOLE DEBUG: Generated GeoJSON successfully")
            geoJson
        } catch (e: Exception) {
            println("🔑 KEYHOLE ERROR: ${e.message}")
            println("🔑 KEYHOLE ERROR: Stack trace: ${e.stackTrace.contentToString()}")
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
    
    /**
     * Calculate sector bisector bearing for keyhole quadrant
     * FIXED: Use the SAME official FAI algorithm as FAIQuadrantCalculator
     */
    private fun calculateSectorBisector(waypoint: RacingWaypoint, context: TaskContext): Double {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return 0.0
        
        return calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
    }

    /**
     * Calculate FAI sector bisector per official FAI Sporting Code Section 3 Annex A
     * IDENTICAL to FAIQuadrantCalculator.kt and KeyholeCalculator.kt
     */
    private fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint
    ): Double {
        if (previousWaypoint == null) {
            // If no previous waypoint, point sector opposite to next bearing
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val nextBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
            return (nextBearing + 180.0) % 360.0
        }

        // Calculate track bisector (bisector of the angle between incoming and outgoing legs)
        // Use shared RacingGeometryUtils - single algorithm for display and calculations
        val inboundBearing = RacingGeometryUtils.calculateBearing(previousWaypoint.lat, previousWaypoint.lon, waypoint.lat, waypoint.lon)
        val outboundBearing = RacingGeometryUtils.calculateBearing(waypoint.lat, waypoint.lon, nextWaypoint.lat, nextWaypoint.lon)
        
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
        
        // Debug logging for configurable keyhole sector orientation
        println("🔑 CONFIGURABLE KEYHOLE SECTOR:")
        println("   Waypoint: ${waypoint.title}")
        println("   Inner Cylinder: ${(waypoint.keyholeInnerRadius * 1000.0).toInt()}m (user configured)")
        println("   Outer Sector: ${(waypoint.gateWidth * 1000.0).toInt()}m (user configured)")
        println("   Angle: ${waypoint.keyholeAngle}° (user configured)")
        println("   Inbound bearing: ${inboundBearing.toInt()}° (prev→wp)")
        println("   Outbound bearing: ${outboundBearing.toInt()}° (wp→next)")
        println("   Track bisector: ${trackBisector.toInt()}°")
        println("   Turn direction: ${if (turnDirection > 0) "RIGHT" else "LEFT"} (${turnDirection.toInt()}°)")
        println("   Sector bisector: ${sectorBisector.toInt()}° [PERPENDICULAR TO TRACK, OUTWARD]")
        println("   ✅ Configurable keyhole: ${(waypoint.keyholeInnerRadius * 1000.0).toInt()}m cylinder + ${waypoint.keyholeAngle}° sector (${(waypoint.gateWidth * 1000.0).toInt()}m radius)")
        println("   ✅ Enhanced flexibility beyond FAI implementation")
        
        return sectorBisector
    }
    
    /**
     * Calculate the angle bisector between two bearings (FAI compliant)
     * Copied from FAIQuadrantCalculator for consistency
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
    
    /**
     * CRITICAL FIX: Calculate turn direction for proper sector orientation
     * Returns positive for right turn, negative for left turn
     * COPIED FROM FAIQuadrantCalculator for consistency
     */
    private fun calculateTurnDirection(incomingBearing: Double, outgoingBearing: Double): Double {
        val angleDifference = (outgoingBearing - incomingBearing + 360.0) % 360.0
        return if (angleDifference <= 180.0) angleDifference else angleDifference - 360.0
    }

    /**
     * Generate proper FAI keyhole shape geometry as coordinate array
     * A keyhole consists of:
     * 1. A user-configurable cylinder at the turnpoint center
     * 2. A configurable sector extending from the cylinder
     *
     * The shape is the UNION of both parts, creating a true keyhole 🔑 shape.
     */
    private fun generateFAIKeyholeShapeArray(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        sectorStartAngle: Double,
        sectorEndAngle: Double
    ): List<List<Double>> {
        val coordinates = mutableListOf<List<Double>>()

        // Step 1: Start with the cylinder outline, but only the part NOT covered by the sector
        val cylinderPoints = 72 // More points for smoother curve

        // Start angle should be where the sector ends, go around to where sector starts
        val startDrawAngle = sectorEndAngle
        val endDrawAngle = sectorStartAngle + 360.0 // Go all the way around

        // Draw cylinder outline from sector end to sector start (the visible cylinder part)
        for (i in 0..cylinderPoints) {
            val angleProgress = i.toDouble() / cylinderPoints
            val currentAngle = startDrawAngle + angleProgress * (endDrawAngle - startDrawAngle)
            val normalizedAngle = currentAngle % 360.0

            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val point = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, normalizedAngle, cylinderRadiusMeters)
            coordinates.add(listOf(point.second, point.first))
        }

        // Step 2: Connect to sector outer boundary at start angle
        val sectorOuterStart = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorStartAngle, sectorRadiusMeters)
        coordinates.add(listOf(sectorOuterStart.second, sectorOuterStart.first))

        // Step 3: Draw the sector outer arc
        val sectorPoints = 45 // Points along the sector arc
        for (i in 1 until sectorPoints) {
            val angleProgress = i.toDouble() / sectorPoints
            val angle = sectorStartAngle + angleProgress * (sectorEndAngle - sectorStartAngle)
            val point = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, angle, sectorRadiusMeters)
            coordinates.add(listOf(point.second, point.first))
        }

        // Step 4: Connect to sector outer boundary at end angle
        val sectorOuterEnd = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorEndAngle, sectorRadiusMeters)
        coordinates.add(listOf(sectorOuterEnd.second, sectorOuterEnd.first))

        // Step 5: Connect back to cylinder edge at sector end angle (this closes the keyhole)
        val cylinderSectorEnd = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorEndAngle, cylinderRadiusMeters)
        coordinates.add(listOf(cylinderSectorEnd.second, cylinderSectorEnd.first))

        // Close the polygon
        if (coordinates.isNotEmpty()) {
            coordinates.add(coordinates.first())
        }

        return coordinates
    }

    /**
     * Generate proper FAI keyhole shape geometry
     * A keyhole consists of:
     * 1. A user-configurable cylinder at the turnpoint center
     * 2. A 90° FAI quadrant extending from the cylinder
     *
     * The shape is the UNION of both parts, creating a true keyhole 🔑 shape.
     */
    private fun generateFAIKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        sectorStartAngle: Double,
        sectorEndAngle: Double
    ): String {
        val coordinates = mutableListOf<List<Double>>()

        // Step 1: Start with the cylinder outline, but only the part NOT covered by the sector
        val cylinderPoints = 72 // More points for smoother curve

        // Start angle should be where the sector ends, go around to where sector starts
        val startDrawAngle = sectorEndAngle
        val endDrawAngle = sectorStartAngle + 360.0 // Go all the way around

        // Draw cylinder outline from sector end to sector start (the visible cylinder part)
        for (i in 0..cylinderPoints) {
            val angleProgress = i.toDouble() / cylinderPoints
            val currentAngle = startDrawAngle + angleProgress * (endDrawAngle - startDrawAngle)
            val normalizedAngle = currentAngle % 360.0

            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val point = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, normalizedAngle, cylinderRadiusMeters)
            coordinates.add(listOf(point.second, point.first))
        }

        // Step 2: Connect to sector outer boundary at start angle
        val sectorOuterStart = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorStartAngle, sectorRadiusMeters)
        coordinates.add(listOf(sectorOuterStart.second, sectorOuterStart.first))

        // Step 3: Draw the sector outer arc
        val sectorPoints = 45 // Points along the 90° sector arc
        for (i in 1 until sectorPoints) {
            val angleProgress = i.toDouble() / sectorPoints
            val angle = sectorStartAngle + angleProgress * (sectorEndAngle - sectorStartAngle)
            val point = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, angle, sectorRadiusMeters)
            coordinates.add(listOf(point.second, point.first))
        }

        // Step 4: Connect to sector outer boundary at end angle
        val sectorOuterEnd = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorEndAngle, sectorRadiusMeters)
        coordinates.add(listOf(sectorOuterEnd.second, sectorOuterEnd.first))

        // Step 5: Connect back to cylinder edge at sector end angle (this closes the keyhole)
        val cylinderSectorEnd = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, sectorEndAngle, cylinderRadiusMeters)
        coordinates.add(listOf(cylinderSectorEnd.second, cylinderSectorEnd.first))

        // Close the polygon
        if (coordinates.isNotEmpty()) {
            coordinates.add(coordinates.first())
        }

        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    /**
     * Generate proper keyhole shape: user configurable cylinder + configurable sector extending from edge
     * Creates a single polygon that forms the complete keyhole appearance like 🔑
     */
    private fun generateKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        val coordinates = mutableListOf<List<Double>>()
        
        // KEYHOLE APPROACH: Start with full user-configured circle, then add sector extension
        
        // Step 1: Create the main user-configured cylinder (complete circle)
        val cylinderPoints = 64
        for (i in 0..cylinderPoints) {
            val aviationBearing = 360.0 * i / cylinderPoints
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat, centerLon,
                aviationBearing,
                cylinderRadiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }
        
        // Step 2: Find the connection points on the cylinder edge where sector begins
        // Use shared RacingGeometryUtils - single algorithm for display and calculations
        val (startEdgeLat, startEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat, centerLon, startAngle, cylinderRadiusMeters
        )
        val (endEdgeLat, endEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat, centerLon, endAngle, cylinderRadiusMeters
        )
        
        // Step 3: Add sector extension - arc at 10km radius
        val sectorPoints = 32
        for (i in 0..sectorPoints) {
            val t = i.toDouble() / sectorPoints
            val currentAngle = startAngle + (endAngle - startAngle) * t
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat, centerLon,
                currentAngle,
                sectorRadiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }
        
        // Step 4: Connect back to the cylinder edge to close the keyhole shape
        coordinates.add(listOf(endEdgeLon, endEdgeLat))
        
        // Close the polygon
        if (coordinates.isNotEmpty()) {
            coordinates.add(coordinates.first())
        }
        
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    /**
     * Generate unified keyhole shape - single polygon combining cylinder + sector
     * Creates a true keyhole shape: full circle with sector extension
     */
    private fun generateUnifiedKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        val coordinates = mutableListOf<List<Double>>()
        
        // Step 1: Start with complete cylinder circle
        val cylinderPoints = 64
        // Find where sector connects to cylinder
        // Use shared RacingGeometryUtils - single algorithm for display and calculations
        val (startEdgeLat, startEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat, centerLon, startAngle, cylinderRadiusMeters
        )
        val (endEdgeLat, endEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat, centerLon, endAngle, cylinderRadiusMeters
        )
        
        // Step 2: Trace cylinder outline, but skip the part where sector extends
        for (i in 0..cylinderPoints) {
            val currentBearing = 360.0 * i / cylinderPoints
            val currentBearingRad = Math.toRadians(currentBearing)
            
            // Check if this point is within the sector extension angle
            val normalizedCurrent = (currentBearing + 360.0) % 360.0
            val normalizedStart = (startAngle + 360.0) % 360.0
            val normalizedEnd = (endAngle + 360.0) % 360.0
            
            val withinSector = if (normalizedEnd > normalizedStart) {
                normalizedCurrent >= normalizedStart && normalizedCurrent <= normalizedEnd
            } else {
                normalizedCurrent >= normalizedStart || normalizedCurrent <= normalizedEnd
            }
            
            if (!withinSector) {
                // Add cylinder point only if it's NOT in the sector extension area
                val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                    centerLat, centerLon, Math.toDegrees(currentBearingRad), cylinderRadiusMeters
                )
                coordinates.add(listOf(lon, lat))
            } else if (i == 0 || (i > 0 && !wasWithinSector(360.0 * (i-1) / cylinderPoints, startAngle, endAngle))) {
                // Add the connection point where cylinder meets sector
                coordinates.add(listOf(startEdgeLon, startEdgeLat))
                break
            }
        }
        
        // Step 3: Add sector arc at outer radius
        val sectorPoints = 32
        for (i in 0..sectorPoints) {
            val t = i.toDouble() / sectorPoints
            val currentAngle = startAngle + (endAngle - startAngle) * t
            val currentAngleRad = Math.toRadians(currentAngle)
            
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat, centerLon, Math.toDegrees(currentAngleRad), sectorRadiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }
        
        // Step 4: Connect back to cylinder at end angle
        coordinates.add(listOf(endEdgeLon, endEdgeLat))
        
        // Step 5: Continue cylinder outline from end angle back to start
        for (i in 0..cylinderPoints) {
            val currentBearing = 360.0 * i / cylinderPoints
            val currentBearingRad = Math.toRadians(currentBearing)
            
            val normalizedCurrent = (currentBearing + 360.0) % 360.0
            val normalizedStart = (startAngle + 360.0) % 360.0
            val normalizedEnd = (endAngle + 360.0) % 360.0
            
            val withinSector = if (normalizedEnd > normalizedStart) {
                normalizedCurrent >= normalizedStart && normalizedCurrent <= normalizedEnd
            } else {
                normalizedCurrent >= normalizedStart || normalizedCurrent <= normalizedEnd
            }
            
            if (!withinSector && normalizedCurrent > normalizedEnd) {
                val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                    centerLat, centerLon, Math.toDegrees(currentBearingRad), cylinderRadiusMeters
                )
                coordinates.add(listOf(lon, lat))
            }
        }
        
        // Close the polygon
        if (coordinates.isNotEmpty()) {
            coordinates.add(coordinates.first())
        }
        
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }
    
    /**
     * Helper function to check if an angle is within sector
     */
    private fun wasWithinSector(bearing: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedCurrent = (bearing + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0
        
        return if (normalizedEnd > normalizedStart) {
            normalizedCurrent >= normalizedStart && normalizedCurrent <= normalizedEnd
        } else {
            normalizedCurrent >= normalizedStart || normalizedCurrent <= normalizedEnd
        }
    }

    /**
     * Generate sector with circular hole to prevent overlap with cylinder
     * Creates a GeoJSON polygon with exterior ring (sector) and interior ring (cylinder hole)
     */
    private fun generateSectorWithHole(
        centerLat: Double, 
        centerLon: Double, 
        holeRadiusMeters: Double,
        sectorRadiusMeters: Double, 
        startAngle: Double, 
        endAngle: Double
    ): String {
        // Generate outer sector ring (same as normal sector)
        val outerRingCoords = generateSectorCoordinatesArray(centerLat, centerLon, sectorRadiusMeters, startAngle, endAngle, true)

        // SSOT: Use RacingGeometryUtils for circle generation
        val innerRingCoords = RacingGeometryUtils.generateCircleCoordinatesArray(
            centerLat, centerLon, holeRadiusMeters, numPoints = 64, reverse = true
        )
        
        // Format as GeoJSON polygon with hole: [exterior_ring, interior_ring]
        val outerRing = outerRingCoords.joinToString(",") { "[${it[0]},${it[1]}]" }
        val innerRing = innerRingCoords.joinToString(",") { "[${it[0]},${it[1]}]" }
        
        return "$outerRing],[$innerRing"
    }
    
    /**
     * Generate sector coordinates as array for GeoJSON polygon with hole support
     * FIXED: Proper angle handling and bearing conversion for keyhole sectors
     */
    private fun generateSectorCoordinates(
        centerLat: Double, 
        centerLon: Double, 
        radiusMeters: Double, 
        startAngle: Double, 
        endAngle: Double, 
        includeCenter: Boolean = true
    ): String {
        val points = 32
        val coordinates = mutableListOf<List<Double>>()
        
        if (includeCenter) {
            coordinates.add(listOf(centerLon, centerLat))
        }
        
        // CRITICAL FIX: Handle angle sweep correctly, especially across 0°/360° boundary
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0
        
        for (i in 0..points) {
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
            
            // CRITICAL FIX: startAngle and endAngle are ALREADY aviation bearings from FAI calculation
            // No double conversion needed - use currentAngle directly as aviation bearing
            val aviationBearingRad = Math.toRadians(currentAngle)
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat, centerLon,
                Math.toDegrees(aviationBearingRad), // Use aviation bearing directly
                radiusMeters // Already in meters
            )
            coordinates.add(listOf(lon, lat))
        }
        
        if (includeCenter) {
            coordinates.add(listOf(centerLon, centerLat))
        }
        
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }
    
    
    /**
     * Generate sector coordinates as array (for polygon with holes)
     */
    private fun generateSectorCoordinatesArray(
        centerLat: Double, 
        centerLon: Double, 
        radiusMeters: Double, 
        startAngle: Double, 
        endAngle: Double, 
        includeCenter: Boolean = true
    ): List<List<Double>> {
        val points = 32
        val coordinates = mutableListOf<List<Double>>()
        
        if (includeCenter) {
            coordinates.add(listOf(centerLon, centerLat))
        }
        
        // Handle angle sweep correctly, especially across 0°/360° boundary
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0
        
        for (i in 0..points) {
            val t = i.toDouble() / points
            
            // Handle angle interpolation correctly for sectors crossing 0°
            val currentAngle = if (normalizedEnd > normalizedStart) {
                normalizedStart + (normalizedEnd - normalizedStart) * t
            } else {
                val angle = normalizedStart + ((normalizedEnd + 360.0) - normalizedStart) * t
                angle % 360.0
            }
            
            val aviationBearingRad = Math.toRadians(currentAngle)
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat, centerLon,
                Math.toDegrees(aviationBearingRad),
                radiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }
        
        if (includeCenter) {
            coordinates.add(listOf(centerLon, centerLat))
        }
        
        return coordinates
    }
    
    
    /**
     * TEMP TEST: Generate simple circle coordinates to test basic GeoJSON functionality
     * FIXED: Use GSON to create proper JSON arrays instead of string concatenation
     */
    private fun generateSimpleCircle(centerLat: Double, centerLon: Double, radiusMeters: Double): String {
        val coordinates = mutableListOf<List<Double>>()
        val points = 32

        for (i in 0..points) {
            val bearing = 360.0 * i / points
            // Use shared RacingGeometryUtils - single algorithm for display and calculations
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, bearing, radiusMeters)
            coordinates.add(listOf(lon, lat))
        }

        // Use GSON to properly serialize the coordinate arrays
        val gson = com.google.gson.Gson()
        return gson.toJson(coordinates)
    }
}