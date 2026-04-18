package com.trust3.xcpro.map.replay

import com.trust3.xcpro.replay.IgcLog
import com.trust3.xcpro.replay.IgcMetadata
import com.trust3.xcpro.replay.IgcPoint
import com.trust3.xcpro.tasks.racing.RacingGeometryUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds a deterministic in-memory thermal replay for snail-trail validation.
 *
 * The generated log stays standard IGC-shaped data with 1 Hz fix timestamps.
 * Sub-second behavior comes from the existing replay cadence/densification path,
 * not from a custom file format.
 */
class SyntheticThermalReplayLogBuilder(
    private val defaultScenario: SyntheticThermalReplayScenario = SyntheticThermalReplayScenario()
) {

    fun build(variant: SyntheticThermalReplayVariant = SyntheticThermalReplayVariant.CLEAN): IgcLog =
        build(defaultScenario.copy(variant = variant))

    fun build(scenario: SyntheticThermalReplayScenario): IgcLog {
        require(scenario.durationMillis > 0L) { "Synthetic thermal duration must be > 0ms" }
        require(scenario.stepMillis > 0L) { "Synthetic thermal step must be > 0ms" }
        require(scenario.radiusMeters > 0.0) { "Synthetic thermal radius must be > 0m" }
        require(scenario.turnPeriodSeconds > 0.0) { "Synthetic thermal turn period must be > 0s" }
        require(scenario.startAltitudeMeters.isFinite()) { "Synthetic thermal start altitude must be finite" }
        require(scenario.endAltitudeMeters.isFinite()) { "Synthetic thermal end altitude must be finite" }
        require(scenario.indicatedAirspeedKmh > 0.0) { "Synthetic thermal IAS must be > 0 km/h" }
        require(scenario.trueAirspeedKmh > 0.0) { "Synthetic thermal TAS must be > 0 km/h" }

        val steps = ceil(scenario.durationMillis.toDouble() / scenario.stepMillis.toDouble()).toInt()
        val points = ArrayList<IgcPoint>(steps + 1)
        var cumulativeNorthMeters = 0.0
        var cumulativeEastMeters = 0.0

        for (index in 0..steps) {
            val elapsedMillis = minOf(index.toLong() * scenario.stepMillis, scenario.durationMillis)
            if (index > 0) {
                val previousElapsedMillis = minOf((index - 1L) * scenario.stepMillis, scenario.durationMillis)
                val deltaSeconds = (elapsedMillis - previousElapsedMillis) / 1_000.0
                val windVector = resolveWindVectorMetersPerSecond(
                    scenario = scenario,
                    elapsedSeconds = previousElapsedMillis / 1_000.0
                )
                cumulativeNorthMeters += windVector.northMs * deltaSeconds
                cumulativeEastMeters += windVector.eastMs * deltaSeconds
            }

            val fraction = elapsedMillis.toDouble() / scenario.durationMillis.toDouble()
            val angleRad = scenario.initialAngleRadians + (2.0 * PI * elapsedMillis / 1_000.0) / scenario.turnPeriodSeconds
            val orbitNorthMeters = scenario.radiusMeters * cos(angleRad)
            val orbitEastMeters = scenario.radiusMeters * sin(angleRad)
            val altitudeMeters = lerp(
                start = scenario.startAltitudeMeters,
                end = scenario.endAltitudeMeters,
                fraction = fraction
            )
            val (latitude, longitude) = translateFromOrigin(
                originLat = scenario.anchorLatitudeDeg,
                originLon = scenario.anchorLongitudeDeg,
                northMeters = cumulativeNorthMeters + orbitNorthMeters,
                eastMeters = cumulativeEastMeters + orbitEastMeters
            )

            points += IgcPoint(
                timestampMillis = scenario.startTimestampMillis + elapsedMillis,
                latitude = latitude,
                longitude = longitude,
                gpsAltitude = altitudeMeters,
                pressureAltitude = altitudeMeters,
                indicatedAirspeedKmh = scenario.indicatedAirspeedKmh,
                trueAirspeedKmh = scenario.trueAirspeedKmh
            )
        }

        return IgcLog(
            metadata = IgcMetadata(qnhHpa = scenario.qnhHpa),
            points = points
        )
    }

    private fun resolveWindVectorMetersPerSecond(
        scenario: SyntheticThermalReplayScenario,
        elapsedSeconds: Double
    ): WindVectorMs {
        val baseNorthMs = scenario.windFromSouthMs
        if (scenario.variant == SyntheticThermalReplayVariant.CLEAN) {
            return WindVectorMs(northMs = baseNorthMs, eastMs = 0.0)
        }
        val northNoiseMs =
            NOISY_WIND_PRIMARY_MS * sin(2.0 * PI * elapsedSeconds / NOISY_WIND_PRIMARY_PERIOD_S) +
                NOISY_WIND_SECONDARY_MS * sin(2.0 * PI * elapsedSeconds / NOISY_WIND_SECONDARY_PERIOD_S + NOISY_WIND_PHASE_RAD)
        val eastNoiseMs =
            NOISY_CROSSWIND_MS * sin(2.0 * PI * elapsedSeconds / NOISY_CROSSWIND_PERIOD_S + NOISY_CROSSWIND_PHASE_RAD)
        return WindVectorMs(
            northMs = baseNorthMs + northNoiseMs,
            eastMs = eastNoiseMs
        )
    }

    private fun translateFromOrigin(
        originLat: Double,
        originLon: Double,
        northMeters: Double,
        eastMeters: Double
    ): Pair<Double, Double> {
        val (northLat, northLon) = moveByBearing(
            latitude = originLat,
            longitude = originLon,
            bearingDeg = if (northMeters >= 0.0) 0.0 else 180.0,
            distanceMeters = abs(northMeters)
        )
        return moveByBearing(
            latitude = northLat,
            longitude = northLon,
            bearingDeg = if (eastMeters >= 0.0) 90.0 else 270.0,
            distanceMeters = abs(eastMeters)
        )
    }

    private fun moveByBearing(
        latitude: Double,
        longitude: Double,
        bearingDeg: Double,
        distanceMeters: Double
    ): Pair<Double, Double> {
        if (distanceMeters == 0.0) return latitude to longitude
        return RacingGeometryUtils.calculateDestinationPoint(
            latitude,
            longitude,
            bearingDeg,
            distanceMeters
        )
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double =
        start + (end - start) * fraction.coerceIn(0.0, 1.0)

    private data class WindVectorMs(
        val northMs: Double,
        val eastMs: Double
    )

    private companion object {
        private const val NOISY_WIND_PRIMARY_MS = 0.45
        private const val NOISY_WIND_SECONDARY_MS = 0.18
        private const val NOISY_CROSSWIND_MS = 0.24
        private const val NOISY_WIND_PRIMARY_PERIOD_S = 47.0
        private const val NOISY_WIND_SECONDARY_PERIOD_S = 13.0
        private const val NOISY_CROSSWIND_PERIOD_S = 61.0
        private const val NOISY_WIND_PHASE_RAD = 0.7
        private const val NOISY_CROSSWIND_PHASE_RAD = 1.2
    }
}

enum class SyntheticThermalReplayVariant {
    CLEAN,
    WIND_NOISY
}

data class SyntheticThermalReplayScenario(
    val anchorLatitudeDeg: Double = DEFAULT_ANCHOR_LATITUDE_DEG,
    val anchorLongitudeDeg: Double = DEFAULT_ANCHOR_LONGITUDE_DEG,
    val startTimestampMillis: Long = DEFAULT_START_TIME_MS,
    val durationMillis: Long = DEFAULT_DURATION_MS,
    val stepMillis: Long = DEFAULT_STEP_MS,
    val startAltitudeMeters: Double = DEFAULT_START_ALTITUDE_M,
    val endAltitudeMeters: Double = DEFAULT_END_ALTITUDE_M,
    val radiusMeters: Double = DEFAULT_RADIUS_M,
    val turnPeriodSeconds: Double = DEFAULT_TURN_PERIOD_S,
    val windFromSouthMs: Double = DEFAULT_SOUTH_WIND_MS,
    val indicatedAirspeedKmh: Double = DEFAULT_IAS_KMH,
    val trueAirspeedKmh: Double = DEFAULT_TAS_KMH,
    val qnhHpa: Double = DEFAULT_QNH_HPA,
    val initialAngleRadians: Double = DEFAULT_INITIAL_ANGLE_RAD,
    val variant: SyntheticThermalReplayVariant = SyntheticThermalReplayVariant.CLEAN
) {
    companion object {
        private const val FEET_TO_METERS = 0.3048
        private const val KNOT_TO_MS = 0.5144444444444445
        private const val DEFAULT_ANCHOR_LATITUDE_DEG = -33.852
        private const val DEFAULT_ANCHOR_LONGITUDE_DEG = 151.210
        private const val DEFAULT_START_TIME_MS = 1735689600000L // 2025-01-01T00:00:00Z
        private const val DEFAULT_DURATION_MS = 10L * 60L * 1_000L
        private const val DEFAULT_STEP_MS = 1_000L
        private const val DEFAULT_START_ALTITUDE_M = 1_000.0 * FEET_TO_METERS
        private const val DEFAULT_END_ALTITUDE_M = 6_000.0 * FEET_TO_METERS
        private const val DEFAULT_RADIUS_M = 90.0
        private const val DEFAULT_TURN_PERIOD_S = 35.0
        private const val DEFAULT_SOUTH_WIND_MS = 5.0 * KNOT_TO_MS
        private const val DEFAULT_IAS_KMH = 78.0
        private const val DEFAULT_TAS_KMH = 84.0
        private const val DEFAULT_QNH_HPA = 1013.25
        private const val DEFAULT_INITIAL_ANGLE_RAD = 0.0
    }
}
