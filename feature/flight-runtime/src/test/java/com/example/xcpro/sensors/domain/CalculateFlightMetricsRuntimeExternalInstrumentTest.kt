package com.example.xcpro.sensors.domain

import com.example.xcpro.common.flight.FlightMode
import com.example.xcpro.core.flight.calculations.BarometricAltitudeData
import com.example.xcpro.core.flight.calculations.ConfidenceLevel
import com.example.xcpro.external.ExternalInstrumentFlightSnapshot
import com.example.xcpro.external.TimedExternalValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalculateFlightMetricsRuntimeExternalInstrumentTest {

    @Test
    fun fresh_external_total_energy_vario_overrides_phone_computed_te_vario() {
        val useCase = newUseCase { -0.25 }

        val result = useCase.execute(
            request(
                currentTimeMillis = 10_000L,
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    totalEnergyVarioMps = TimedExternalValue(1.7, 9_500L)
                ),
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 9_800L
                )
            )
        )

        assertEquals(1.7, result.teVario!!, 1e-6)
        assertEquals("TE", result.varioSource)
    }

    @Test
    fun stale_external_total_energy_vario_falls_back_cleanly() {
        val useCase = newUseCase { -0.25 }

        useCase.execute(
            request(
                currentTimeMillis = 9_000L,
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 8_800L
                )
            )
        )

        val result = useCase.execute(
            request(
                currentTimeMillis = 10_000L,
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    totalEnergyVarioMps = TimedExternalValue(1.7, 7_500L)
                ),
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 9_800L
                )
            )
        )

        assertEquals(-0.25, result.teVario!!, 1e-6)
    }

    @Test
    fun fresh_external_pressure_altitude_overrides_only_pressure_altitude_branch() {
        val useCase = newUseCase()
        val baro = calibratedBaroResult(
            altitudeMeters = 900.0,
            pressureAltitudeMeters = 900.0,
            qnh = 1009.0,
            confidenceLevel = ConfidenceLevel.HIGH
        )

        useCase.execute(
            request(
                currentTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 1.0,
                altitude = 900.0,
                varioMs = 0.4,
                baroResult = baro,
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    pressureAltitudeM = TimedExternalValue(1_000.0, 1_000L)
                ),
                varioValidUntil = 0L
            )
        )

        val result = useCase.execute(
            request(
                currentTimeMillis = 2_000L,
                gpsTimestampMillis = 2_000L,
                deltaTimeSeconds = 1.0,
                altitude = 900.0,
                varioMs = 0.4,
                baroResult = baro,
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    pressureAltitudeM = TimedExternalValue(1_010.0, 2_000L)
                ),
                varioValidUntil = 0L
            )
        )

        assertEquals(1_010.0, result.pressureAltitude, 1e-6)
        assertEquals(900.0, result.baroAltitude, 1e-6)
        assertEquals(900.0, result.navAltitude, 1e-6)
        assertEquals(1009.0, result.qnh, 1e-6)
        assertEquals(ConfidenceLevel.HIGH, result.baroConfidence)
        assertEquals("PRESSURE", result.varioSource)
        assertEquals(10.0, result.verticalSpeed, 1e-6)
    }

    @Test
    fun stale_external_pressure_altitude_falls_back_cleanly() {
        val useCase = newUseCase()

        useCase.execute(
            request(
                currentTimeMillis = 1_000L,
                gpsTimestampMillis = 1_000L,
                deltaTimeSeconds = 1.0,
                altitude = 905.0,
                varioMs = 0.4,
                baroResult = calibratedBaroResult(altitudeMeters = 905.0, pressureAltitudeMeters = 905.0),
                varioValidUntil = 0L
            )
        )

        val result = useCase.execute(
            request(
                currentTimeMillis = 4_000L,
                gpsTimestampMillis = 4_000L,
                deltaTimeSeconds = 3.0,
                altitude = 910.0,
                varioMs = 0.4,
                baroResult = calibratedBaroResult(altitudeMeters = 910.0, pressureAltitudeMeters = 910.0),
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    pressureAltitudeM = TimedExternalValue(1_010.0, 1_000L)
                ),
                varioValidUntil = 0L
            )
        )

        assertEquals(910.0, result.pressureAltitude, 1e-6)
        assertEquals("PRESSURE", result.varioSource)
        assertEquals(5.0 / 3.0, result.verticalSpeed, 1e-6)
    }

    @Test
    fun replay_mode_ignores_external_inputs_entirely() {
        val useCase = newUseCase { -0.25 }

        useCase.execute(
            request(
                currentTimeMillis = 9_000L,
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 8_800L
                ),
                isReplayMode = true
            )
        )

        val result = useCase.execute(
            request(
                currentTimeMillis = 10_000L,
                baroResult = calibratedBaroResult(altitudeMeters = 900.0, pressureAltitudeMeters = 910.0),
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    pressureAltitudeM = TimedExternalValue(1_050.0, 9_900L),
                    totalEnergyVarioMps = TimedExternalValue(1.7, 9_900L)
                ),
                externalAirspeedSample = airspeedSample(
                    trueMs = 26.0,
                    indicatedMs = 24.0,
                    clockMillis = 9_800L
                ),
                isReplayMode = true
            )
        )

        assertEquals(910.0, result.pressureAltitude, 1e-6)
        assertEquals(-0.25, result.teVario!!, 1e-6)
    }

    @Test
    fun external_instrument_snapshot_has_no_airspeed_influence() {
        val useCase = newUseCase()

        val result = useCase.execute(
            request(
                currentTimeMillis = 10_000L,
                externalInstrumentSnapshot = ExternalInstrumentFlightSnapshot(
                    pressureAltitudeM = TimedExternalValue(1_010.0, 9_900L),
                    totalEnergyVarioMps = TimedExternalValue(1.2, 9_900L)
                )
            )
        )

        assertEquals("GPS", result.airspeedSourceLabel)
        assertNotEquals("SENSOR", result.airspeedSourceLabel)
    }

    private fun request(
        currentTimeMillis: Long,
        gpsTimestampMillis: Long = currentTimeMillis,
        deltaTimeSeconds: Double = 1.0,
        altitude: Double = 1_000.0,
        varioMs: Double = 0.4,
        baroResult: BarometricAltitudeData? = null,
        externalInstrumentSnapshot: ExternalInstrumentFlightSnapshot = ExternalInstrumentFlightSnapshot(),
        externalAirspeedSample: com.example.xcpro.weather.wind.model.AirspeedSample? = null,
        varioValidUntil: Long = currentTimeMillis + 1_000L,
        isReplayMode: Boolean = false
    ): FlightMetricsRequest = FlightMetricsRequest(
        gps = gpsSample(timeMs = gpsTimestampMillis, speedMs = 20.0),
        currentTimeMillis = currentTimeMillis,
        wallTimeMillis = currentTimeMillis,
        gpsTimestampMillis = gpsTimestampMillis,
        deltaTimeSeconds = deltaTimeSeconds,
        varioResult = varioSample(varioMs, altitude),
        varioGpsValue = varioMs,
        baroResult = baroResult,
        windState = null,
        externalAirspeedSample = externalAirspeedSample,
        externalInstrumentSnapshot = externalInstrumentSnapshot,
        allowOnlineTerrainLookup = !isReplayMode,
        varioValidUntil = varioValidUntil,
        isFlying = true,
        macCreadySetting = 0.0,
        autoMcEnabled = false,
        teCompensationEnabled = true,
        flightMode = FlightMode.CRUISE,
        isReplayMode = isReplayMode
    )

    private fun calibratedBaroResult(
        altitudeMeters: Double,
        pressureAltitudeMeters: Double,
        qnh: Double = 1013.25,
        confidenceLevel: ConfidenceLevel = ConfidenceLevel.MEDIUM
    ) = BarometricAltitudeData(
        altitudeMeters = altitudeMeters,
        qnh = qnh,
        isCalibrated = true,
        pressureHPa = qnh,
        temperatureCompensated = false,
        confidenceLevel = confidenceLevel,
        pressureAltitudeMeters = pressureAltitudeMeters,
        gpsDeltaMeters = null,
        lastCalibrationTime = 0L
    )
}
