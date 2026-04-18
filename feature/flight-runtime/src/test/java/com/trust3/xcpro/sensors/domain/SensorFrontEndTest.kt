package com.trust3.xcpro.sensors.domain

import com.trust3.xcpro.core.flight.calculations.BarometricAltitudeData
import com.trust3.xcpro.core.flight.calculations.ConfidenceLevel
import org.junit.Assert.*
import org.junit.Test

class SensorFrontEndTest {

    private fun baroCalibrated(qnh: Double = 1013.25, pressureAlt: Double = 1000.0) =
        BarometricAltitudeData(
            altitudeMeters = pressureAlt,
            qnh = qnh,
            isCalibrated = true,
            pressureHPa = qnh,
            temperatureCompensated = false,
            confidenceLevel = ConfidenceLevel.HIGH,
            pressureAltitudeMeters = pressureAlt,
            gpsDeltaMeters = null,
            lastCalibrationTime = 0L
        )

    @Test
    fun nav_altitude_prefers_baro_when_calibrated() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)

        val snapshot = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 1200.0,
            gpsAltitude = 1000.0,
            gpsTimestampMillis = 0L,
            baroResult = baroCalibrated(pressureAlt = 1200.0),
            isQnhCalibrated = true,
            teVario = null,
            airspeedEstimate = null,
            currentTime = 0L
        )

        assertEquals(1200.0, snapshot.navAltitude, 0.001)
    }

    @Test
    fun nav_altitude_falls_back_to_gps_when_not_calibrated() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)

        val snapshot = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 1200.0,
            gpsAltitude = 950.0,
            gpsTimestampMillis = 0L,
            baroResult = baroCalibrated().copy(isCalibrated = false),
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = null,
            currentTime = 0L
        )

        assertEquals(950.0, snapshot.navAltitude, 0.001)
    }

    @Test
    fun te_altitude_includes_energy_height() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)
        val tas = 20.0
        val navAlt = 1000.0
        val energy = (tas * tas) / (2.0 * FlightMetricsConstants.GRAVITY)

        val snapshot = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = navAlt,
            gpsAltitude = navAlt,
            gpsTimestampMillis = 0L,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = AirspeedEstimate(indicatedMs = tas, trueMs = tas, source = AirspeedSource.WIND_VECTOR),
            currentTime = 0L
        )

        assertEquals(navAlt + energy, snapshot.teAltitude, 0.001)
    }

    @Test
    fun airspeed_hold_returns_last_within_window() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)
        val estimate = AirspeedEstimate(indicatedMs = 12.0, trueMs = 20.0, source = AirspeedSource.WIND_VECTOR)

        fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 0.0,
            gpsAltitude = 0.0,
            gpsTimestampMillis = 0L,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = estimate,
            currentTime = 0L
        )

        val held = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 0.0,
            gpsAltitude = 0.0,
            gpsTimestampMillis = FlightMetricsConstants.SPEED_HOLD_MS - 500,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = null,
            currentTime = FlightMetricsConstants.SPEED_HOLD_MS - 500
        )

        assertTrue(held.tasValid)
        assertEquals(estimate.trueMs, held.trueAirspeedMs, 0.001)
        assertEquals(estimate.indicatedMs, held.indicatedAirspeedMs, 0.001)
    }

    @Test
    fun airspeed_hold_expires_after_window() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)
        val estimate = AirspeedEstimate(indicatedMs = 10.0, trueMs = 15.0, source = AirspeedSource.WIND_VECTOR)

        fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 0.0,
            gpsAltitude = 0.0,
            gpsTimestampMillis = 0L,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = estimate,
            currentTime = 0L
        )

        val expired = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 0.0,
            gpsAltitude = 0.0,
            gpsTimestampMillis = FlightMetricsConstants.SPEED_HOLD_MS + 1_000,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = null,
            currentTime = FlightMetricsConstants.SPEED_HOLD_MS + 1_000
        )

        assertFalse(expired.tasValid)
        assertEquals(0.0, expired.trueAirspeedMs, 0.0)
        assertEquals(0.0, expired.indicatedAirspeedMs, 0.0)
    }

    @Test
    fun pressure_baro_gps_vario_derivation() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)

        val s1 = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 1000.0,
            gpsAltitude = 1000.0,
            gpsTimestampMillis = 0L,
            baroResult = baroCalibrated(pressureAlt = 1000.0),
            isQnhCalibrated = true,
            teVario = null,
            airspeedEstimate = null,
            currentTime = 0L
        )
        assertTrue(s1.pressureVario.isNaN())

        val s2 = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 1010.0,
            gpsAltitude = 1010.0,
            gpsTimestampMillis = 1_000L,
            baroResult = baroCalibrated(pressureAlt = 1010.0),
            isQnhCalibrated = true,
            teVario = null,
            airspeedEstimate = null,
            currentTime = 1_000L
        )
        assertEquals(10.0, s2.pressureVario, 0.001)
        assertEquals(10.0, s2.baroVario, 0.001)
        assertEquals(10.0, s2.gpsVario, 0.001)
    }

    @Test
    fun brutto_uses_te_when_available_else_priority_fallback() {
        val bb = FusionBlackboard()
        val fe = SensorFrontEnd(bb)

        val withTe = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = 0.0,
            gpsAltitude = 0.0,
            gpsTimestampMillis = 0L,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = 2.5,
            airspeedEstimate = null,
            currentTime = 0L
        )
        assertEquals(2.5, withTe.bruttoVario, 0.001)
        assertEquals("TE", withTe.varioSource)

        val withGps = fe.buildSnapshot(
            navBaroAltitudeEnabled = true,
            baroAltitude = Double.NaN,
            gpsAltitude = 10.0,
            gpsTimestampMillis = 1_000L,
            baroResult = null,
            isQnhCalibrated = false,
            teVario = null,
            airspeedEstimate = null,
            currentTime = 1_000L
        )
        assertEquals("GPS", withGps.varioSource)
        assertTrue(withGps.bruttoVario.isFinite())
    }
}
