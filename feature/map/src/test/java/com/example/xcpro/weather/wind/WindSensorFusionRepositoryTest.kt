package com.example.xcpro.weather.wind

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.core.time.FakeClock
import com.example.xcpro.sensors.CirclingDetector
import com.example.xcpro.sensors.FlightStateSource
import com.example.xcpro.weather.wind.data.WindOverrideSource
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.data.WindSensorInputs
import com.example.xcpro.weather.wind.domain.CirclingWind
import com.example.xcpro.weather.wind.domain.CirclingWindResult
import com.example.xcpro.weather.wind.domain.CirclingWindSample
import com.example.xcpro.weather.wind.domain.WindStore
import com.example.xcpro.weather.wind.domain.WindSelectionUseCase
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GLoadSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindOverride
import com.example.xcpro.weather.wind.model.WindSource
import com.example.xcpro.weather.wind.model.WindVector
import com.example.xcpro.sensors.domain.FlyingState
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WindSensorFusionRepositoryTest {

    @Test
    fun `repository publishes circling wind`() = runTest {
        val inputs = TestWindInputs()
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindSensorFusionRepository(
            liveInputs = inputs.inputs,
            replayInputs = inputs.inputs,
            flightDataRepository = flightRepo,
            flightStateSource = FakeFlightStateSource(),
            windOverrideSource = FakeWindOverrideSource(),
            windSelectionUseCase = WindSelectionUseCase(),
            clock = FakeClock(),
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)

        generateCirclingSamples().forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis
            )
            runCurrent()
        }

        val state = repo.windState.value
        assertTrue("Wind state should be available after circling samples", state.isAvailable)
        assertEquals(WindSource.CIRCLING, state.source)
        val vector = state.vector
        assertNotNull("Wind vector should not be null", vector)
        assertTrue("Wind speed should be significant", vector!!.speed > 4.0)
    }

    @Test
    fun `repository does not publish straight flight wind without circling`() = runTest {
        val inputs = TestWindInputs()
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindSensorFusionRepository(
            liveInputs = inputs.inputs,
            replayInputs = inputs.inputs,
            flightDataRepository = flightRepo,
            flightStateSource = FakeFlightStateSource(),
            windOverrideSource = FakeWindOverrideSource(),
            windSelectionUseCase = WindSelectionUseCase(),
            clock = FakeClock(),
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)

        generateStraightSamples().forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis
            )
            inputs.airspeed.value = AirspeedSample(
                trueMs = TAS,
                indicatedMs = TAS,
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis,
                valid = true
            )
            runCurrent()
        }

        val state = repo.windState.value
        assertTrue("Wind state should not be available without circling", state.vector == null)
    }

    @Test
    fun `repository blends multiple circling measurements`() = runTest {
        val inputs = TestWindInputs()
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindSensorFusionRepository(
            liveInputs = inputs.inputs,
            replayInputs = inputs.inputs,
            flightDataRepository = flightRepo,
            flightStateSource = FakeFlightStateSource(),
            windOverrideSource = FakeWindOverrideSource(),
            windSelectionUseCase = WindSelectionUseCase(),
            clock = FakeClock(),
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)

        val wind1 = WindVector(east = 6.0, north = -2.0)
        val wind2 = WindVector(east = -4.0, north = 8.0)

        val firstSamples = generateCirclingSamples(
            windEast = wind1.east,
            windNorth = wind1.north,
            startTimestamp = 0L
        )
        val secondSamples = generateCirclingSamples(
            windEast = wind2.east,
            windNorth = wind2.north,
            startTimestamp = firstSamples.last().timestampMillis + 1000L
        )

        firstSamples.forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis
            )
            runCurrent()
        }

        secondSamples.forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis
            )
            runCurrent()
        }

        val state = repo.windState.value
        assertTrue("Wind state should be available after multiple circles", state.isAvailable)
        assertEquals(WindSource.CIRCLING, state.source)
        val vector = requireNotNull(state.vector)
        val expected = computeExpectedBlend(
            samples = firstSamples + secondSamples,
            altitudeMeters = 1000.0
        )

        assertEquals(expected.east, vector.east, 0.5)
        assertEquals(expected.north, vector.north, 0.5)
    }

    @Test
    fun `replay to live hold respects monotonic clock`() = runTest {
        val inputs = TestWindInputs()
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 1_000L, wallMs = 5_000L)
        val repo = WindSensorFusionRepository(
            liveInputs = inputs.inputs,
            replayInputs = inputs.inputs,
            flightDataRepository = flightRepo,
            flightStateSource = FakeFlightStateSource(),
            windOverrideSource = FakeWindOverrideSource(),
            windSelectionUseCase = WindSelectionUseCase(),
            clock = clock,
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.REPLAY)

        generateCirclingSamples().forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis,
                clockMillis = sample.clockMillis
            )
            runCurrent()
        }

        val stateBefore = repo.windState.value
        assertTrue(stateBefore.vector != null)

        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)
        runCurrent()

        inputs.gps.value = null
        runCurrent()
        assertEquals(stateBefore.vector, repo.windState.value.vector)

        clock.advanceMonoMs(REPLAY_HOLD_MS)
        advanceTimeBy(REPLAY_HOLD_MS)
        runCurrent()
        assertTrue(repo.windState.value.vector == null)
    }

    private fun generateCirclingSamples(
        windEast: Double = TEST_WIND_EAST,
        windNorth: Double = TEST_WIND_NORTH,
        startTimestamp: Long = 0L
    ): List<GpsSample> {
        val result = mutableListOf<GpsSample>()
        var timestamp = startTimestamp
        val steps = 4 * STEPS_PER_CIRCLE
        for (step in 0..steps) {
            val angle = (step % STEPS_PER_CIRCLE).toDouble() / STEPS_PER_CIRCLE * 2 * PI
            val gpsSpeedVector = airspeedVector(angle)
            val groundEast = gpsSpeedVector.first + windEast
            val groundNorth = gpsSpeedVector.second + windNorth
            val groundSpeed = hypot(groundEast, groundNorth)
            val trackRad = atan2(groundEast, groundNorth)
            result += gpsSample(timestamp, trackRad, groundSpeed)
            timestamp += 500
        }
        return result
    }

    private fun generateStraightSamples(): List<GpsSample> {
        val result = mutableListOf<GpsSample>()
        var timestamp = 0L
        repeat(2000) { index ->
            val headingDeg = 5.0 + 90.0 * sin(index / 200.0)
            val headingRad = Math.toRadians(headingDeg)
            val airEast = TAS * sin(headingRad)
            val airNorth = TAS * cos(headingRad)
            val groundEast = airEast + TEST_WIND_EAST
            val groundNorth = airNorth + TEST_WIND_NORTH
            val groundSpeed = hypot(groundEast, groundNorth)
            val groundTrackRad = atan2(groundEast, groundNorth)
            result += gpsSample(timestamp, groundTrackRad, groundSpeed)
            timestamp += 500
        }
        return result
    }

    private fun airspeedVector(trackRad: Double): Pair<Double, Double> {
        val east = TAS * sin(trackRad)
        val north = TAS * cos(trackRad)
        return east to north
    }

    private fun gpsSample(timestamp: Long, trackRad: Double, groundSpeed: Double): GpsSample =
        GpsSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = 1000.0,
            groundSpeedMs = groundSpeed,
            trackRad = trackRad,
            timestampMillis = timestamp,
            clockMillis = timestamp
        )

    private fun computeCirclingWind(samples: List<GpsSample>): WindVector {
        val circlingWind = CirclingWind()
        var result: CirclingWindResult? = null
        samples.forEach { sample ->
            result = circlingWind.addSample(
                CirclingWindSample(
                    clockMillis = sample.clockMillis,
                    trackRad = sample.trackRad,
                    groundSpeed = sample.groundSpeedMs,
                    isCircling = true
                )
            ) ?: result
        }
        return requireNotNull(result).windVector
    }

    private fun computeExpectedBlend(samples: List<GpsSample>, altitudeMeters: Double): WindVector {
        val detector = CirclingDetector()
        val circlingWind = CirclingWind()
        val store = WindStore()
        var lastEvaluationClock = Long.MIN_VALUE

        samples.forEach { sample ->
            val decision = detector.update(
                trackDegrees = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.clockMillis,
                isFlying = true
            )
            val result = circlingWind.addSample(
                CirclingWindSample(
                    clockMillis = sample.clockMillis,
                    trackRad = sample.trackRad,
                    groundSpeed = sample.groundSpeedMs,
                    isCircling = decision.isCircling
                )
            )
            if (result != null) {
                store.slotMeasurement(
                    clockMillis = result.clockMillis,
                    timestampMillis = sample.timestampMillis,
                    altitudeMeters = altitudeMeters,
                    vector = result.windVector,
                    quality = result.quality,
                    source = WindSource.CIRCLING
                )
                lastEvaluationClock = sample.clockMillis
            }
        }

        val evaluated = store.evaluate(lastEvaluationClock, altitudeMeters)
        return requireNotNull(evaluated).vector
    }

    private class TestWindInputs {
        val gps = MutableStateFlow<GpsSample?>(null)
        val pressure = MutableStateFlow<PressureSample?>(null)
        val airspeed = MutableStateFlow<AirspeedSample?>(null)
        val heading = MutableStateFlow<HeadingSample?>(null)
        val gLoad = MutableStateFlow<GLoadSample?>(null)

        val inputs = WindSensorInputs(
            gps = gps,
            pressure = pressure,
            airspeed = airspeed,
            heading = heading,
            gLoad = gLoad
        )
    }

    private class FakeWindOverrideSource : WindOverrideSource {
        override val manualWind = MutableStateFlow<WindOverride?>(null)
        override val externalWind = MutableStateFlow<WindOverride?>(null)
    }

    private class FakeFlightStateSource : FlightStateSource {
        override val flightState = MutableStateFlow(FlyingState(isFlying = true, onGround = false))
    }

    companion object {
        private const val STEPS_PER_CIRCLE = 36
        private const val TAS = 30.0
        private const val TEST_WIND_EAST = 5.0
        private const val TEST_WIND_NORTH = -2.0
        private const val REPLAY_HOLD_MS = 10_000L
    }
}
