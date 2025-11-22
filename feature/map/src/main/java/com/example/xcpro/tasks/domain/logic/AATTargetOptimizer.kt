package com.example.xcpro.tasks.domain.logic

import com.example.xcpro.tasks.domain.model.GeoPoint
import com.example.xcpro.tasks.domain.model.TaskPointDef
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Computes a target along the "isoline" inside an AAT observation zone.
 *
 * AI-NOTE: Approximates small areas with a local tangent plane; keeps targets
 * on the OZ arc between inbound/outbound bearings, clamped by the zone's angle.
 */
object AATTargetOptimizer {

    data class TargetResult(
        val target: GeoPoint,
        val rangeParam: Double // 0..1 along isoline chord
    )

    /**
     * @param prev previous point location
     * @param current current task point (with OZ radius encoded in zone)
     * @param next next point location
     * @param clamp 0..1 param along isoline arc within the OZ sweep
     */
    fun moveTarget(
        prev: GeoPoint,
        current: TaskPointDef,
        next: GeoPoint,
        clamp: Double
    ): TargetResult {
        val t = clamp.coerceIn(0.0, 1.0)
        val center = current.location
        val radiusMeters = current.zoneRadius()

        // Project neighbor points into a local tangent plane (meters).
        val inbound = toLocal(center, prev)
        val outbound = toLocal(center, next)

        val thetaIn = atan2(inbound.second, inbound.first)
        val thetaOut = atan2(outbound.second, outbound.first)

        // Sweep limited by OZ angle (segments default to 90deg; cylinders are full 360).
        val maxSweep = current.zoneAngleRad()
        val bisector = normalizeAngle(thetaIn + signedAngleDiff(thetaOut, thetaIn) / 2.0)
        val start = bisector - maxSweep / 2.0
        val end = bisector + maxSweep / 2.0
        val targetAngle = start + (end - start) * t

        val localTarget = Pair(
            radiusMeters * cos(targetAngle),
            radiusMeters * sin(targetAngle)
        )
        val target = fromLocal(center, localTarget)
        return TargetResult(target, t)
    }

    private fun TaskPointDef.zoneRadius(): Double = when (val z = zone) {
        is com.example.xcpro.tasks.domain.model.SegmentOZ -> z.radiusMeters
        is com.example.xcpro.tasks.domain.model.CylinderOZ -> z.radiusMeters
        is com.example.xcpro.tasks.domain.model.SectorOZ -> z.radiusMeters
        is com.example.xcpro.tasks.domain.model.KeyholeOZ -> z.outerRadiusMeters
        is com.example.xcpro.tasks.domain.model.AnnularSectorOZ -> z.outerRadiusMeters
        is com.example.xcpro.tasks.domain.model.LineOZ -> z.widthMeters / 2.0
    }

    private fun TaskPointDef.zoneAngleRad(): Double = when (val z = zone) {
        is com.example.xcpro.tasks.domain.model.SegmentOZ -> Math.toRadians(z.angleDeg)
        is com.example.xcpro.tasks.domain.model.SectorOZ -> Math.toRadians(z.angleDeg)
        is com.example.xcpro.tasks.domain.model.KeyholeOZ -> Math.toRadians(z.angleDeg)
        is com.example.xcpro.tasks.domain.model.AnnularSectorOZ -> Math.toRadians(z.angleDeg)
        else -> 2 * PI
    }

    private fun toLocal(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(origin.lat))
        val dx = (point.lon - origin.lon) * metersPerDegLon
        val dy = (point.lat - origin.lat) * metersPerDegLat
        return dx to dy
    }

    private fun fromLocal(origin: GeoPoint, local: Pair<Double, Double>): GeoPoint {
        val metersPerDegLat = 111_320.0
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(origin.lat))
        val lon = origin.lon + local.first / metersPerDegLon
        val lat = origin.lat + local.second / metersPerDegLat
        return GeoPoint(lat, lon)
    }

    private fun normalizeAngle(angle: Double): Double {
        var a = angle
        while (a <= -PI) a += 2 * PI
        while (a > PI) a -= 2 * PI
        return a
    }

    private fun signedAngleDiff(a: Double, b: Double): Double {
        val diff = normalizeAngle(a - b)
        return diff
    }
}
