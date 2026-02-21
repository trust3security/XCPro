package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import kotlin.math.PI
import kotlin.math.abs

internal object SectorAreaGeometrySupport {

    fun nearestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        val distanceToCenter = AATMathUtils.calculateDistance(from, center)
        val bearingToFrom = AATMathUtils.calculateBearing(center, from)
        val isInSectorAngle = AATMathUtils.isAngleBetween(bearingToFrom, startBearing, endBearing)

        if (isInSectorAngle) {
            return nearestPointForInAngle(
                center = center,
                innerRadius = innerRadius,
                outerRadius = outerRadius,
                distanceToCenter = distanceToCenter,
                bearingToFrom = bearingToFrom
            )
        }

        val distanceToStart = abs(AATMathUtils.angleDifference(bearingToFrom, startBearing))
        val distanceToEnd = abs(AATMathUtils.angleDifference(bearingToFrom, endBearing))
        val nearestBearing = if (distanceToStart < distanceToEnd) startBearing else endBearing

        val clampedRadius = when {
            distanceToCenter < (innerRadius ?: 0.0) -> innerRadius ?: outerRadius
            distanceToCenter > outerRadius -> outerRadius
            else -> distanceToCenter
        }
        return AATMathUtils.calculatePointAtBearing(center, nearestBearing, clampedRadius)
    }

    fun farthestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        val candidates = mutableListOf<AATLatLng>()

        appendArcPoints(
            sink = candidates,
            center = center,
            radius = outerRadius,
            startBearing = startBearing,
            endBearing = endBearing,
            steps = 20
        )

        if (innerRadius != null) {
            appendArcPoints(
                sink = candidates,
                center = center,
                radius = innerRadius,
                startBearing = startBearing,
                endBearing = endBearing,
                steps = 20
            )
        }

        candidates.add(AATMathUtils.calculatePointAtBearing(center, startBearing, outerRadius))
        candidates.add(AATMathUtils.calculatePointAtBearing(center, endBearing, outerRadius))

        if (innerRadius != null) {
            candidates.add(AATMathUtils.calculatePointAtBearing(center, startBearing, innerRadius))
            candidates.add(AATMathUtils.calculatePointAtBearing(center, endBearing, innerRadius))
        }

        return candidates.maxByOrNull { candidate ->
            AATMathUtils.calculateDistance(from, candidate)
        } ?: candidates.first()
    }

    fun generateBoundaryPoints(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double,
        numArcPoints: Int = 36
    ): List<AATLatLng> {
        val points = mutableListOf<AATLatLng>()
        val span = sectorSpan(startBearing, endBearing)
        val actualPoints = ((span / 360.0) * numArcPoints).toInt().coerceAtLeast(3)

        for (i in 0..actualPoints) {
            val fraction = i.toDouble() / actualPoints
            val bearing = bearingAtFraction(startBearing, endBearing, fraction)
            points.add(AATMathUtils.calculatePointAtBearing(center, bearing, outerRadius))
        }

        if (innerRadius != null) {
            for (i in actualPoints downTo 0) {
                val fraction = i.toDouble() / actualPoints
                val bearing = bearingAtFraction(startBearing, endBearing, fraction)
                points.add(AATMathUtils.calculatePointAtBearing(center, bearing, innerRadius))
            }
        } else {
            points.add(center)
        }

        return points
    }

    fun calculateAreaSizeKm2(
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        val outerRadiusKm = outerRadius / 1000.0
        val innerRadiusKm = (innerRadius ?: 0.0) / 1000.0
        val sectorFraction = sectorSpan(startBearing, endBearing) / 360.0
        return sectorFraction * PI * (outerRadiusKm * outerRadiusKm - innerRadiusKm * innerRadiusKm)
    }

    fun calculateOptimalBearing(
        approachBearing: Double,
        exitBearing: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        val bearingDiff = AATMathUtils.angleDifference(approachBearing, exitBearing)
        val idealBearing = AATMathUtils.normalizeAngle(approachBearing + bearingDiff / 2.0)

        if (AATMathUtils.isAngleBetween(idealBearing, startBearing, endBearing)) {
            return idealBearing
        }

        val distanceToStart = abs(AATMathUtils.angleDifference(idealBearing, startBearing))
        val distanceToEnd = abs(AATMathUtils.angleDifference(idealBearing, endBearing))
        return if (distanceToStart < distanceToEnd) startBearing else endBearing
    }

    private fun nearestPointForInAngle(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        distanceToCenter: Double,
        bearingToFrom: Double
    ): AATLatLng {
        return when {
            distanceToCenter <= (innerRadius ?: 0.0) -> {
                if (innerRadius != null) {
                    AATMathUtils.calculatePointAtBearing(center, bearingToFrom, innerRadius)
                } else {
                    AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
                }
            }
            distanceToCenter >= outerRadius -> {
                AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
            }
            else -> {
                val distanceToInner = innerRadius?.let { distanceToCenter - it } ?: Double.MAX_VALUE
                val distanceToOuter = outerRadius - distanceToCenter
                if (innerRadius != null && distanceToInner < distanceToOuter) {
                    AATMathUtils.calculatePointAtBearing(center, bearingToFrom, innerRadius)
                } else {
                    AATMathUtils.calculatePointAtBearing(center, bearingToFrom, outerRadius)
                }
            }
        }
    }

    private fun appendArcPoints(
        sink: MutableList<AATLatLng>,
        center: AATLatLng,
        radius: Double,
        startBearing: Double,
        endBearing: Double,
        steps: Int
    ) {
        for (i in 0..steps) {
            val fraction = i.toDouble() / steps
            val bearing = bearingAtFraction(startBearing, endBearing, fraction)
            sink.add(AATMathUtils.calculatePointAtBearing(center, bearing, radius))
        }
    }

    private fun sectorSpan(startBearing: Double, endBearing: Double): Double {
        return if (endBearing >= startBearing) {
            endBearing - startBearing
        } else {
            360 - startBearing + endBearing
        }
    }

    private fun bearingAtFraction(startBearing: Double, endBearing: Double, fraction: Double): Double {
        val span = sectorSpan(startBearing, endBearing)
        return if (endBearing >= startBearing) {
            startBearing + fraction * (endBearing - startBearing)
        } else {
            AATMathUtils.normalizeAngle(startBearing + fraction * span)
        }
    }
}
