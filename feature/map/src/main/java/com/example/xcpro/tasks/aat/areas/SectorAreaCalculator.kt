package com.example.xcpro.tasks.aat.areas

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AreaGeometry
import com.example.xcpro.tasks.aat.models.AssignedArea

class SectorAreaCalculator {

    fun isInsideArea(
        point: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Boolean {
        val distance = AATMathUtils.calculateDistance(point, center)
        if (distance > outerRadius) return false
        if (innerRadius != null && distance < innerRadius) return false

        val bearingToPoint = AATMathUtils.calculateBearing(center, point)
        return AATMathUtils.isAngleBetween(bearingToPoint, startBearing, endBearing)
    }

    fun isInsideArea(point: AATLatLng, area: AssignedArea): Boolean {
        return when (val geometry = area.geometry) {
            is AreaGeometry.Sector -> {
                isInsideArea(
                    point,
                    area.centerPoint,
                    geometry.innerRadius,
                    geometry.outerRadius,
                    geometry.startBearing,
                    geometry.endBearing
                )
            }
            else -> false
        }
    }

    fun nearestPointOnBoundary(
        from: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): AATLatLng {
        return SectorAreaGeometrySupport.nearestPointOnBoundary(
            from = from,
            center = center,
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startBearing = startBearing,
            endBearing = endBearing
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
        return SectorAreaGeometrySupport.farthestPointOnBoundary(
            from = from,
            center = center,
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startBearing = startBearing,
            endBearing = endBearing
        )
    }

    fun calculateCreditedFix(track: List<AATLatLng>, area: AssignedArea): AATLatLng? {
        val geometry = area.geometry
        if (geometry !is AreaGeometry.Sector) {
            return null
        }
        if (track.isEmpty()) {
            return null
        }

        val pointsInArea = track.filter { point ->
            isInsideArea(
                point,
                area.centerPoint,
                geometry.innerRadius,
                geometry.outerRadius,
                geometry.startBearing,
                geometry.endBearing
            )
        }
        if (pointsInArea.isEmpty()) {
            return null
        }

        return pointsInArea.maxByOrNull { point ->
            AATMathUtils.calculateDistance(point, area.centerPoint)
        }
    }

    fun calculateOptimalTouchPoint(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double,
        approachFrom: AATLatLng,
        exitTo: AATLatLng
    ): AATLatLng {
        val approachBearing = AATMathUtils.calculateBearing(approachFrom, center)
        val exitBearing = AATMathUtils.calculateBearing(center, exitTo)

        val optimalBearing = SectorAreaGeometrySupport.calculateOptimalBearing(
            approachBearing = approachBearing,
            exitBearing = exitBearing,
            startBearing = startBearing,
            endBearing = endBearing
        )

        return AATMathUtils.calculatePointAtBearing(center, optimalBearing, outerRadius)
    }

    fun generateBoundaryPoints(
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double,
        numArcPoints: Int = 36
    ): List<AATLatLng> {
        return SectorAreaGeometrySupport.generateBoundaryPoints(
            center = center,
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startBearing = startBearing,
            endBearing = endBearing,
            numArcPoints = numArcPoints
        )
    }

    fun calculateAreaSizeKm2(
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Double {
        return SectorAreaGeometrySupport.calculateAreaSizeKm2(
            innerRadius = innerRadius,
            outerRadius = outerRadius,
            startBearing = startBearing,
            endBearing = endBearing
        )
    }

    fun doesTrackIntersectArea(
        trackStart: AATLatLng,
        trackEnd: AATLatLng,
        center: AATLatLng,
        innerRadius: Double?,
        outerRadius: Double,
        startBearing: Double,
        endBearing: Double
    ): Boolean {
        if (isInsideArea(trackStart, center, innerRadius, outerRadius, startBearing, endBearing) ||
            isInsideArea(trackEnd, center, innerRadius, outerRadius, startBearing, endBearing)
        ) {
            return true
        }
        return false
    }
}
