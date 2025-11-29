package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GRAVITY

/**
 * Centralized front-end for sensor-derived fundamentals (nav altitude, IAS/TAS, TE altitude).
 * Mirrors XCSoar's BasicComputer responsibilities so downstream code consumes a single snapshot.
 */
internal class SensorFrontEnd(
    private val fusionBlackboard: FusionBlackboard
    // fusionBlackboard is only used for airspeed hold memory; everything else is stateless here.
) {

    data class SensorSnapshot(
        val navAltitude: Double,
        val teAltitude: Double,
        val indicatedAirspeedMs: Double,
        val trueAirspeedMs: Double,
        val airspeedSource: AirspeedSource,
        val tasValid: Boolean
    )

    fun buildSnapshot(
        navBaroAltitudeEnabled: Boolean,
        baroAltitude: Double,
        gpsAltitude: Double,
        baroResult: BarometricAltitudeData?,
        isQnhCalibrated: Boolean,
        airspeedEstimate: AirspeedEstimate?,
        currentTime: Long
    ): SensorSnapshot {
        val navAltitude = when {
            navBaroAltitudeEnabled && baroResult != null && isQnhCalibrated -> baroAltitude
            gpsAltitude.isFinite() -> gpsAltitude
            else -> baroAltitude
        }

        val activeEstimate = fusionBlackboard.resolveAirspeedHold(airspeedEstimate, currentTime)
        val indicatedAirspeedMs = activeEstimate?.indicatedMs ?: 0.0
        val trueAirspeedMs = activeEstimate?.trueMs ?: 0.0
        val airspeedSource = activeEstimate?.source ?: AirspeedSource.GPS_GROUND
        val tasValid = activeEstimate != null

        val energyHeight = if (trueAirspeedMs.isFinite()) {
            (trueAirspeedMs * trueAirspeedMs) / (2.0 * GRAVITY)
        } else 0.0
        val teAltitude = navAltitude + energyHeight

        return SensorSnapshot(
            navAltitude = navAltitude,
            teAltitude = teAltitude,
            indicatedAirspeedMs = indicatedAirspeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedSource = airspeedSource,
            tasValid = tasValid
        )
    }
}

