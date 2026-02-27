package com.example.xcpro.sensors

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.PressureHpa
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.common.units.VerticalSpeedMs
import com.example.xcpro.weather.wind.data.AirspeedDataSource
import com.example.xcpro.weather.wind.model.AirspeedSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightStateRepositoryAglFreshnessTest {

    private class FakeSensorDataSource : SensorDataSource {
        override val gpsFlow = MutableStateFlow<GPSData?>(null)
        override val baroFlow = MutableStateFlow<BaroData?>(null)
        override val compassFlow = MutableStateFlow<CompassData?>(null)
        override val rawAccelFlow = MutableStateFlow<RawAccelData?>(null)
        override val accelFlow = MutableStateFlow<AccelData?>(null)
        override val attitudeFlow = MutableStateFlow<AttitudeData?>(null)
    }

    private class FakeAirspeedDataSource : AirspeedDataSource {
        override val airspeedFlow: StateFlow<AirspeedSample?> = MutableStateFlow(null)
    }

    @Test
    fun staleAglOverride_doesNotForceFlyingStateTrue() = runTest {
        val liveSensors = FakeSensorDataSource()
        val replaySensors = FakeSensorDataSource()
        val liveAirspeed = FakeAirspeedDataSource()
        val replayAirspeed = FakeAirspeedDataSource()
        val flightDataRepository = FlightDataRepository()
        val clock = FakeClock(monoMs = 50_000L, wallMs = 50_000L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FlightStateRepository(
            liveSensors = liveSensors,
            replaySensors = replaySensors,
            liveAirspeedSource = liveAirspeed,
            replayAirspeedSource = replayAirspeed,
            flightDataRepository = flightDataRepository,
            clock = clock,
            defaultDispatcher = dispatcher
        )

        val staleAglData = buildCompleteFlightDataForTest(gps = null).copy(
            agl = AltitudeM(320.0),
            aglTimestampMonoMs = 1_000L
        )
        flightDataRepository.update(staleAglData, FlightDataRepository.Source.LIVE)

        repeat(12) { index ->
            val timestampMs = (index + 1) * 1_000L
            clock.setMonoMs(50_000L + timestampMs)
            liveSensors.gpsFlow.value = gpsSample(
                timestampMillis = timestampMs,
                monotonicTimestampMillis = timestampMs,
                speedMs = 1.0
            )
            advanceUntilIdle()
        }

        assertFalse(repository.flightState.value.isFlying)
    }

    @Test
    fun freshAglOverride_allowsFlyingStateTakeoffWhenSpeedIsLow() = runTest {
        val liveSensors = FakeSensorDataSource()
        val replaySensors = FakeSensorDataSource()
        val liveAirspeed = FakeAirspeedDataSource()
        val replayAirspeed = FakeAirspeedDataSource()
        val flightDataRepository = FlightDataRepository()
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FlightStateRepository(
            liveSensors = liveSensors,
            replaySensors = replaySensors,
            liveAirspeedSource = liveAirspeed,
            replayAirspeedSource = replayAirspeed,
            flightDataRepository = flightDataRepository,
            clock = clock,
            defaultDispatcher = dispatcher
        )

        repeat(12) { index ->
            val timestampMs = (index + 1) * 1_000L
            clock.setMonoMs(timestampMs)
            flightDataRepository.update(
                buildCompleteFlightDataForTest(gps = null).copy(
                    agl = AltitudeM(320.0),
                    aglTimestampMonoMs = timestampMs
                ),
                FlightDataRepository.Source.LIVE
            )
            liveSensors.gpsFlow.value = gpsSample(
                timestampMillis = timestampMs,
                monotonicTimestampMillis = timestampMs,
                speedMs = 1.0
            )
            advanceUntilIdle()
        }

        assertTrue(repository.flightState.value.isFlying)
    }

    private fun gpsSample(
        timestampMillis: Long,
        monotonicTimestampMillis: Long,
        speedMs: Double
    ): GPSData = GPSData(
        position = GeoPoint(47.0, 13.0),
        altitude = AltitudeM(400.0),
        speed = SpeedMs(speedMs),
        bearing = 90.0,
        accuracy = 5f,
        timestamp = timestampMillis,
        monotonicTimestampMillis = monotonicTimestampMillis
    )

    private fun buildCompleteFlightDataForTest(
        gps: GPSData?,
        timestampMillis: Long = 1_000L
    ): CompleteFlightData = CompleteFlightData(
        gps = gps,
        baro = null,
        compass = null,
        baroAltitude = AltitudeM(1_000.0),
        qnh = PressureHpa(1013.25),
        isQNHCalibrated = false,
        verticalSpeed = VerticalSpeedMs(0.0),
        pressureAltitude = AltitudeM(0.0),
        baroGpsDelta = null,
        baroConfidence = ConfidenceLevel.LOW,
        qnhCalibrationAgeSeconds = -1,
        agl = AltitudeM(0.0),
        thermalAverage = VerticalSpeedMs(0.0),
        currentLD = 0f,
        netto = VerticalSpeedMs(0.0),
        timestamp = timestampMillis,
        dataQuality = "TEST"
    )
}
