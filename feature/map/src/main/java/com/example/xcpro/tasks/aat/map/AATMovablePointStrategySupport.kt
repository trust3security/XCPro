package com.example.xcpro.tasks.aat.map

import com.example.xcpro.tasks.aat.calculations.AATMathUtils
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATWaypoint
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal object AATMovablePointStrategySupport {

    fun calculateOptimalPosition(
        waypoint: AATWaypoint,
        windDirection: Double,
        windSpeed: Double,
        nextWaypoint: AATWaypoint?
    ): AATLatLng {
        val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0
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
        val optimalDistance = areaRadiusKm * optimalDistanceRatio * optimizationBoost.coerceAtMost(1.0)

        val optimalLat = waypoint.lat + (optimalDistance / 111.0) * cos(Math.toRadians(optimalBearing))
        val optimalLon =
            waypoint.lon + (optimalDistance / (111.0 * cos(Math.toRadians(waypoint.lat)))) * sin(Math.toRadians(optimalBearing))

        return AATLatLng(optimalLat, optimalLon)
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
                val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                val bearing = AATMathUtils.calculateBearing(
                    AATLatLng(waypoint.lat, waypoint.lon),
                    AATLatLng(aggressivePos.latitude, aggressivePos.longitude)
                )
                val aggressiveDistance = areaRadiusKm * 0.9
                val aggressiveLat = waypoint.lat + (aggressiveDistance / 111.0) * cos(Math.toRadians(bearing))
                val aggressiveLon =
                    waypoint.lon + (aggressiveDistance / (111.0 * cos(Math.toRadians(waypoint.lat)))) * sin(Math.toRadians(bearing))
                AATLatLng(aggressiveLat, aggressiveLon)
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
        val distance = AATMathUtils.calculateDistanceKm(current.lat, current.lon, next.lat, next.lon)
        return when {
            distance > 100.0 -> 1.0
            distance > 50.0 -> 0.7
            distance > 20.0 -> 0.4
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
