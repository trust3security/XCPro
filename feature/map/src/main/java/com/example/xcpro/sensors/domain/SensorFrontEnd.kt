package com.example.xcpro.sensors.domain

import com.example.dfcards.calculations.BarometricAltitudeData
import com.example.xcpro.sensors.domain.FlightMetricsConstants.GRAVITY

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
        val pressureAltitudeVario: Double,
        val pressureVario: Double,
        val baroVario: Double,
        val gpsVario: Double,
        val bruttoVario: Double,
        val varioSource: String,
        val varioValid: Boolean,
        val xcSoarVario: Double,
        val xcSoarVarioValid: Boolean
    )

    fun buildSnapshot(
        navBaroAltitudeEnabled: Boolean,
        baroAltitude: Double,
        gpsAltitude: Double,
        gpsTimestampMillis: Long,
        baroResult: BarometricAltitudeData?,
        isQnhCalibrated: Boolean,
        teVario: Double?,
        airspeedEstimate: AirspeedEstimate?,
        currentTime: Long,
        pressureVarioOverride: Double? = null
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
        val tasValid = activeEstimate != null && airspeedSource != AirspeedSource.GPS_GROUND

        // TE altitude requires an airspeed estimate (not just GPS ground speed), otherwise energy height
        // changes would inject bogus "lift/sink" into thermal tracking during turns/accels.
        val energyHeight = if (
            airspeedSource != AirspeedSource.GPS_GROUND &&
            trueAirspeedMs.isFinite()
        ) {
            (trueAirspeedMs * trueAirspeedMs) / (2.0 * GRAVITY)
        } else 0.0
        val teAltitude = navAltitude + energyHeight

        val pressureAltitudeVario = baroResult?.pressureAltitudeMeters
            ?.takeIf { it.isFinite() }
            ?.let { pressureAltitude ->
                deriveVario(
                    pressureAltitude = pressureAltitude,
                    currentTime = currentTime,
                    altitudeType = AltitudeType.PRESSURE
                )
            }
            ?: Double.NaN

        val pressureVario = pressureVarioOverride
            ?.takeIf { it.isFinite() }
            ?: pressureAltitudeVario

        val baroVario = baroResult
            ?.let { deriveVario(pressureAltitude = baroAltitude, currentTime = currentTime, altitudeType = AltitudeType.BARO) }
            ?: Double.NaN

        val gpsVario = deriveGpsVario(gpsAltitude = gpsAltitude, currentTime = gpsTimestampMillis)

        val (bruttoVario, varioSource) = when {
            teVario != null && teVario.isFinite() -> teVario to "TE"
            pressureVario.isFinite() -> pressureVario to "PRESSURE"
            baroVario.isFinite() -> baroVario to "BARO"
            gpsVario.isFinite() -> gpsVario to "GPS"
            else -> Double.NaN to "NONE"
        }
        val varioValid = bruttoVario.isFinite()

        val xcSoarVario = when {
            pressureVario.isFinite() -> pressureVario
            baroVario.isFinite() -> baroVario
            gpsVario.isFinite() -> gpsVario
            else -> Double.NaN
        }.guardVario()
        val xcSoarVarioValid = xcSoarVario.isFinite()

        return SensorSnapshot(
            navAltitude = navAltitude,
            teAltitude = teAltitude,
            indicatedAirspeedMs = indicatedAirspeedMs,
            trueAirspeedMs = trueAirspeedMs,
            airspeedSource = airspeedSource,
            tasValid = tasValid,
            pressureAltitudeVario = pressureAltitudeVario,
            pressureVario = pressureVario,
            baroVario = baroVario,
            gpsVario = gpsVario,
            bruttoVario = bruttoVario,
            varioSource = varioSource,
            varioValid = varioValid,
            xcSoarVario = xcSoarVario,
            xcSoarVarioValid = xcSoarVarioValid
        )
    }

    private enum class AltitudeType { PRESSURE, BARO, GPS }

    private var prevPressureAltitude: Double? = null
    private var prevPressureTime: Long = -1L
    private var lastPressureVario: Double? = null
    private var prevBaroAltitude: Double? = null
    private var prevBaroTime: Long = -1L
    private var lastBaroVario: Double? = null
    private var prevGpsAltitude: Double? = null
    private var prevGpsTime: Long = -1L
    private var lastGpsVario: Double? = null
    private val gpsAltitudeWindow = ArrayDeque<Double>(3)

    private fun deriveVario(pressureAltitude: Double, currentTime: Long, altitudeType: AltitudeType): Double {
        if (!pressureAltitude.isFinite()) return Double.NaN

        val (prevAlt, prevTime, lastVario) = when (altitudeType) {
            AltitudeType.PRESSURE -> Triple(prevPressureAltitude, prevPressureTime, lastPressureVario)
            AltitudeType.BARO -> Triple(prevBaroAltitude, prevBaroTime, lastBaroVario)
            AltitudeType.GPS -> Triple(prevGpsAltitude, prevGpsTime, lastGpsVario)
        }

        if (prevTime < 0L) {
            remember(altitudeType, pressureAltitude, currentTime)
            return Double.NaN
        }

        val dt = (currentTime - prevTime) / 1000.0
        if (dt <= 0.0 || dt < MIN_DERIVATIVE_DT_SECONDS) return lastVario ?: Double.NaN

        if (dt > MAX_DERIVATIVE_DT_SECONDS) {
            remember(altitudeType, pressureAltitude, currentTime, vario = null)
            return Double.NaN
        }

        val vario = if (prevAlt != null) (pressureAltitude - prevAlt) / dt else Double.NaN
        val guarded = vario.guardVario()
        remember(altitudeType, pressureAltitude, currentTime, vario = guarded.takeIf { it.isFinite() })
        return guarded
    }

    private fun remember(type: AltitudeType, altitude: Double, time: Long, vario: Double? = null) {
        when (type) {
            AltitudeType.PRESSURE -> {
                prevPressureAltitude = altitude
                prevPressureTime = time
                lastPressureVario = vario
            }
            AltitudeType.BARO -> {
                prevBaroAltitude = altitude
                prevBaroTime = time
                lastBaroVario = vario
            }
            AltitudeType.GPS -> {
                prevGpsAltitude = altitude
                prevGpsTime = time
                lastGpsVario = vario
            }
        }
    }

    fun resetDerivatives() {
        prevPressureAltitude = null
        prevPressureTime = -1L
        lastPressureVario = null
        prevBaroAltitude = null
        prevBaroTime = -1L
        lastBaroVario = null
        prevGpsAltitude = null
        prevGpsTime = -1L
        lastGpsVario = null
    }

    /**
     * GPS vario gets a tiny 3-sample median on altitude to reject single-sample spikes
     * before the derivative and clamp are applied.
     */
    private fun deriveGpsVario(gpsAltitude: Double, currentTime: Long): Double {
        if (!gpsAltitude.isFinite()) return Double.NaN

        // Seed or reset window when time moves backwards
        if (prevGpsTime < 0L || currentTime < prevGpsTime) {
            gpsAltitudeWindow.clear()
            gpsAltitudeWindow.addLast(gpsAltitude)
            prevGpsAltitude = gpsAltitude
            prevGpsTime = currentTime
            lastGpsVario = null
            return Double.NaN
        }

        val dt = (currentTime - prevGpsTime) / 1000.0
        if (dt <= 0.0 || dt < MIN_DERIVATIVE_DT_SECONDS) return lastGpsVario ?: Double.NaN
        if (dt > MAX_DERIVATIVE_DT_SECONDS) {
            prevGpsAltitude = gpsAltitude
            prevGpsTime = currentTime
            lastGpsVario = null
            gpsAltitudeWindow.clear()
            gpsAltitudeWindow.addLast(gpsAltitude)
            return Double.NaN
        }

        // Maintain last 3 altitude samples
        gpsAltitudeWindow.addLast(gpsAltitude)
        if (gpsAltitudeWindow.size > 3) gpsAltitudeWindow.removeFirst()
        val medianAltitude = gpsAltitudeWindow.sorted()[gpsAltitudeWindow.size / 2]

        val prevAlt = prevGpsAltitude
        val vario = if (prevAlt != null) (medianAltitude - prevAlt) / dt else Double.NaN
        val guarded = vario.guardVario()

        prevGpsAltitude = medianAltitude
        prevGpsTime = currentTime
        lastGpsVario = guarded.takeIf { it.isFinite() }
        return guarded
    }

    /**
     * Keep output finite and realistic:
     * - Drop only non‑finite inputs.
     * - Clamp extreme spikes to a plausible full-scale instead of emitting NaN (prevents UI freeze).
     */
    private fun Double.guardVario(): Double {
        if (!this.isFinite()) return Double.NaN
        return this.coerceIn(-15.0, 15.0)
    }

}
