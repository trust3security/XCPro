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
        val sectorBisector = if (turnDirection > 0) {
            (trackBisector - 90.0 + 360.0) % 360.0
        } else {
            (trackBisector + 90.0) % 360.0
        }

        println("? CONFIGURABLE KEYHOLE SECTOR:")
        println("   Waypoint: ${waypoint.title}")
        println("   Inner Cylinder: ${(waypoint.keyholeInnerRadius * 1000.0).toInt()}m (user configured)")
        println("   Outer Sector: ${(waypoint.gateWidth * 1000.0).toInt()}m (user configured)")
        println("   Angle: ${waypoint.keyholeAngle}? (user configured)")
        println("   Inbound bearing: ${inboundBearing.toInt()}? (prev?wp)")
        println("   Outbound bearing: ${outboundBearing.toInt()}? (wp?next)")
        println("   Track bisector: ${trackBisector.toInt()}?")
        println("   Turn direction: ${if (turnDirection > 0) "RIGHT" else "LEFT"} (${turnDirection.toInt()}?)")
        println("   Sector bisector: ${sectorBisector.toInt()}? [PERPENDICULAR TO TRACK, OUTWARD]")
        println("   ? Configurable keyhole: ${(waypoint.keyholeInnerRadius * 1000.0).toInt()}m cylinder + ${waypoint.keyholeAngle}? sector (${(waypoint.gateWidth * 1000.0).toInt()}m radius)")
        println("   ? Enhanced flexibility beyond FAI implementation")

        return sectorBisector
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
        val coordinates = mutableListOf<List<Double>>()
        val sectorPoints = 90 // smoother edge

        // Start at center
        coordinates.add(listOf(centerLon, centerLat))

        // Outer arc from start -> end
        for (i in 0..sectorPoints) {
            val t = i.toDouble() / sectorPoints
            val angle = if (sectorEndAngle >= sectorStartAngle) {
                sectorStartAngle + (sectorEndAngle - sectorStartAngle) * t
            } else {
                // wrap-around case
                val span = 360.0 - sectorStartAngle + sectorEndAngle
                (sectorStartAngle + span * t) % 360.0
            }
            val p = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
                angle,
                sectorRadiusMeters
            )
            coordinates.add(listOf(p.second, p.first))
        }

        // Close back to center
        coordinates.add(listOf(centerLon, centerLat))

        return coordinates
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
        val coordinates = mutableListOf<List<Double>>()
        val cylinderPoints = 64

        for (i in 0..cylinderPoints) {
            val aviationBearing = 360.0 * i / cylinderPoints
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
                aviationBearing,
                cylinderRadiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }

        val (startEdgeLat, startEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat,
            centerLon,
            startAngle,
            cylinderRadiusMeters
        )
        val (endEdgeLat, endEdgeLon) = RacingGeometryUtils.calculateDestinationPoint(
            centerLat,
            centerLon,
            endAngle,
            cylinderRadiusMeters
        )

        val sectorPoints = 32
        for (i in 0..sectorPoints) {
            val t = i.toDouble() / sectorPoints
            val currentAngle = startAngle + (endAngle - startAngle) * t
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
                currentAngle,
                sectorRadiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }

        coordinates.add(listOf(endEdgeLon, endEdgeLat))

        if (coordinates.isNotEmpty()) {
            coordinates.add(coordinates.first())
        }

        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    fun generateUnifiedKeyholeShape(
        centerLat: Double,
        centerLon: Double,
        cylinderRadiusMeters: Double,
        sectorRadiusMeters: Double,
        startAngle: Double,
        endAngle: Double
    ): String {
        val cylinderCoords = RacingGeometryUtils.generateCircleCoordinatesArray(
            centerLat,
            centerLon,
            cylinderRadiusMeters,
            numPoints = 64
        )

        val sectorCoords = generateSectorCoordinatesArray(
            centerLat,
            centerLon,
            sectorRadiusMeters,
            startAngle,
            endAngle,
            includeCenter = false
        )

        val combined = (cylinderCoords + sectorCoords).toMutableList()
        if (combined.isNotEmpty()) {
            combined.add(combined.first())
        }

        return combined.joinToString(",") { "[${it[0]},${it[1]}]" }
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
        val outerRingCoords = generateSectorCoordinatesArray(
            centerLat,
            centerLon,
            radiusMeters,
            startAngle,
            endAngle,
            includeCenter = false
        )

        val firstPoint = outerRingCoords.firstOrNull()
            ?: listOf(centerLon, centerLat)
        val previousPoint = outerRingCoords.getOrNull(outerRingCoords.size - 2) ?: firstPoint
        val bearingFromCenter = RacingGeometryUtils.calculateBearing(
            centerLat,
            centerLon,
            firstPoint[1],
            firstPoint[0]
        )
        val holeBearing = (bearingFromCenter + 180.0) % 360.0
        val holeStart = RacingGeometryUtils.calculateDestinationPoint(
            centerLat,
            centerLon,
            startAngle,
            holeRadiusMeters
        )
        val holeEnd = RacingGeometryUtils.calculateDestinationPoint(
            centerLat,
            centerLon,
            endAngle,
            holeRadiusMeters
        )

        val holeDirection = RacingGeometryUtils.calculateBearing(
            holeStart.first,
            holeStart.second,
            holeEnd.first,
            holeEnd.second
        )
        val orientation = if (abs(holeDirection - holeBearing) < 1e-3) 1 else -1

        val innerRingCoords = RacingGeometryUtils.generateCircleCoordinatesArray(
            centerLat,
            centerLon,
            holeRadiusMeters,
            numPoints = 64,
            reverse = orientation < 0
        )

        val outerRing = outerRingCoords.joinToString(",") { "[${it[0]},${it[1]}]" }
        val innerRing = innerRingCoords.joinToString(",") { "[${it[0]},${it[1]}]" }

        return "$outerRing],[$innerRing"
    }

    fun generateSectorCoordinates(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean = true
    ): String {
        val coordinates = generateSectorCoordinatesArray(
            centerLat,
            centerLon,
            radiusMeters,
            startAngle,
            endAngle,
            includeCenter
        )
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    fun generateSectorCoordinatesArray(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean = true
    ): MutableList<List<Double>> {
        val points = 32
        val coordinates = mutableListOf<List<Double>>()

        if (includeCenter) {
            coordinates.add(listOf(centerLon, centerLat))
        }

        val normalizedStart = (startAngle + 360.0) % 360.0
        val normalizedEnd = (endAngle + 360.0) % 360.0

        for (i in 0..points) {
            val t = i.toDouble() / points
            val currentAngle = if (normalizedEnd > normalizedStart) {
                normalizedStart + (normalizedEnd - normalizedStart) * t
            } else {
                val angle = normalizedStart + ((normalizedEnd + 360.0) - normalizedStart) * t
                angle % 360.0
            }

            val aviationBearingRad = Math.toRadians(currentAngle)
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
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

    fun generateSimpleCircle(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double
    ): String {
        val coordinates = mutableListOf<List<Double>>()
        val points = 32

        for (i in 0..points) {
            val bearing = 360.0 * i / points
            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
                bearing,
                radiusMeters
            )
            coordinates.add(listOf(lon, lat))
        }

        val gson = com.google.gson.Gson()
        return gson.toJson(coordinates)
    }
}
