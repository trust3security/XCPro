package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.min

internal object AATMovablePointStrategySupport {
    private const val LONG_LEG_METERS = 100_000.0
    private const val MEDIUM_LEG_METERS = 50_000.0
    private const val SHORT_LEG_METERS = 20_000.0

    fun calculateOptimalPosition(
        waypoint: AATWaypoint,
        windDirection: Double,
        windSpeed: Double,
        nextWaypoint: AATWaypoint?
    ): AATLatLng {
        val areaRadiusMeters = waypoint.assignedArea.radiusMeters
        val windOptimizationFactor = calculateWindOptimization(windSpeed)
        val routeOptimizationFactor = nextWaypoint?.let {
            calculateRouteOptimization(waypoint, it)
        } ?: 0.0

        val optimalBearing = if (nextWaypoint != null) {
            val routeBearing = AATMathUtils.calculateBearing(
                AATLatLng(waypoint.lat, waypoint.lon),
                AATLatLng(nextWaypoint.lat, nextWaypoint.lon)
            )
            combineWindAndRouteBearing(
                windDirection = windDirection,
                routeBearing = routeBearing,
                windSpeed = windSpeed
            )
        } else {
            windDirection + 180.0
        }

        val optimalDistanceRatio = when {
            windSpeed > 40.0 -> 0.8
            windSpeed > 20.0 -> 0.7
            else -> 0.6
        }
        val optimizationBoost = 1.0 + (windOptimizationFactor + routeOptimizationFactor) * 0.1
        val optimalDistanceMeters = areaRadiusMeters * optimalDistanceRatio * optimizationBoost.coerceAtMost(1.0)

        return AATMathUtils.calculatePointAtBearingMeters(
            from = AATLatLng(waypoint.lat, waypoint.lon),
            bearing = optimalBearing,
            distanceMeters = optimalDistanceMeters
        )
    }

    fun getRecommendedPosition(
        waypoint: AATWaypoint,
        flightPhase: AATFlightPhase,
        windDirection: Double,
        windSpeed: Double
    ): AATLatLng {
        return when (flightPhase) {
            AATFlightPhase.EARLY_TASK -> {
                val centerBias = 0.3
                val optimalPos = calculateOptimalPosition(
                    waypoint = waypoint,
                    windDirection = windDirection,
                    windSpeed = windSpeed,
                    nextWaypoint = null
                )
                interpolatePosition(AATLatLng(waypoint.lat, waypoint.lon), optimalPos, centerBias)
            }

            AATFlightPhase.MID_TASK -> calculateOptimalPosition(
                waypoint = waypoint,
                windDirection = windDirection,
                windSpeed = windSpeed,
                nextWaypoint = null
            )

            AATFlightPhase.LATE_TASK -> {
                val aggressivePos = calculateOptimalPosition(
                    waypoint = waypoint,
                    windDirection = windDirection,
                    windSpeed = windSpeed,
                    nextWaypoint = null
                )
                val areaRadiusMeters = waypoint.assignedArea.radiusMeters
                val bearing = AATMathUtils.calculateBearing(
                    AATLatLng(waypoint.lat, waypoint.lon),
                    AATLatLng(aggressivePos.latitude, aggressivePos.longitude)
                )
                val aggressiveDistanceMeters = areaRadiusMeters * 0.9
                AATMathUtils.calculatePointAtBearingMeters(
                    from = AATLatLng(waypoint.lat, waypoint.lon),
                    bearing = bearing,
                    distanceMeters = aggressiveDistanceMeters
                )
            }
        }
    }

    private fun calculateWindOptimization(windSpeed: Double): Double {
        return when {
            windSpeed > 40.0 -> 1.0
            windSpeed > 20.0 -> 0.7
            windSpeed > 10.0 -> 0.4
            else -> 0.1
        }
    }

    private fun calculateRouteOptimization(current: AATWaypoint, next: AATWaypoint): Double {
        val distanceMeters = AATMathUtils.calculateDistanceMeters(current.lat, current.lon, next.lat, next.lon)
        return when {
            distanceMeters > LONG_LEG_METERS -> 1.0
            distanceMeters > MEDIUM_LEG_METERS -> 0.7
            distanceMeters > SHORT_LEG_METERS -> 0.4
            else -> 0.1
        }
    }

    private fun combineWindAndRouteBearing(
        windDirection: Double,
        routeBearing: Double,
        windSpeed: Double
    ): Double {
        val windWeight = min(windSpeed / 50.0, 1.0)
        val routeWeight = 1.0 - windWeight
        val upwindBearing = windDirection + 180.0
        return normalizeAngle(upwindBearing * windWeight + routeBearing * routeWeight)
    }

    private fun interpolatePosition(pos1: AATLatLng, pos2: AATLatLng, factor: Double): AATLatLng {
        val lat = pos1.latitude + (pos2.latitude - pos1.latitude) * factor
        val lon = pos1.longitude + (pos2.longitude - pos1.longitude) * factor
        return AATLatLng(lat, lon)
    }

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }
}
