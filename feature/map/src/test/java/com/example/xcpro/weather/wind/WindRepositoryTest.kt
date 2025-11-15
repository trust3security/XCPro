package com.example.xcpro.weather.wind

import com.example.dfcards.calculations.ConfidenceLevel
import com.example.xcpro.flightdata.FlightDataRepository
import com.example.xcpro.sensors.BaroData
import com.example.xcpro.sensors.CompleteFlightData
import com.example.xcpro.sensors.CompassData
import com.example.xcpro.sensors.GPSData
import com.example.xcpro.weather.wind.data.WindRepository
import com.example.xcpro.weather.wind.model.WindSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class WindRepositoryTest {

    @Test
    fun `repository publishes circling wind`() = runTest {
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindRepository(flightRepo, dispatcher)

        generateFlightDataSamples().forEach { sample ->
            repo.processSampleForTest(sample)
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
        val flightRepo = FlightDataRepository()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = WindRepository(flightRepo, dispatcher)

        generateStraightFlightSamples().forEach { sample ->
            repo.processSampleForTest(sample)
        }

        val state = repo.windState.value
        assertTrue("Wind state should be available after straight-flight samples", state.isAvailable)
        assertEquals(WindSource.EKF, state.source)
        val vector = state.vector
        assertNotNull("Wind vector should not be null", vector)
        requireNotNull(vector)
        assertTrue("Wind vector magnitude should be non-zero", vector.speed > 0.5)
        assertTrue("EKF quality should be at least medium", state.quality >= 3)
    }

    private fun generateFlightDataSamples(): List<CompleteFlightData> {
        val result = mutableListOf<CompleteFlightData>()
        var timestamp = 0L
        val steps = 4 * STEPS_PER_CIRCLE
        for (step in 0..steps) {
            val angle = (step % STEPS_PER_CIRCLE).toDouble() / STEPS_PER_CIRCLE * 2 * PI
            val gpsSpeedVector = airspeedVector(angle)
            val groundEast = gpsSpeedVector.first + TEST_WIND_EAST
            val groundNorth = gpsSpeedVector.second + TEST_WIND_NORTH
            val groundSpeed = hypot(groundEast, groundNorth)
            val trackRad = atan2(groundEast, groundNorth)
            val trackDeg = Math.toDegrees(trackRad).toFloat()
            result += sampleFlightData(timestamp, trackDeg, groundSpeed)
            timestamp += 500
        }
        return result
    }

    private fun generateStraightFlightSamples(): List<CompleteFlightData> {
        val result = mutableListOf<CompleteFlightData>()
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
            var groundTrackDeg = Math.toDegrees(groundTrackRad)
            if (groundTrackDeg < 0) {
                groundTrackDeg += 360.0
            }
            result += sampleFlightData(timestamp, groundTrackDeg.toFloat(), groundSpeed)
            timestamp += 500
        }
        return result
    }

    private fun airspeedVector(trackRad: Double): Pair<Double, Double> {
        val east = TAS * sin(trackRad)
        val north = TAS * cos(trackRad)
        return east to north
    }

    private fun sampleFlightData(timestamp: Long, trackDeg: Float, groundSpeed: Double): CompleteFlightData {
        val gps = GPSData(
            latLng = LatLng(0.0, 0.0),
            altitude = 1000.0,
            speed = groundSpeed,
            bearing = trackDeg.toDouble(),
            accuracy = 5f,
            timestamp = timestamp
        )
        val baro = BaroData(pressureHPa = 1013.25, timestamp = timestamp)
        val compass = CompassData(
            heading = trackDeg.toDouble(),
            accuracy = 3,
            timestamp = timestamp
        )
        return CompleteFlightData(
            gps = gps,
            baro = baro,
            compass = compass,
            baroAltitude = 1000.0,
            qnh = 1013.25,
            isQNHCalibrated = true,
            verticalSpeed = 0.0,
            pressureAltitude = 1000.0,
            baroGpsDelta = 0.0,
            baroConfidence = ConfidenceLevel.MEDIUM,
            qnhCalibrationAgeSeconds = 0,
            agl = 500.0,
            windSpeed = 0f,
            windDirection = 0f,
            windHeadwind = 0.0,
            windCrosswind = 0.0,
            windQuality = 0,
            windSource = WindSource.NONE,
            thermalAverage = 0f,
            currentLD = 0f,
            netto = 0f,
            trueAirspeed = TAS,
            indicatedAirspeed = TAS,
            airspeedSource = "TEST",
            varioOptimized = 0.0,
            varioLegacy = 0.0,
            varioRaw = 0.0,
            varioGPS = 0.0,
            varioComplementary = 0.0,
            timestamp = timestamp,
            dataQuality = "TEST"
        )
    }

    companion object {
        private const val STEPS_PER_CIRCLE = 36
        private const val TAS = 30.0
        private const val TEST_WIND_EAST = 5.0
        private const val TEST_WIND_NORTH = -2.0
    }
}

private val processSampleMethod = WindRepository::class.java
    .getDeclaredMethod("processSample", CompleteFlightData::class.java)
    .apply { isAccessible = true }

private fun WindRepository.processSampleForTest(sample: CompleteFlightData) {
    processSampleMethod.invoke(this, sample)
}


