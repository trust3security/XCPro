package com.example.xcpro.tasks.aat.map

import kotlin.math.*
import com.example.xcpro.tasks.aat.models.AATWaypoint
import com.example.xcpro.tasks.aat.models.AATLatLng
import com.example.xcpro.tasks.aat.models.AATAreaShape
import com.example.xcpro.tasks.aat.calculations.AATMathUtils

/**
 * AAT Movable Point Manager - Strategic Positioning Engine
 *
 * Core system for managing movable target points within AAT assigned areas.
 * Provides strategic positioning algorithms based on weather, wind, and flight optimization.
 *
 * Features:
 * - Strategic target point positioning within area boundaries
 * - Wind optimization calculations
 * - Weather-based routing suggestions
 * - Distance maximization algorithms
 * - Boundary validation and constraint enforcement
 * - Real-time position updates during flight
 *
 * Usage:
 * - Calculate optimal target positions for competitive advantage
 * - Move target points during flight based on changing conditions
 * - Validate all movements stay within assigned area bounds
 * - Optimize task distance within time constraints
 */
class AATMovablePointManager {

    fun isPointInsideArea(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        return when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE, AATAreaShape.LINE -> {
                val radiusKm = waypoint.assignedArea.radiusMeters / 1000.0
                val distance = AATMathUtils.calculateDistanceKm(
                    waypoint.lat, waypoint.lon,
                    point.latitude, point.longitude
                )
                distance <= radiusKm
            }
            AATAreaShape.SECTOR -> isPointInSectorOrKeyhole(waypoint, point)
        }
    }

    /**
     * Move target point while keeping it inside the assigned area geometry.
     * For sector/keyhole shapes the point is clamped to the angular wedge and radius band.
     * For cylinders/lines the point is clamped to the circle radius.
     */
    fun moveTargetPoint(
        waypoint: AATWaypoint,
        newLat: Double,
        newLon: Double
    ): AATWaypoint {
        val center = AATLatLng(waypoint.lat, waypoint.lon)
        val candidate = AATLatLng(newLat, newLon)

        val clamped = when (waypoint.assignedArea.shape) {
            AATAreaShape.CIRCLE, AATAreaShape.LINE -> clampToCircle(
                center,
                candidate,
                waypoint.assignedArea.radiusMeters / 1000.0
            )
            AATAreaShape.SECTOR -> clampToSectorOrKeyhole(waypoint, candidate)
        }

        return waypoint.copy(
            targetPoint = clamped,
            isTargetPointCustomized = true
        )
    }

    fun calculateOptimalPosition(
        waypoint: AATWaypoint,
        windDirection: Double, // degrees (0-360, where 0 = North)
        windSpeed: Double,     // km/h
        nextWaypoint: AATWaypoint? = null
    ): AATLatLng {
        val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

        // Strategic positioning factors
        val windOptimizationFactor = calculateWindOptimization(windDirection, windSpeed)
        val routeOptimizationFactor = nextWaypoint?.let {
            calculateRouteOptimization(waypoint, it)
        } ?: 0.0

        // Combine factors to determine optimal direction and distance from center
        val optimalBearing = when {
            nextWaypoint != null -> {
                // Optimize for route to next waypoint with wind consideration
                val routeBearing = AATMathUtils.calculateBearing(
                    AATLatLng(waypoint.lat, waypoint.lon),
                    AATLatLng(nextWaypoint.lat, nextWaypoint.lon)
                )
                combineWindAndRouteBearing(windDirection, routeBearing, windSpeed)
            }
            else -> {
                // Pure wind optimization when no next waypoint
                windDirection + 180.0 // Move upwind for better approach
            }
        }

        // Calculate optimal distance from center (typically 60-80% of radius for strategic flexibility)
        val optimalDistanceRatio = when {
            windSpeed > 40.0 -> 0.8 // Strong wind - move further for advantage
            windSpeed > 20.0 -> 0.7 // Moderate wind - good balance
            else -> 0.6 // Light wind - stay closer to center
        }

        val optimalDistance = areaRadiusKm * optimalDistanceRatio

        // Calculate new position
        val optimalLat = waypoint.lat + (optimalDistance / 111.0) * cos(Math.toRadians(optimalBearing))
        val optimalLon = waypoint.lon + (optimalDistance / (111.0 * cos(Math.toRadians(waypoint.lat)))) * sin(Math.toRadians(optimalBearing))

        return AATLatLng(optimalLat, optimalLon)
    }

    /**
     * Calculate strategic wind advantage positioning
     */
    private fun calculateWindOptimization(windDirection: Double, windSpeed: Double): Double {
        // Wind optimization strategy:
        // - Position upwind for better final approach
        // - Consider crosswind components for optimal track
        // - Stronger winds = more aggressive positioning

        return when {
            windSpeed > 40.0 -> 1.0 // Strong wind - maximum optimization
            windSpeed > 20.0 -> 0.7 // Moderate wind - balanced approach
            windSpeed > 10.0 -> 0.4 // Light wind - minor adjustment
            else -> 0.1 // Very light wind - minimal optimization
        }
    }

    /**
     * Calculate route optimization for next waypoint
     */
    private fun calculateRouteOptimization(current: AATWaypoint, next: AATWaypoint): Double {
        val distance = AATMathUtils.calculateDistanceKm(
            current.lat, current.lon,
            next.lat, next.lon
        )

        // Longer legs benefit more from route optimization
        return when {
            distance > 100.0 -> 1.0 // Long leg - maximum route optimization
            distance > 50.0 -> 0.7  // Medium leg - good optimization
            distance > 20.0 -> 0.4  // Short leg - minor optimization
            else -> 0.1            // Very short leg - minimal optimization
        }
    }

    /**
     * Combine wind and route bearing for optimal track
     */
    private fun combineWindAndRouteBearing(
        windDirection: Double,
        routeBearing: Double,
        windSpeed: Double
    ): Double {
        val windWeight = min(windSpeed / 50.0, 1.0) // Normalize wind influence
        val routeWeight = 1.0 - windWeight

        // Weighted combination of upwind positioning and route optimization
        val upwindBearing = windDirection + 180.0 // Opposite of wind direction

        return normalizeAngle(upwindBearing * windWeight + routeBearing * routeWeight)
    }


    /**
     * Check if target point is within area bounds
     */
    fun isTargetPointValid(waypoint: AATWaypoint, targetPoint: AATLatLng): Boolean {
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            targetPoint.latitude, targetPoint.longitude
        )

        return distance <= (waypoint.assignedArea.radiusMeters / 1000.0)
    }

    /**
     * Get distance from area center to target point
     */
    fun getTargetPointOffset(waypoint: AATWaypoint): Double {
        return AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            waypoint.targetPoint.latitude, waypoint.targetPoint.longitude
        )
    }

    /**
     * Reset target point to area center
     */
    fun resetToCenter(waypoint: AATWaypoint): AATWaypoint {
        return waypoint.copy(
            targetPoint = AATLatLng(waypoint.lat, waypoint.lon),
            isTargetPointCustomized = false
        )
    }

    /**
     * Get recommended positioning based on flight phase
     */
    fun getRecommendedPosition(
        waypoint: AATWaypoint,
        flightPhase: AATFlightPhase,
        windDirection: Double,
        windSpeed: Double
    ): AATLatLng {
        return when (flightPhase) {
            AATFlightPhase.EARLY_TASK -> {
                // Early in task - conservative positioning near center
                val centerBias = 0.3
                val optimalPos = calculateOptimalPosition(waypoint, windDirection, windSpeed)
                interpolatePosition(
                    AATLatLng(waypoint.lat, waypoint.lon),
                    optimalPos,
                    centerBias
                )
            }
            AATFlightPhase.MID_TASK -> {
                // Mid task - balanced strategic positioning
                calculateOptimalPosition(waypoint, windDirection, windSpeed)
            }
            AATFlightPhase.LATE_TASK -> {
                // Late in task - aggressive positioning for maximum distance
                val aggressivePos = calculateOptimalPosition(waypoint, windDirection, windSpeed)
                val areaRadiusKm = waypoint.assignedArea.radiusMeters / 1000.0

                // Push to 90% of area radius for maximum distance
                val bearing = AATMathUtils.calculateBearing(
                    AATLatLng(waypoint.lat, waypoint.lon),
                    AATLatLng(aggressivePos.latitude, aggressivePos.longitude)
                )
                val aggressiveDistance = areaRadiusKm * 0.9

                val aggressiveLat = waypoint.lat + (aggressiveDistance / 111.0) * cos(Math.toRadians(bearing))
                val aggressiveLon = waypoint.lon + (aggressiveDistance / (111.0 * cos(Math.toRadians(waypoint.lat)))) * sin(Math.toRadians(bearing))

                AATLatLng(aggressiveLat, aggressiveLon)
            }
        }
    }

    /**
     * Interpolate between two positions
     */
    private fun interpolatePosition(
        pos1: AATLatLng,
        pos2: AATLatLng,
        factor: Double // 0.0 = pos1, 1.0 = pos2
    ): AATLatLng {
        val lat = pos1.latitude + (pos2.latitude - pos1.latitude) * factor
        val lon = pos1.longitude + (pos2.longitude - pos1.longitude) * factor
        return AATLatLng(lat, lon)
    }

    /**
     * Clamp a point to a circle boundary (used for cylinder/line shapes).
     */
    private fun clampToCircle(center: AATLatLng, point: AATLatLng, radiusKm: Double): AATLatLng {
        val distance = AATMathUtils.calculateDistanceKm(
            center.latitude, center.longitude,
            point.latitude, point.longitude
        )

        if (radiusKm <= 0.0 || distance <= radiusKm) return point

        val bearing = AATMathUtils.calculateBearing(center, point)
        return calculateDestination(center.latitude, center.longitude, bearing, radiusKm)
    }

    /**
     * Clamp a point to a sector/keyhole: keep angle inside sector and distance within radii.
     * If inside the inner cylinder of a keyhole, leave the point unchanged.
     */
    private fun clampToSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): AATLatLng {
        val center = AATLatLng(waypoint.lat, waypoint.lon)
        val distanceKm = AATMathUtils.calculateDistanceKm(
            center.latitude, center.longitude,
            point.latitude, point.longitude
        )

        val innerRadiusKm = max(0.0, waypoint.assignedArea.innerRadiusMeters / 1000.0)
        val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

        // Inside inner cylinder: no angular restriction, no movement.
        if (innerRadiusKm > 0.0 && distanceKm <= innerRadiusKm) {
            return point
        }

        val bearing = AATMathUtils.calculateBearing(center, point)
        val angleInside = isAngleInSector(
            bearing,
            waypoint.assignedArea.startAngleDegrees,
            waypoint.assignedArea.endAngleDegrees
        )

        val clampedBearing = if (angleInside) {
            bearing
        } else {
            clampAngleToSector(
                bearing,
                waypoint.assignedArea.startAngleDegrees,
                waypoint.assignedArea.endAngleDegrees
            )
        }

        val clampedDistance = distanceKm.coerceIn(innerRadiusKm, outerRadiusKm)
        return calculateDestination(center.latitude, center.longitude, clampedBearing, clampedDistance)
    }

    private fun clampAngleToSector(angle: Double, startAngle: Double, endAngle: Double): Double {
        val normalizedAngle = normalizeAngle(angle)
        val normalizedStart = normalizeAngle(startAngle)
        val normalizedEnd = normalizeAngle(endAngle)

        if (isAngleInSector(normalizedAngle, normalizedStart, normalizedEnd)) {
            return normalizedAngle
        }

        val distanceToStart = angularDistance(normalizedAngle, normalizedStart)
        val distanceToEnd = angularDistance(normalizedAngle, normalizedEnd)
        return if (distanceToStart <= distanceToEnd) normalizedStart else normalizedEnd
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val diff = abs(a - b)
        return min(diff, 360.0 - diff)
    }

    private fun calculateDestination(lat: Double, lon: Double, bearing: Double, distanceKm: Double): AATLatLng {
        val earthRadiusKm = 6371.0
        val bearingRad = Math.toRadians(bearing)
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val angularDistance = distanceKm / earthRadiusKm

        val destLatRad = asin(
            sin(latRad) * cos(angularDistance) +
            cos(latRad) * sin(angularDistance) * cos(bearingRad)
        )

        val destLonRad = lonRad + atan2(
            sin(bearingRad) * sin(angularDistance) * cos(latRad),
            cos(angularDistance) - sin(latRad) * sin(destLatRad)
        )

        return AATLatLng(
            latitude = Math.toDegrees(destLatRad),
            longitude = Math.toDegrees(destLonRad)
        )
    }

    /**
     * Normalize angle to 0-360 degrees
     */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }

    /**
     * Check if a point is within a sector or keyhole observation zone.
     * Inner cylinder (keyhole) is always valid; otherwise apply radius and angle limits.
     */
    private fun isPointInSectorOrKeyhole(waypoint: AATWaypoint, point: AATLatLng): Boolean {
        val distance = AATMathUtils.calculateDistanceKm(
            waypoint.lat, waypoint.lon,
            point.latitude, point.longitude
        )

        val innerRadiusKm = waypoint.assignedArea.innerRadiusMeters / 1000.0
        val outerRadiusKm = waypoint.assignedArea.outerRadiusMeters / 1000.0

        // Inner cylinder is always valid (full 360 degrees)
        if (innerRadiusKm > 0.0 && distance <= innerRadiusKm) {
            return true
        }

        if (distance > outerRadiusKm) {
            return false
        }

        val bearingToPoint = AATMathUtils.calculateBearing(
            AATLatLng(waypoint.lat, waypoint.lon),
            point
        )

        val startAngle = waypoint.assignedArea.startAngleDegrees
        val endAngle = waypoint.assignedArea.endAngleDegrees

        return isAngleInSector(bearingToPoint, startAngle, endAngle)
    }

    /**
     * Check if an angle is within a sector's angular range.
     * Uses a small tolerance to make edge selection user-friendly.
     */
    private fun isAngleInSector(angle: Double, startAngle: Double, endAngle: Double): Boolean {
        val normalizedAngle = normalizeAngle(angle)
        val normalizedStart = normalizeAngle(startAngle)
        val normalizedEnd = normalizeAngle(endAngle)

        val angleTolerance = 5.0

        return if (normalizedEnd >= normalizedStart) {
            (normalizedAngle >= normalizedStart - angleTolerance) &&
            (normalizedAngle <= normalizedEnd + angleTolerance)
        } else {
            // Sector crosses 0-degree boundary (e.g., 350 to 10)
            (normalizedAngle >= normalizedStart - angleTolerance) ||
            (normalizedAngle <= normalizedEnd + angleTolerance)
        }
    }
}

/**
 * Flight phases for strategic positioning
 */
enum class AATFlightPhase {
    EARLY_TASK,  // Conservative positioning, safety first
    MID_TASK,    // Balanced strategic positioning
    LATE_TASK    // Aggressive positioning, maximize distance
}
