package com.example.xcpro.weather.wind

import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.weather.wind.data.WindSensorFusionRepository
import com.example.xcpro.weather.wind.data.WindSensorInputs
import com.example.xcpro.weather.wind.model.AirspeedSample
import com.example.xcpro.weather.wind.model.GpsSample
import com.example.xcpro.weather.wind.model.HeadingSample
import com.example.xcpro.weather.wind.model.PressureSample
import com.example.xcpro.weather.wind.model.WindSource
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)

        generateCirclingSamples().forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis
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
    fun `repository publishes straight flight wind via ekf`() = runTest {
        val inputs = TestWindInputs()
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindSensorFusionRepository(
            liveInputs = inputs.inputs,
            replayInputs = inputs.inputs,
            flightDataRepository = flightRepo,
            dispatcher = dispatcher
        )
        flightRepo.setActiveSource(FlightDataRepository.Source.LIVE)

        generateStraightSamples().forEach { sample ->
            inputs.gps.value = sample
            inputs.heading.value = HeadingSample(
                headingDeg = Math.toDegrees(sample.trackRad),
                timestampMillis = sample.timestampMillis
            )
            inputs.airspeed.value = AirspeedSample(
                trueMs = TAS,
                indicatedMs = TAS,
                timestampMillis = sample.timestampMillis,
                valid = true
            )
            runCurrent()
        }

        val state = repo.windState.value
        assertTrue("Wind state should be available after straight-flight samples", state.isAvailable)
        assertEquals(WindSource.EKF, state.source)
        val vector = state.vector
        assertNotNull("Wind vector should not be null", vector)
        assertTrue("Wind vector magnitude should be non-zero", vector!!.speed > 0.5)
        assertTrue("EKF quality should be at least medium", state.quality >= 3)
    }

    private fun generateCirclingSamples(): List<GpsSample> {
        val result = mutableListOf<GpsSample>()
        var timestamp = 0L
        val steps = 4 * STEPS_PER_CIRCLE
        for (step in 0..steps) {
            val angle = (step % STEPS_PER_CIRCLE).toDouble() / STEPS_PER_CIRCLE * 2 * PI
            val gpsSpeedVector = airspeedVector(angle)
            val groundEast = gpsSpeedVector.first + TEST_WIND_EAST
            val groundNorth = gpsSpeedVector.second + TEST_WIND_NORTH
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
            timestampMillis = timestamp
        )

    private class TestWindInputs {
        val gps = MutableStateFlow<GpsSample?>(null)
        val pressure = MutableStateFlow<PressureSample?>(null)
        val airspeed = MutableStateFlow<AirspeedSample?>(null)
        val heading = MutableStateFlow<HeadingSample?>(null)

        val inputs = WindSensorInputs(
            gps = gps,
            pressure = pressure,
            airspeed = airspeed,
            heading = heading
        )
    }

    companion object {
        private const val STEPS_PER_CIRCLE = 36
        private const val TAS = 30.0
        private const val TEST_WIND_EAST = 5.0
        private const val TEST_WIND_NORTH = -2.0
    }
}
