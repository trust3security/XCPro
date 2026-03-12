package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.models.RacingWaypoint
import kotlin.math.abs

internal object KeyholeGeometry {

    fun calculateSectorBisector(
        waypoint: RacingWaypoint,
        context: TaskContext
    ): Double {
        val previousWaypoint = context.previousWaypoint
        val nextWaypoint = context.nextWaypoint ?: return 0.0
        return calculateFAISectorBisector(waypoint, previousWaypoint, nextWaypoint)
    }

    fun calculateFAISectorBisector(
        waypoint: RacingWaypoint,
        previousWaypoint: RacingWaypoint?,
        nextWaypoint: RacingWaypoint
    ): Double {
        if (previousWaypoint == null) {
            val nextBearing = RacingGeometryUtils.calculateBearing(
                waypoint.lat,
                waypoint.lon,
                nextWaypoint.lat,
                nextWaypoint.lon
            )
            return (nextBearing + 180.0) % 360.0
        }

        val inboundBearing = RacingGeometryUtils.calculateBearing(
            previousWaypoint.lat,
            previousWaypoint.lon,
            waypoint.lat,
            waypoint.lon
        )
        val outboundBearing = RacingGeometryUtils.calculateBearing(
            waypoint.lat,
            waypoint.lon,
            nextWaypoint.lat,
            nextWaypoint.lon
        )

        val trackBisector = calculateAngleBisector(inboundBearing, outboundBearing)
        val turnDirection = calculateTurnDirection(inboundBearing, outboundBearing)
        return if (turnDirection > 0) {
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            (trackBisector + 90.0) % 360.0
        }
    }

    fun calculateAngleBisector(bearing1: Double, bearing2: Double): Double {
        val b1 = (bearing1 + 360.0) % 360.0
        val b2 = (bearing2 + 360.0) % 360.0
        val diff = (b2 - b1 + 360.0) % 360.0
        return if (diff <= 180.0) {
            (b1 + diff / 2.0) % 360.0
        } else {
            (b1 - (360.0 - diff) / 2.0 + 360.0) % 360.0
        }
    }

    fun calculateTurnDirection(incomingBearing: Double, outgoingBearing: Double): Double {
        val angleDifference = (outgoingBearing - incomingBearing + 360.0) % 360.0
        return if (angleDifference <= 180.0) angleDifference else angleDifference - 360.0
    }

    fun generateFAIKeyholeShapeArray(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        sectorStartAngle: Double,
        sectorEndAngle: Double
    ): List<List<Double>> {
        return KeyholeShapeSupport.generateFAIKeyholeShapeArray(
            centerLat = centerLat,
            centerLon = centerLon,
            sectorRadiusMeters = sectorRadiusMeters,
            sectorStartAngle = sectorStartAngle,
            sectorEndAngle = sectorEndAngle
        )
    }

    fun generateFAIKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        sectorStartAngle: Double,
        sectorEndAngle: Double
    ): String {
        val coordinates = generateFAIKeyholeShapeArray(
            centerLat,
            centerLon,
            cylinderRadiusMeters,
            sectorRadiusMeters,
            sectorStartAngle,
            sectorEndAngle
        )
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    fun generateKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        return KeyholeShapeSupport.generateKeyholeShape(
            centerLat = centerLat,
            centerLon = centerLon,
            cylinderRadiusMeters = cylinderRadiusMeters,
            sectorRadiusMeters = sectorRadiusMeters,
            startAngle = startAngle,
            endAngle = endAngle
        )
    }

    fun generateUnifiedKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        return KeyholeShapeSupport.generateUnifiedKeyholeShape(
            centerLat = centerLat,
            centerLon = centerLon,
            cylinderRadiusMeters = cylinderRadiusMeters,
            sectorRadiusMeters = sectorRadiusMeters,
            startAngle = startAngle,
            endAngle = endAngle
        )
    }

    fun wasWithinSector(bearing: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedCurrent = (bearing + 360.0) % 360.0
        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        return if (normalizedEnd > normalizedStart) {
            normalizedCurrent >= normalizedStart && normalizedCurrent <= normalizedEnd
        } else {
            normalizedCurrent >= normalizedStart || normalizedCurrent <= normalizedEnd
        }
    }

    fun generateSectorWithHole(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        holeRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        return KeyholeShapeSupport.generateSectorWithHole(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            holeRadiusMeters = holeRadiusMeters,
            startAngle = startAngle,
            endAngle = endAngle
        )
    }

    fun generateSectorCoordinates(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean = true
    ): String {
        return KeyholeShapeSupport.generateSectorCoordinates(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            startAngle = startAngle,
            endAngle = endAngle,
            includeCenter = includeCenter
        )
    }

    fun generateSectorCoordinatesArray(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean = true
    ): MutableList<List<Double>> {
        return KeyholeShapeSupport.generateSectorCoordinatesArray(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            startAngle = startAngle,
            endAngle = endAngle,
            includeCenter = includeCenter
        )
    }

    fun generateSimpleCircle(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double
    ): String {
        return KeyholeShapeSupport.generateSimpleCircle(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters
        )
    }
}
