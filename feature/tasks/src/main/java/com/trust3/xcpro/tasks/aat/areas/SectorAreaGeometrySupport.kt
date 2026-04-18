package com.trust3.xcpro.tasks.aat.areas

import com.trust3.xcpro.tasks.aat.calculations.AATMathUtils
import com.trust3.xcpro.tasks.aat.models.AATLatLng
import kotlin.math.PI
import kotlin.math.abs

internal object SectorAreaGeometrySupport {
    private const val METERS_PER_KILOMETER = 1000.0

    fun nearestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        val distanceToCenterMeters = distanceMeters(from, center)
        val bearingToFrom = AATMathUtils.calculateBearing(center, from)
        val isInSectorAngle = AATMathUtils.isAngleBetween(bearingToFrom, startBearing, endBearing)

        if (isInSectorAngle) {
            return nearestPointForInAngle(
                center = center,
                innerRadius = innerRadius,
                outerRadius = outerRadius,
                distanceToCenterMeters = distanceToCenterMeters,
                bearingToFrom = bearingToFrom
            )
        }

        val distanceToStart = abs(AATMathUtils.angleDifference(bearingToFrom, startBearing))
        val distanceToEnd = abs(AATMathUtils.angleDifference(bearingToFrom, endBearing))
        val nearestBearing = if (distanceToStart < distanceToEnd) startBearing else endBearing

        val clampedRadiusMeters = when {
            distanceToCenterMeters < (innerRadius ?: 0.0) -> innerRadius ?: outerRadius
            distanceToCenterMeters > outerRadius -> outerRadius
            else -> distanceToCenterMeters
        }
        return AATMathUtils.calculatePointAtBearing(
            center,
            nearestBearing,
            metersToKilometers(clampedRadiusMeters)
        )
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

        candidates.add(
            AATMathUtils.calculatePointAtBearing(center, startBearing, metersToKilometers(outerRadius))
        )
        candidates.add(
            AATMathUtils.calculatePointAtBearing(center, endBearing, metersToKilometers(outerRadius))
        )

        if (innerRadius != null) {
            candidates.add(
                AATMathUtils.calculatePointAtBearing(center, startBearing, metersToKilometers(innerRadius))
            )
            candidates.add(
                AATMathUtils.calculatePointAtBearing(center, endBearing, metersToKilometers(innerRadius))
            )
        }

        return candidates.maxByOrNull { candidate ->
            distanceMeters(from, candidate)
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
            points.add(
                AATMathUtils.calculatePointAtBearing(
                    center,
                    bearing,
                    metersToKilometers(outerRadius)
                )
            )
        }

        if (innerRadius != null) {
            for (i in actualPoints downTo 0) {
                val fraction = i.toDouble() / actualPoints
                val bearing = bearingAtFraction(startBearing, endBearing, fraction)
                points.add(
                    AATMathUtils.calculatePointAtBearing(
                        center,
                        bearing,
                        metersToKilometers(innerRadius)
                    )
                )
            }
        } else {
            points.add(center)
        }

        return points
    }

    fun calculateAreaSizeMeters2(
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        val outerRadiusMeters = outerRadius
        val innerRadiusMeters = innerRadius ?: 0.0
        val sectorFraction = sectorSpan(startBearing, endBearing) / 360.0
        return sectorFraction * PI * (
            outerRadiusMeters * outerRadiusMeters - innerRadiusMeters * innerRadiusMeters
        )
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
        distanceToCenterMeters: Double,
        bearingToFrom: Double
    ): AATLatLng {
        return when {
            distanceToCenterMeters <= (innerRadius ?: 0.0) -> {
                if (innerRadius != null) {
                    AATMathUtils.calculatePointAtBearing(
                        center,
                        bearingToFrom,
                        metersToKilometers(innerRadius)
                    )
                } else {
                    AATMathUtils.calculatePointAtBearing(
                        center,
                        bearingToFrom,
                        metersToKilometers(outerRadius)
                    )
                }
            }
            distanceToCenterMeters >= outerRadius -> {
                AATMathUtils.calculatePointAtBearing(
                    center,
                    bearingToFrom,
                    metersToKilometers(outerRadius)
                )
            }
            else -> {
                val distanceToInner = innerRadius?.let { distanceToCenterMeters - it } ?: Double.MAX_VALUE
                val distanceToOuter = outerRadius - distanceToCenterMeters
                if (innerRadius != null && distanceToInner < distanceToOuter) {
                    AATMathUtils.calculatePointAtBearing(
                        center,
                        bearingToFrom,
                        metersToKilometers(innerRadius)
                    )
                } else {
                    AATMathUtils.calculatePointAtBearing(
                        center,
                        bearingToFrom,
                        metersToKilometers(outerRadius)
                    )
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
            sink.add(
                AATMathUtils.calculatePointAtBearing(
                    center,
                    bearing,
                    metersToKilometers(radius)
                )
            )
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

    private fun distanceMeters(from: AATLatLng, to: AATLatLng): Double {
        return AATMathUtils.calculateDistanceMeters(from, to)
    }

    private fun metersToKilometers(distanceMeters: Double): Double {
        return distanceMeters / METERS_PER_KILOMETER
    }
}
