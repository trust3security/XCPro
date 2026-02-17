package com.example.xcpro.tasks.racing.turnpoints

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.abs

internal object KeyholeShapeSupport {

    fun generateFAIKeyholeShapeArray(
        centerLat: Double,
        centerLon: Double,
        sectorRadiusMeters: Double,
        sectorStartAngle: Double,
        sectorEndAngle: Double
    ): List<List<Double>> {
        val coordinates = mutableListOf<List<Double>>()
        val sectorPoints = 90
        coordinates.add(listOf(centerLon, centerLat))

        for (i in 0..sectorPoints) {
            val t = i.toDouble() / sectorPoints
            val angle = if (sectorEndAngle >= sectorStartAngle) {
                sectorStartAngle + (sectorEndAngle - sectorStartAngle) * t
            } else {
                val span = 360.0 - sectorStartAngle + sectorEndAngle
                (sectorStartAngle + span * t) % 360.0
            }
            val p = RacingGeometryUtils.calculateDestinationPoint(centerLat, centerLon, angle, sectorRadiusMeters)
            coordinates.add(listOf(p.second, p.first))
        }

        coordinates.add(listOf(centerLon, centerLat))
        return coordinates
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
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = sectorRadiusMeters,
            startAngle = startAngle,
            endAngle = endAngle,
            includeCenter = false
        )

        val combined = (cylinderCoords + sectorCoords).toMutableList()
        if (combined.isNotEmpty()) {
            combined.add(combined.first())
        }
        return combined.joinToString(",") { "[${it[0]},${it[1]}]" }
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
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            startAngle = startAngle,
            endAngle = endAngle,
            includeCenter = false
        )

        val firstPoint = outerRingCoords.firstOrNull() ?: listOf(centerLon, centerLat)
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
        includeCenter: Boolean
    ): String {
        val coordinates = generateSectorCoordinatesArray(
            centerLat = centerLat,
            centerLon = centerLon,
            radiusMeters = radiusMeters,
            startAngle = startAngle,
            endAngle = endAngle,
            includeCenter = includeCenter
        )
        return coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
    }

    fun generateSectorCoordinatesArray(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        startAngle: Double,
        endAngle: Double,
        includeCenter: Boolean
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

            val (lat, lon) = RacingGeometryUtils.calculateDestinationPoint(
                centerLat,
                centerLon,
                Math.toDegrees(Math.toRadians(currentAngle)),
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
