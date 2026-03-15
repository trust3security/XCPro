package com.example.xcpro.weather.wind.data

import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.sensors.domain.FlyingState
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindOverride
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WindSensorFusionRepositoryTest {

    @Test
    fun nonFiniteGpsSample_advances_existingWindToStaleState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gpsFlow = MutableStateFlow<GpsSample?>(null)
        val pressureFlow = MutableStateFlow<PressureSample?>(null)
        val airspeedFlow = MutableStateFlow<AirspeedSample?>(null)
        val headingFlow = MutableStateFlow<HeadingSample?>(null)
        val gLoadFlow = MutableStateFlow<GLoadSample?>(null)
        val inputs = WindSensorInputs(
            gps = gpsFlow,
            pressure = pressureFlow,
            airspeed = airspeedFlow,
            heading = headingFlow,
            gLoad = gLoadFlow
        )

        val flightDataRepository = FlightDataRepository()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState(isFlying = true))
        }
        val windOverrideSource = object : WindOverrideSource {
            override val manualWind = MutableStateFlow<WindOverride?>(null)
            override val externalWind = MutableStateFlow<WindOverride?>(null)
        }

        val repository = WindSensorFusionRepository(
            liveInputs = inputs,
            replayInputs = inputs,
            flightDataRepository = flightDataRepository,
            flightStateSource = flightStateSource,
            windOverrideSource = windOverrideSource,
            windSelectionUseCase = WindSelectionUseCase(),
            clock = FakeClock(),
            dispatcher = dispatcher
        )

        val startTime = 1_000L
        gpsFlow.value = GpsSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = 1000.0,
            groundSpeedMs = 22.0,
            trackRad = 0.0,
            timestampMillis = startTime,
            clockMillis = startTime
        )
        windOverrideSource.manualWind.value = WindOverride(
            vector = WindVector(east = 4.0, north = 1.0),
            timestampMillis = startTime,
            source = WindSource.MANUAL
        )
        advanceUntilIdle()

        val seeded = repository.windState.value
        assertTrue(seeded.isAvailable)
        assertNotNull(seeded.vector)

        // One hour + a little later, but with an invalid track; stale handling must still progress.
        val staleTime = startTime + 3_600_000L + 1_000L
        gpsFlow.value = GpsSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = 1000.0,
            groundSpeedMs = 22.0,
            trackRad = Double.NaN,
            timestampMillis = staleTime,
            clockMillis = staleTime
        )
        advanceUntilIdle()

        val updated = repository.windState.value
        assertTrue(updated.stale)
        assertFalse(updated.isAvailable)
        assertTrue(updated.vector == null)
    }

    @Test
    fun confidence_decays_with_time_since_last_circle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gpsFlow = MutableStateFlow<GpsSample?>(null)
        val pressureFlow = MutableStateFlow<PressureSample?>(null)
        val airspeedFlow = MutableStateFlow<AirspeedSample?>(null)
        val headingFlow = MutableStateFlow<HeadingSample?>(null)
        val gLoadFlow = MutableStateFlow<GLoadSample?>(null)
        val inputs = WindSensorInputs(
            gps = gpsFlow,
            pressure = pressureFlow,
            airspeed = airspeedFlow,
            heading = headingFlow,
            gLoad = gLoadFlow
        )

        val flightDataRepository = FlightDataRepository()
        val flightStateSource = object : FlightStateSource {
            override val flightState = MutableStateFlow(FlyingState(isFlying = true))
        }
        val windOverrideSource = object : WindOverrideSource {
            override val manualWind = MutableStateFlow<WindOverride?>(null)
            override val externalWind = MutableStateFlow<WindOverride?>(null)
        }

        val repository = WindSensorFusionRepository(
            liveInputs = inputs,
            replayInputs = inputs,
            flightDataRepository = flightDataRepository,
            flightStateSource = flightStateSource,
            windOverrideSource = windOverrideSource,
            windSelectionUseCase = WindSelectionUseCase(),
            clock = FakeClock(),
            dispatcher = dispatcher
        )

        var timeMs = 0L
        var trackDeg = 0.0
        repeat(80) {
            gpsFlow.value = GpsSample(
                latitude = 0.0,
                longitude = 0.0,
                altitudeMeters = 1000.0,
                groundSpeedMs = 25.0,
                trackRad = Math.toRadians(trackDeg),
                timestampMillis = timeMs,
                clockMillis = timeMs
            )
            advanceUntilIdle()
            timeMs += 1_000L
            trackDeg = (trackDeg + 10.0) % 360.0
        }

        val baseConfidence = repository.windState.value.confidence
        assertTrue("expected base confidence > 0", baseConfidence > 0.0)

        val lastCircleTime = repository.windState.value.lastCirclingClockMillis
        val laterTime = lastCircleTime + 7L * 60L * 1000L
        gpsFlow.value = GpsSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = 1000.0,
            groundSpeedMs = 25.0,
            trackRad = 0.0,
            timestampMillis = laterTime,
            clockMillis = laterTime
        )
        advanceUntilIdle()

        val decayed = repository.windState.value.confidence
        val ratio = if (baseConfidence > 0.0) decayed / baseConfidence else 0.0
        assertTrue("expected ~0.5 decay, got $ratio", abs(ratio - 0.5) < 0.08)
    }
}
