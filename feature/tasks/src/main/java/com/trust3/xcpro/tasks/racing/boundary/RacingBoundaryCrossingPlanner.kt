package com.trust3.xcpro.tasks.racing.boundary

import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import com.trust3.xcpro.tasks.racing.navigation.RacingNavigationFix
import kotlin.math.abs
import kotlin.math.max

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

        val t = RacingBoundaryCrossingMath.intersectionParameter(
            center = center,
            radiusMeters = radiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            transition = transition
        ) ?: return null

        val crossingPoint = RacingBoundaryCrossingMath.boundaryPointFromParameter(
            center = center,
            radiusMeters = radiusMeters,
            previousFix = previousFix,
            currentFix = currentFix,
            t = t
        )
        val crossingTime = RacingBoundaryCrossingMath.interpolateTime(
            startMillis = previousFix.timestampMillis,
            endMillis = currentFix.timestampMillis,
            t = t
        )
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
            outsideAnchor = outsideAnchor,
            evidenceSource = RacingBoundaryEvidenceSource.CYLINDER_INTERSECTION
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

        val epsilon = max(
            epsilonPolicy.epsilonMeters(previousFix),
            epsilonPolicy.epsilonMeters(currentFix)
        )

        val halfLength = lineLengthMeters / 2.0
        val endA = RacingBoundaryGeometry.pointOnBearing(center, lineBearingDegrees, halfLength)
        val endB = RacingBoundaryGeometry.pointOnBearing(center, lineBearingDegrees + 180.0, halfLength)
        val p0 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(previousFix.lat, previousFix.lon))
        val p1 = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(currentFix.lat, currentFix.lon))
        val q0 = RacingBoundaryGeometry.toLocalMeters(center, endA)
        val q1 = RacingBoundaryGeometry.toLocalMeters(center, endB)
        val intersection = RacingBoundaryGeometry.segmentIntersection(p0, p1, q0, q1) ?: return null

        val previousRelation = classifyLineRelation(
            center = center,
            sectorBearingDegrees = sectorBearingDegrees,
            fix = previousFix,
            epsilonMeters = epsilon
        )
        val currentRelation = classifyLineRelation(
            center = center,
            sectorBearingDegrees = sectorBearingDegrees,
            fix = currentFix,
            epsilonMeters = epsilon
        )
        if (!isTransition(
                previousRelation = previousRelation,
                currentRelation = currentRelation,
                transition = transition
            )
        ) {
            return null
        }

        val crossingPoint = RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
        val crossingTime = RacingBoundaryCrossingMath.interpolateTime(
            startMillis = previousFix.timestampMillis,
            endMillis = currentFix.timestampMillis,
            t = intersection.t
        )
        val (insideAnchor, outsideAnchor) = RacingBoundaryCrossingMath.lineAnchors(
            crossingPoint = crossingPoint,
            sectorBearingDegrees = sectorBearingDegrees,
            epsilonMeters = epsilon
        )

        return RacingBoundaryCrossing(
            transition = transition,
            crossingPoint = crossingPoint,
            crossingTimeMillis = crossingTime,
            insideAnchor = insideAnchor,
            outsideAnchor = outsideAnchor,
            evidenceSource = RacingBoundaryEvidenceSource.LINE_INTERSECTION
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

        val previousInside = RacingBoundaryCrossingMath.isInsideSector(
            center = center,
            sectorBearingDegrees = sectorBearingDegrees,
            halfAngleDegrees = halfAngleDegrees,
            radiusMeters = radiusMeters,
            epsilonMeters = epsilon,
            fix = previousFix
        )
        val currentInside = RacingBoundaryCrossingMath.isInsideSector(
            center = center,
            sectorBearingDegrees = sectorBearingDegrees,
            halfAngleDegrees = halfAngleDegrees,
            radiusMeters = radiusMeters,
            epsilonMeters = epsilon,
            fix = currentFix
        )
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
        val intersection = RacingBoundaryCrossingMath.sectorIntersectionParameter(
            center = center,
            radiusMeters = radiusMeters,
            sectorBearingDegrees = sectorBearingDegrees,
            halfAngleDegrees = halfAngleDegrees,
            p0 = p0,
            p1 = p1,
            transition = transition
        ) ?: return null

        val crossingPoint = RacingBoundaryGeometry.fromLocalMeters(center, intersection.x, intersection.y)
        val crossingTime = RacingBoundaryCrossingMath.interpolateTime(
            startMillis = previousFix.timestampMillis,
            endMillis = currentFix.timestampMillis,
            t = intersection.t
        )
        val (insideAnchor, outsideAnchor) = RacingBoundaryCrossingMath.anchorsAlongTrack(
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
            outsideAnchor = outsideAnchor,
            evidenceSource = RacingBoundaryEvidenceSource.SECTOR_INTERSECTION
        )
    }

    private fun distanceMeters(center: RacingBoundaryPoint, fix: RacingNavigationFix): Double {
        return abs(RacingGeometryUtils.haversineDistanceMeters(center.lat, center.lon, fix.lat, fix.lon))
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
        return when (transition) {
            RacingBoundaryTransition.ENTER -> when {
                previousRelation == ZoneRelation.OUTSIDE && currentRelation == ZoneRelation.INSIDE -> true
                previousRelation == ZoneRelation.BORDER && currentRelation == ZoneRelation.INSIDE -> true
                else -> false
            }

            RacingBoundaryTransition.EXIT -> when {
                previousRelation == ZoneRelation.INSIDE && currentRelation == ZoneRelation.OUTSIDE -> true
                previousRelation == ZoneRelation.BORDER && currentRelation == ZoneRelation.OUTSIDE -> true
                else -> false
            }
        }
    }

    private fun classifyLineRelation(
        center: RacingBoundaryPoint,
        sectorBearingDegrees: Double,
        fix: RacingNavigationFix,
        epsilonMeters: Double
    ): ZoneRelation {
        val point = RacingBoundaryGeometry.toLocalMeters(center, RacingBoundaryPoint(fix.lat, fix.lon))
        val sectorRad = Math.toRadians(sectorBearingDegrees)
        val axisX = kotlin.math.sin(sectorRad)
        val axisY = kotlin.math.cos(sectorRad)
        val projection = point.first * axisX + point.second * axisY
        return when {
            projection > epsilonMeters -> ZoneRelation.INSIDE
            projection < -epsilonMeters -> ZoneRelation.OUTSIDE
            else -> ZoneRelation.BORDER
        }
    }
}
