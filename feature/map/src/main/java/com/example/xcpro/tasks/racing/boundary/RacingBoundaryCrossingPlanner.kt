package com.example.xcpro.tasks.racing.boundary

import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.tasks.racing.navigation.RacingNavigationFix
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class RacingBoundaryCrossingPlanner(
    private val epsilonPolicy: RacingBoundaryEpsilonPolicy = RacingBoundaryEpsilonPolicy()
) {

    fun detectCylinderCrossing(
        center: RacingBoundaryPoint,
        radiusMeters: Double,
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        transition: RacingBoundaryTransition
    ): RacingBoundaryCrossing? {
        if (radiusMeters <= 0.0) return null

        val epsilon = max(
            epsilonPolicy.epsilonMeters(previousFix),
            epsilonPolicy.epsilonMeters(currentFix)
        )

        val previousDistance = distanceMeters(center, previousFix)
        val currentDistance = distanceMeters(center, currentFix)

        val previousRelation = classify(previousDistance, radiusMeters, epsilon)
        val currentRelation = classify(currentDistance, radiusMeters, epsilon)

        if (!isTransition(previousRelation, currentRelation, transition)) {
            return null
        }

        val t = intersectionParameter(center, radiusMeters, previousFix, currentFix, transition) ?: return null

        val crossingPoint = boundaryPointFromParameter(center, radiusMeters, previousFix, currentFix, t)
        val crossingTime = interpolateTime(previousFix.timestampMillis, currentFix.timestampMillis, t)
        val (insideAnchor, outsideAnchor) = RacingBoundaryGeometry.anchorsForBoundaryPoint(
            center = center,
            boundaryPoint = crossingPoint,
            radiusMeters = radiusMeters,
            epsilonMeters = epsilon
        )

        return RacingBoundaryCrossing(
            transition = transition,
            crossingPoint = crossingPoint,
            crossingTimeMillis = crossingTime,
            insideAnchor = insideAnchor,
            outsideAnchor = outsideAnchor
        )
    }

    fun detectLineCrossing(
        center: RacingBoundaryPoint,
        lineLengthMeters: Double,
        lineBearingDegrees: Double,
        sectorBearingDegrees: Double,
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        transition: RacingBoundaryTransition
    ): RacingBoundaryCrossing? {
        if (lineLengthMeters <= 0.0) return null

        val radiusMeters = lineLengthMeters / 2.0
        val epsilon = max(
            epsilonPolicy.epsilonMeters(previousFix),
            epsilonPolicy.epsilonMeters(currentFix)
        )

        val previousDistance = distanceMeters(center, previousFix)
        val currentDistance = distanceMeters(center, currentFix)
        if (previousDistance > radiusMeters || currentDistance > radiusMeters) {
            return null
        }

        val previousInside = isInsideLineSector(center, sectorBearingDegrees, radiusMeters, epsilon, previousFix)
        val currentInside = isInsideLineSector(center, sectorBearingDegrees, radiusMeters, epsilon, currentFix)
        if (!isTransition(
                previousRelation = if (previousInside) ZoneRelation.INSIDE else ZoneRelation.OUTSIDE,
                currentRelation = if (currentInside) ZoneRelation.INSIDE else ZoneRelation.OUTSIDE,
                transition = transition
            )
        ) {
            return null
        }

        val halfLength = lineLengthMeters / 2.0
        val endA = RacingBoundaryGeometry.pointOnBearing(center, lineBearingDegrees, halfLength)
        val endB = RacingBoundaryGeometry.pointOnBearing(center, lineBearingDegrees + 180.0, halfLength)
        val p0 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(previousFix.lat, previousFix.lon))
        val p1 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(currentFix.lat, currentFix.lon))
        val q0 = RacingBoundaryGeometry.toLocalMeters(center, endA)
        val q1 = RacingBoundaryGeometry.toLocalMeters(center, endB)
        val intersection = RacingBoundaryGeometry.segmentIntersection(p0, p1, q0, q1) ?: return null

        val crossingPoint = RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
        val crossingTime = interpolateTime(previousFix.timestampMillis, currentFix.timestampMillis, intersection.t)
        val (insideAnchor, outsideAnchor) = lineAnchors(
            crossingPoint = crossingPoint,
            sectorBearingDegrees = sectorBearingDegrees,
            epsilonMeters = epsilon
        )

        return RacingBoundaryCrossing(
            transition = transition,
            crossingPoint = crossingPoint,
            crossingTimeMillis = crossingTime,
            insideAnchor = insideAnchor,
            outsideAnchor = outsideAnchor
        )
    }

    fun detectSectorCrossing(
        center: RacingBoundaryPoint,
        radiusMeters: Double,
        sectorBearingDegrees: Double,
        halfAngleDegrees: Double,
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        transition: RacingBoundaryTransition
    ): RacingBoundaryCrossing? {
        if (radiusMeters <= 0.0) return null

        val epsilon = max(
            epsilonPolicy.epsilonMeters(previousFix),
            epsilonPolicy.epsilonMeters(currentFix)
        )

        val previousInside = isInsideSector(center, sectorBearingDegrees, halfAngleDegrees, radiusMeters, epsilon, previousFix)
        val currentInside = isInsideSector(center, sectorBearingDegrees, halfAngleDegrees, radiusMeters, epsilon, currentFix)
        if (!isTransition(
                previousRelation = if (previousInside) ZoneRelation.INSIDE else ZoneRelation.OUTSIDE,
                currentRelation = if (currentInside) ZoneRelation.INSIDE else ZoneRelation.OUTSIDE,
                transition = transition
            )
        ) {
            return null
        }

        val p0 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(previousFix.lat, previousFix.lon))
        val p1 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(currentFix.lat, currentFix.lon))
        val intersection = sectorIntersectionParameter(
            center = center,
            radiusMeters = radiusMeters,
            sectorBearingDegrees = sectorBearingDegrees,
            halfAngleDegrees = halfAngleDegrees,
            p0 = p0,
            p1 = p1,
            transition = transition
        ) ?: return null

        val crossingPoint = RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
        val crossingTime = interpolateTime(previousFix.timestampMillis, currentFix.timestampMillis, intersection.t)
        val (insideAnchor, outsideAnchor) = anchorsAlongTrack(
            previousFix = previousFix,
            currentFix = currentFix,
            crossingT = intersection.t,
            epsilonMeters = epsilon,
            transition = transition
        )

        return RacingBoundaryCrossing(
            transition = transition,
            crossingPoint = crossingPoint,
            crossingTimeMillis = crossingTime,
            insideAnchor = insideAnchor,
            outsideAnchor = outsideAnchor
        )
    }

    private fun distanceMeters(center: RacingBoundaryPoint, fix: RacingNavigationFix): Double {
        val km = RacingGeometryUtils.haversineDistance(center.lat, center.lon, fix.lat, fix.lon)
        return abs(km * 1000.0)
    }

    private enum class ZoneRelation {
        INSIDE,
        OUTSIDE,
        BORDER
    }

    private fun classify(distanceMeters: Double, radiusMeters: Double, epsilonMeters: Double): ZoneRelation {
        return when {
            distanceMeters <= radiusMeters - epsilonMeters -> ZoneRelation.INSIDE
            distanceMeters >= radiusMeters + epsilonMeters -> ZoneRelation.OUTSIDE
            else -> ZoneRelation.BORDER
        }
    }

    private fun isTransition(
        previousRelation: ZoneRelation,
        currentRelation: ZoneRelation,
        transition: RacingBoundaryTransition
    ): Boolean {
        if (previousRelation == ZoneRelation.BORDER || currentRelation == ZoneRelation.BORDER) {
            return false
        }
        return when (transition) {
            RacingBoundaryTransition.ENTER ->
                previousRelation == ZoneRelation.OUTSIDE && currentRelation == ZoneRelation.INSIDE
            RacingBoundaryTransition.EXIT ->
                previousRelation == ZoneRelation.INSIDE && currentRelation == ZoneRelation.OUTSIDE
        }
    }

    private fun intersectionParameter(
        center: RacingBoundaryPoint,
        radiusMeters: Double,
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        transition: RacingBoundaryTransition
    ): Double? {
        val (x0, y0) = RacingBoundaryGeometry.toLocalMeters(
            center,
            RacingBoundaryPoint(previousFix.lat, previousFix.lon)
        )
        val (x1, y1) = RacingBoundaryGeometry.toLocalMeters(
            center,
            RacingBoundaryPoint(currentFix.lat, currentFix.lon)
        )

        val dx = x1 - x0
        val dy = y1 - y0
        val a = dx * dx + dy * dy
        if (a <= 0.0) return null

        val b = 2.0 * (x0 * dx + y0 * dy)
        val c = x0 * x0 + y0 * y0 - radiusMeters * radiusMeters
        val discriminant = b * b - 4.0 * a * c
        if (discriminant < 0.0) return null

        val sqrtDisc = sqrt(discriminant)
        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)
        val candidates = listOf(t1, t2).filter { it in 0.0..1.0 }.sorted()
        if (candidates.isEmpty()) return null

        return when (transition) {
            RacingBoundaryTransition.ENTER -> candidates.first()
            RacingBoundaryTransition.EXIT -> candidates.last()
        }
    }

    private fun sectorIntersectionParameter(
        center: RacingBoundaryPoint,
        radiusMeters: Double,
        sectorBearingDegrees: Double,
        halfAngleDegrees: Double,
        p0: Pair<Double, Double>,
        p1: Pair<Double, Double>,
        transition: RacingBoundaryTransition
    ): RacingBoundaryGeometry.SegmentIntersection? {
        val candidates = mutableListOf<RacingBoundaryGeometry.SegmentIntersection>()

        // Arc intersection (circle boundary)
        val arcCandidates = circleIntersections(radiusMeters, p0, p1)
        arcCandidates.forEach { intersection ->
            val point = RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
            val bearing = RacingBoundaryGeometry.bearingDegrees(center, point)
            if (angleDifferenceDegrees(bearing, sectorBearingDegrees) <= halfAngleDegrees) {
                candidates += intersection
            }
        }

        // Radial boundaries
        val startBearing = normalizeBearing(sectorBearingDegrees - halfAngleDegrees)
        val endBearing = normalizeBearing(sectorBearingDegrees + halfAngleDegrees)
        val radialStart = RacingBoundaryGeometry.pointOnBearing(center, startBearing, radiusMeters)
        val radialEnd = RacingBoundaryGeometry.pointOnBearing(center, endBearing, radiusMeters)

        val centerLocal = 0.0 to 0.0
        val radialStartLocal = RacingBoundaryGeometry.toLocalMeters(center, radialStart)
        val radialEndLocal = RacingBoundaryGeometry.toLocalMeters(center, radialEnd)

        RacingBoundaryGeometry.segmentIntersection(p0, p1, centerLocal, radialStartLocal)?.let { candidates += it }
        RacingBoundaryGeometry.segmentIntersection(p0, p1, centerLocal, radialEndLocal)?.let { candidates += it }

        val filtered = candidates.filter { it.t in 0.0..1.0 }.sortedBy { it.t }
        if (filtered.isEmpty()) return null
        return when (transition) {
            RacingBoundaryTransition.ENTER -> filtered.first()
            RacingBoundaryTransition.EXIT -> filtered.last()
        }
    }

    private fun circleIntersections(
        radiusMeters: Double,
        p0: Pair<Double, Double>,
        p1: Pair<Double, Double>
    ): List<RacingBoundaryGeometry.SegmentIntersection> {
        val dx = p1.first - p0.first
        val dy = p1.second - p0.second
        val a = dx * dx + dy * dy
        if (a <= 0.0) return emptyList()

        val b = 2.0 * (p0.first * dx + p0.second * dy)
        val c = p0.first * p0.first + p0.second * p0.second - radiusMeters * radiusMeters
        val discriminant = b * b - 4.0 * a * c
        if (discriminant < 0.0) return emptyList()

        val sqrtDisc = sqrt(discriminant)
        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)
        val candidates = listOf(t1, t2)
        return candidates.filter { it in 0.0..1.0 }.map { t ->
            val x = p0.first + dx * t
            val y = p0.second + dy * t
            RacingBoundaryGeometry.SegmentIntersection(t = t, u = 0.0, x = x, y = y)
        }
    }

    private fun boundaryPointFromParameter(
        center: RacingBoundaryPoint,
        radiusMeters: Double,
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        t: Double
    ): RacingBoundaryPoint {
        val (x0, y0) = RacingBoundaryGeometry.toLocalMeters(
            center,
            RacingBoundaryPoint(previousFix.lat, previousFix.lon)
        )
        val (x1, y1) = RacingBoundaryGeometry.toLocalMeters(
            center,
            RacingBoundaryPoint(currentFix.lat, currentFix.lon)
        )
        val x = x0 + (x1 - x0) * t
        val y = y0 + (y1 - y0) * t
        val bearing = Math.toDegrees(Math.atan2(x, y))
        val normalized = (bearing + 360.0) % 360.0
        return RacingBoundaryGeometry.pointOnBearing(center, normalized, radiusMeters)
    }

    private fun interpolateTime(startMillis: Long, endMillis: Long, t: Double): Long {
        val delta = endMillis - startMillis
        return startMillis + (delta * t).toLong()
    }

    private fun isInsideLineSector(
        center: RacingBoundaryPoint,
        sectorBearingDegrees: Double,
        radiusMeters: Double,
        epsilonMeters: Double,
        fix: RacingNavigationFix
    ): Boolean {
        val distance = distanceMeters(center, fix)
        if (distance > radiusMeters - epsilonMeters) return false
        val bearing = RacingGeometryUtils.calculateBearing(center.lat, center.lon, fix.lat, fix.lon)
        return angleDifferenceDegrees(bearing, sectorBearingDegrees) <= 90.0
    }

    private fun isInsideSector(
        center: RacingBoundaryPoint,
        sectorBearingDegrees: Double,
        halfAngleDegrees: Double,
        radiusMeters: Double,
        epsilonMeters: Double,
        fix: RacingNavigationFix
    ): Boolean {
        val distance = distanceMeters(center, fix)
        if (distance > radiusMeters - epsilonMeters) return false
        val bearing = RacingGeometryUtils.calculateBearing(center.lat, center.lon, fix.lat, fix.lon)
        return angleDifferenceDegrees(bearing, sectorBearingDegrees) <= halfAngleDegrees
    }

    private fun angleDifferenceDegrees(a: Double, b: Double): Double {
        val diff = (a - b + 540.0) % 360.0 - 180.0
        return abs(diff)
    }

    private fun normalizeBearing(bearing: Double): Double {
        val normalized = (bearing % 360.0 + 360.0) % 360.0
        return if (abs(normalized - 360.0) < 1e-6) 0.0 else normalized
    }

    private fun lineAnchors(
        crossingPoint: RacingBoundaryPoint,
        sectorBearingDegrees: Double,
        epsilonMeters: Double
    ): Pair<RacingBoundaryPoint, RacingBoundaryPoint> {
        val inside = RacingBoundaryGeometry.pointOnBearing(crossingPoint, sectorBearingDegrees, epsilonMeters)
        val outside = RacingBoundaryGeometry.pointOnBearing(crossingPoint, sectorBearingDegrees + 180.0, epsilonMeters)
        return inside to outside
    }

    private fun anchorsAlongTrack(
        previousFix: RacingNavigationFix,
        currentFix: RacingNavigationFix,
        crossingT: Double,
        epsilonMeters: Double,
        transition: RacingBoundaryTransition
    ): Pair<RacingBoundaryPoint, RacingBoundaryPoint> {
        val center = RacingBoundaryPoint(previousFix.lat, previousFix.lon)
        val p0 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(previousFix.lat, previousFix.lon))
        val p1 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(currentFix.lat, currentFix.lon))
        val dx = p1.first - p0.first
        val dy = p1.second - p0.second
        val segmentLength = sqrt(dx * dx + dy * dy)
        if (segmentLength <= 0.0) {
            val point = RacingBoundaryPoint(currentFix.lat, currentFix.lon)
            return point to point
        }
        val deltaT = (epsilonMeters / segmentLength).coerceIn(0.0, 0.25)
        val insideT = when (transition) {
            RacingBoundaryTransition.ENTER -> (crossingT + deltaT).coerceAtMost(1.0)
            RacingBoundaryTransition.EXIT -> (crossingT - deltaT).coerceAtLeast(0.0)
        }
        val outsideT = when (transition) {
            RacingBoundaryTransition.ENTER -> (crossingT - deltaT).coerceAtLeast(0.0)
            RacingBoundaryTransition.EXIT -> (crossingT + deltaT).coerceAtMost(1.0)
        }

        val insidePoint = RacingBoundaryPoint(
            lat = previousFix.lat + (currentFix.lat - previousFix.lat) * insideT,
            lon = previousFix.lon + (currentFix.lon - previousFix.lon) * insideT
        )
        val outsidePoint = RacingBoundaryPoint(
            lat = previousFix.lat + (currentFix.lat - previousFix.lat) * outsideT,
            lon = previousFix.lon + (currentFix.lon - previousFix.lon) * outsideT
        )
        return insidePoint to outsidePoint
    }
}
