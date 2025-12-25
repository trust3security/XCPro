package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GRAVITY
import kotlin.math.abs

private const val MIN_GATE_DT_SECONDS = 0.02  // reject duplicate/too-fast timestamps
private const val MIN_DERIVATIVE_DT_SECONDS = 0.05
// GPS/baro cadences vary widely (phone GPS can be ~1Hz; replay is typically 1Hz).
// Keep this loose enough to avoid NaN vario during normal jitter, while still rejecting
// long gaps (dropouts) that would make derivatives meaningless.
private const val MAX_DERIVATIVE_DT_SECONDS = 5.0

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
        val tasValid: Boolean,
        val pressureVario: Double,
        val baroVario: Double,
        val gpsVario: Double,
        val bruttoVario: Double,
        val varioSource: String,
        val varioValid: Boolean,
        val xcsoarVario: Double,
        val xcsoarVarioValid: Boolean
    )

    fun buildSnapshot(
        navBaroAltitudeEnabled: Boolean,
        baroAltitude: Double,
        gpsAltitude: Double,
        baroResult: BarometricAltitudeData?,
        isQnhCalibrated: Boolean,
        teVario: Double?,
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

        // TE altitude requires an airspeed estimate (not just GPS ground speed), otherwise energy height
        // changes would inject bogus "lift/sink" into thermal tracking during turns/accels.
        val energyHeight = if (
            airspeedSource != AirspeedSource.GPS_GROUND &&
            trueAirspeedMs.isFinite()
        ) {
            (trueAirspeedMs * trueAirspeedMs) / (2.0 * GRAVITY)
        } else 0.0
        val teAltitude = navAltitude + energyHeight

        val pressureVario = deriveVario(pressureAltitude = baroResult?.pressureAltitudeMeters ?: baroAltitude, currentTime = currentTime, altitudeType = AltitudeType.PRESSURE)
        val baroVario = deriveVario(pressureAltitude = baroAltitude, currentTime = currentTime, altitudeType = AltitudeType.BARO)
        val gpsVario = deriveVario(pressureAltitude = gpsAltitude, currentTime = currentTime, altitudeType = AltitudeType.GPS)

        val (bruttoVario, varioSource) = if (teVario != null && teVario.isFinite()) {
            teVario to "TE"
        } else {
            val candidates = listOf(
                "GPS" to gpsVario,
                "PRESSURE" to pressureVario,
                "BARO" to baroVario
            ).filter { it.second.isFinite() }
            val best = candidates.maxByOrNull { abs(it.second) }
            if (best != null) best.second to best.first else Double.NaN to "NONE"
        }
        val varioValid = bruttoVario.isFinite()

        val xcsoarVario = when {
            pressureVario.isFinite() -> pressureVario
            baroVario.isFinite() -> baroVario
            gpsVario.isFinite() -> gpsVario
            else -> Double.NaN
        }.guardVario()
        val xcsoarVarioValid = xcsoarVario.isFinite()

        return SensorSnapshot(
            navAltitude = navAltitude,
            teAltitude = teAltitude,
            indicatedAirspeedMs = indicatedAirspeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedSource = airspeedSource,
            tasValid = tasValid,
            pressureVario = pressureVario,
            baroVario = baroVario,
            gpsVario = gpsVario,
            bruttoVario = bruttoVario,
            varioSource = varioSource,
            varioValid = varioValid,
            xcsoarVario = xcsoarVario,
            xcsoarVarioValid = xcsoarVarioValid
        )
    }

    private enum class AltitudeType { PRESSURE, BARO, GPS }

    private var prevPressureAltitude: Double? = null
    private var prevPressureTime: Long = -1L
    private var prevBaroAltitude: Double? = null
    private var prevBaroTime: Long = -1L
    private var prevGpsAltitude: Double? = null
    private var prevGpsTime: Long = -1L

    private fun deriveVario(pressureAltitude: Double, currentTime: Long, altitudeType: AltitudeType): Double {
        if (!pressureAltitude.isFinite()) return Double.NaN

        val (prevAlt, prevTime) = when (altitudeType) {
            AltitudeType.PRESSURE -> prevPressureAltitude to prevPressureTime
            AltitudeType.BARO -> prevBaroAltitude to prevBaroTime
            AltitudeType.GPS -> prevGpsAltitude to prevGpsTime
        }

        if (prevTime < 0L) {
            remember(altitudeType, pressureAltitude, currentTime)
            return Double.NaN
        }

        val dt = (currentTime - prevTime) / 1000.0
        if (dt <= 0.0 || dt < MIN_DERIVATIVE_DT_SECONDS) {
            remember(altitudeType, pressureAltitude, currentTime)
            return Double.NaN
        }

        val vario = if (prevAlt != null && dt <= MAX_DERIVATIVE_DT_SECONDS) {
            (pressureAltitude - prevAlt) / dt
        } else Double.NaN

        remember(altitudeType, pressureAltitude, currentTime)
        return vario.guardVario()
    }

    private fun remember(type: AltitudeType, altitude: Double, time: Long) {
        when (type) {
            AltitudeType.PRESSURE -> {
                prevPressureAltitude = altitude
                prevPressureTime = time
            }
            AltitudeType.BARO -> {
                prevBaroAltitude = altitude
                prevBaroTime = time
            }
            AltitudeType.GPS -> {
                prevGpsAltitude = altitude
                prevGpsTime = time
            }
        }
    }

    fun resetDerivatives() {
        prevPressureAltitude = null
        prevPressureTime = -1L
        prevBaroAltitude = null
        prevBaroTime = -1L
        prevGpsAltitude = null
        prevGpsTime = -1L
    }

    private fun Double.guardVario(): Double =
        if (!this.isFinite() || kotlin.math.abs(this) > 10.0) Double.NaN else this
}
