package com.example.xcpro.glide

import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.tasks.core.RacingAltitudeReference
import com.example.xcpro.tasks.racing.RacingGeometryUtils
import com.example.xcpro.weather.wind.model.WindState
import javax.inject.Inject
import kotlin.math.cos

internal data class GlideSolution(
    val valid: Boolean,
    val invalidReason: GlideInvalidReason? = null,
    val requiredGlideRatio: Double = Double.NaN,
    val arrivalHeightMeters: Double = Double.NaN,
    val requiredAltitudeMeters: Double = Double.NaN,
    val arrivalHeightMc0Meters: Double = Double.NaN,
    val distanceRemainingMeters: Double = Double.NaN
) {
    companion object {
        fun invalid(reason: GlideInvalidReason): GlideSolution = GlideSolution(
            valid = false,
            invalidReason = reason
        )
    }
}

class FinalGlideUseCase @Inject constructor(
    private val sinkProvider: StillAirSinkProvider
) {
    internal fun solve(
        completeData: CompleteFlightData,
        windState: WindState?,
        target: GlideTargetSnapshot,
        reserveMeters: Double = 0.0
    ): GlideSolution {
        if (!target.valid) {
            return GlideSolution.invalid(target.invalidReason)
        }

        val gps = completeData.gps ?: return GlideSolution.invalid(GlideInvalidReason.NO_POSITION)
        val navAltitudeMeters = completeData.navAltitude.value
        if (!navAltitudeMeters.isFinite()) {
            return GlideSolution.invalid(GlideInvalidReason.NO_ALTITUDE)
        }

        val bounds = sinkProvider.iasBoundsMs() ?: return GlideSolution.invalid(GlideInvalidReason.NO_POLAR)
        val finishConstraint = target.finishConstraint ?: return GlideSolution.invalid(GlideInvalidReason.NO_FINISH_ALTITUDE)
        if (finishConstraint.altitudeReference == RacingAltitudeReference.QNH && !completeData.isQNHCalibrated) {
            return GlideSolution.invalid(GlideInvalidReason.NO_ALTITUDE)
        }

        val route = buildRoute(
            currentLat = gps.position.latitude,
            currentLon = gps.position.longitude,
            remainingWaypoints = target.remainingWaypoints
        ) ?: return GlideSolution.invalid(GlideInvalidReason.INVALID_ROUTE)
        val distanceRemainingMeters = route.sumOf { it.distanceMeters }
        if (!distanceRemainingMeters.isFinite() || distanceRemainingMeters <= 0.0) {
            return GlideSolution.invalid(GlideInvalidReason.INVALID_ROUTE)
        }

        val activeMcResult = solveRouteAltitudeLoss(
            route = route,
            macCreadyMs = completeData.macCready.coerceAtLeast(0.0),
            windState = windState,
            bounds = bounds
        ) ?: return GlideSolution.invalid(GlideInvalidReason.INVALID_SPEED)
        val mc0Result = solveRouteAltitudeLoss(
            route = route,
            macCreadyMs = 0.0,
            windState = windState,
            bounds = bounds
        ) ?: return GlideSolution.invalid(GlideInvalidReason.INVALID_SPEED)

        val finishRequiredAltitudeMeters = finishConstraint.requiredAltitudeMeters + reserveMeters.coerceAtLeast(0.0)
        val availableHeightMeters = navAltitudeMeters - finishRequiredAltitudeMeters
        val requiredGlideRatio = if (availableHeightMeters > 0.0) {
            distanceRemainingMeters / availableHeightMeters
        } else {
            Double.POSITIVE_INFINITY
        }

        return GlideSolution(
            valid = true,
            requiredGlideRatio = requiredGlideRatio,
            arrivalHeightMeters = navAltitudeMeters - finishRequiredAltitudeMeters - activeMcResult.altitudeLossMeters,
            requiredAltitudeMeters = finishRequiredAltitudeMeters + activeMcResult.altitudeLossMeters,
            arrivalHeightMc0Meters = navAltitudeMeters - finishRequiredAltitudeMeters - mc0Result.altitudeLossMeters,
            distanceRemainingMeters = distanceRemainingMeters
        )
    }

    private fun buildRoute(
        currentLat: Double,
        currentLon: Double,
        remainingWaypoints: List<GlideRoutePoint>
    ): List<RouteLeg>? {
        if (remainingWaypoints.isEmpty()) return null
        val route = mutableListOf<RouteLeg>()
        var legStart = RoutePoint(lat = currentLat, lon = currentLon)
        remainingWaypoints.forEach { waypoint ->
            val legEnd = RoutePoint(lat = waypoint.lat, lon = waypoint.lon)
            val distanceMeters = RacingGeometryUtils.haversineDistanceMeters(
                legStart.lat,
                legStart.lon,
                legEnd.lat,
                legEnd.lon
            )
            if (!distanceMeters.isFinite() || distanceMeters < 0.0) {
                return null
            }
            if (distanceMeters > 0.0) {
                route += RouteLeg(
                    distanceMeters = distanceMeters,
                    bearingDeg = RacingGeometryUtils.calculateBearing(
                        legStart.lat,
                        legStart.lon,
                        legEnd.lat,
                        legEnd.lon
                    )
                )
            }
            legStart = legEnd
        }
        return route.takeIf { it.isNotEmpty() }
    }

    private fun solveRouteAltitudeLoss(
        route: List<RouteLeg>,
        macCreadyMs: Double,
        windState: WindState?,
        bounds: SpeedBoundsMs
    ): RouteSolution? {
        var altitudeLossMeters = 0.0
        route.forEach { leg ->
            val headwindMs = resolveHeadwindMs(windState, leg.bearingDeg)
            val airspeedMs = findOptimalSpeed(
                macCreadyMs = macCreadyMs,
                headwindMs = headwindMs,
                bounds = bounds
            ) ?: return null
            val sinkMs = sinkProvider.sinkAtSpeed(airspeedMs)
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val groundSpeedMs = airspeedMs - headwindMs
            if (!groundSpeedMs.isFinite() || groundSpeedMs <= MIN_GROUNDSPEED_MS) {
                return null
            }
            altitudeLossMeters += leg.distanceMeters * sinkMs / groundSpeedMs
        }
        return RouteSolution(altitudeLossMeters = altitudeLossMeters)
    }

    private fun findOptimalSpeed(
        macCreadyMs: Double,
        headwindMs: Double,
        bounds: SpeedBoundsMs
    ): Double? {
        var speedMs = bounds.minMs
        var bestSpeedMs: Double? = null
        var bestScore = Double.POSITIVE_INFINITY

        while (speedMs <= bounds.maxMs + SPEED_SCAN_EPSILON_MS) {
            val sinkMs = sinkProvider.sinkAtSpeed(speedMs)
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
            val groundSpeedMs = speedMs - headwindMs
            if (groundSpeedMs > MIN_GROUNDSPEED_MS) {
                val score = (sinkMs + macCreadyMs) / groundSpeedMs
                if (score < bestScore) {
                    bestScore = score
                    bestSpeedMs = speedMs
                }
            }
            speedMs += SPEED_SCAN_STEP_MS
        }

        return bestSpeedMs
    }

    private fun resolveHeadwindMs(
        windState: WindState?,
        courseBearingDeg: Double
    ): Double {
        val vector = windState?.vector ?: return 0.0
        val relativeWindRad = Math.toRadians(normalizeBearing(vector.directionFromDeg - courseBearingDeg))
        return vector.speed * cos(relativeWindRad)
    }

    private fun normalizeBearing(valueDeg: Double): Double {
        return ((valueDeg % FULL_CIRCLE_DEG) + FULL_CIRCLE_DEG) % FULL_CIRCLE_DEG
    }

    private data class RoutePoint(
        val lat: Double,
        val lon: Double
    )

    private data class RouteLeg(
        val distanceMeters: Double,
        val bearingDeg: Double
    )

    private data class RouteSolution(
        val altitudeLossMeters: Double
    )

    private companion object {
        private const val MIN_GROUNDSPEED_MS = 0.5
        private const val SPEED_SCAN_STEP_MS = 0.5
        private const val SPEED_SCAN_EPSILON_MS = 1e-6
        private const val FULL_CIRCLE_DEG = 360.0
    }
}
