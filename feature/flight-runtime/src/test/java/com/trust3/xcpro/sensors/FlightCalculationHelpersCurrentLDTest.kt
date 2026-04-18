package com.trust3.xcpro.sensors

import com.trust3.xcpro.common.geo.GeoPoint
import com.trust3.xcpro.common.units.AltitudeM
import com.trust3.xcpro.common.units.SpeedMs
import com.trust3.xcpro.core.flight.calculations.SimpleAglCalculator
import com.trust3.xcpro.core.flight.calculations.TerrainElevationReadPort
import com.trust3.xcpro.glider.SpeedBoundsMs
import com.trust3.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FlightCalculationHelpersCurrentLDTest {

    @Test
    fun currentLd_bootstrap_firstAcceptedSample_returnsZero() {
        val helpers = newHelpers()
        val gps = gpsSample(latitude = 0.0, timestamp = 5_000L)

        val result = helpers.calculateCurrentLD(gps, currentAltitude = 1_000.0, timestampMillis = 5_000L)

        assertEquals(0f, result, 1e-6f)
    }

    @Test
    fun currentLd_recomputeInterval_hold_returnsHeldValueBeforeFiveSeconds() {
        val helpers = newHelpers()
        val accepted = seedAcceptedLd(helpers, endLatitude = 0.00045, currentAltitude = 999.0)
        val heldGps = gpsSample(latitude = 0.0020, timestamp = 14_000L)

        helpers.recordLocationSample(heldGps, 14_000L)
        val held = helpers.calculateCurrentLD(heldGps, currentAltitude = 995.0, timestampMillis = 14_000L)

        assertTrue(accepted > 5f && accepted < 100f)
        assertEquals(accepted, held, 1e-6f)
    }

    @Test
    fun currentLd_distanceGate_hold_keepsHeldValueWhenMovementIsTooSmall() {
        val helpers = newHelpers()
        val accepted = seedAcceptedLd(helpers, endLatitude = 0.00045, currentAltitude = 999.0)

        listOf(0.00046, 0.00047, 0.00048, 0.00049).forEachIndexed { index, latitude ->
            val timestamp = 11_000L + (index * 1_000L)
            helpers.recordLocationSample(gpsSample(latitude = latitude, timestamp = timestamp), timestamp)
        }
        val gatedGps = gpsSample(latitude = 0.00050, timestamp = 15_000L)

        helpers.recordLocationSample(gatedGps, 15_000L)
        val held = helpers.calculateCurrentLD(gatedGps, currentAltitude = 998.0, timestampMillis = 15_000L)

        assertEquals(accepted, held, 1e-6f)
    }

    @Test
    fun currentLd_altitudeLossGate_hold_keepsHeldValueWhenLossIsTooSmall() {
        val helpers = newHelpers()
        val accepted = seedAcceptedLd(helpers, endLatitude = 0.00045, currentAltitude = 999.0)
        val gatedGps = gpsSample(latitude = 0.00090, timestamp = 15_000L)

        helpers.recordLocationSample(gatedGps, 15_000L)
        val held = helpers.calculateCurrentLD(gatedGps, currentAltitude = 998.7, timestampMillis = 15_000L)

        assertEquals(accepted, held, 1e-6f)
    }

    @Test
    fun currentLd_clampLow_returnsAndPersistsContractFloor() {
        val helpers = newHelpers()
        val fresh = seedAcceptedLd(helpers, endLatitude = 0.00018, currentAltitude = 990.0)
        val heldGps = gpsSample(latitude = 0.00030, timestamp = 14_000L)

        helpers.recordLocationSample(heldGps, 14_000L)
        val held = helpers.calculateCurrentLD(heldGps, currentAltitude = 985.0, timestampMillis = 14_000L)

        assertEquals(5f, fresh, 1e-6f)
        assertEquals(5f, held, 1e-6f)
    }

    @Test
    fun currentLd_clampHigh_returnsContractCeiling() {
        val helpers = newHelpers()

        val fresh = seedAcceptedLd(helpers, endLatitude = 0.00120, currentAltitude = 999.4)

        assertEquals(100f, fresh, 1e-6f)
    }

    @Test
    fun currentLd_heldAfterClamp_doesNotLeakUnclampedValue() {
        val helpers = newHelpers()
        val fresh = seedAcceptedLd(helpers, endLatitude = 0.00120, currentAltitude = 999.4)
        val heldGps = gpsSample(latitude = 0.0020, timestamp = 14_000L)

        helpers.recordLocationSample(heldGps, 14_000L)
        val held = helpers.calculateCurrentLD(heldGps, currentAltitude = 995.0, timestampMillis = 14_000L)

        assertEquals(100f, fresh, 1e-6f)
        assertEquals(100f, held, 1e-6f)
    }

    @Test
    fun currentLd_resetAll_clearsStateAndNextSampleBootstrapsAgain() {
        val helpers = newHelpers()

        val accepted = seedAcceptedLd(helpers, endLatitude = 0.00045, currentAltitude = 999.0)
        assertTrue(accepted > 0f)

        helpers.resetAll()

        val reboot = helpers.calculateCurrentLD(
            gpsSample(latitude = 0.0, timestamp = 20_000L),
            currentAltitude = 990.0,
            timestampMillis = 20_000L
        )

        assertEquals(0f, reboot, 1e-6f)
    }

    private fun seedAcceptedLd(
        helpers: FlightCalculationHelpers,
        endLatitude: Double,
        currentAltitude: Double
    ): Float {
        val startGps = gpsSample(latitude = 0.0, timestamp = 5_000L)
        val bootstrap = helpers.calculateCurrentLD(startGps, currentAltitude = 1_000.0, timestampMillis = 5_000L)
        assertEquals(0f, bootstrap, 1e-6f)

        helpers.recordLocationSample(startGps, 5_000L)
        val endGps = gpsSample(latitude = endLatitude, timestamp = 10_000L)
        helpers.recordLocationSample(endGps, 10_000L)
        return helpers.calculateCurrentLD(endGps, currentAltitude = currentAltitude, timestampMillis = 10_000L)
    }

    private fun newHelpers(): FlightCalculationHelpers =
        FlightCalculationHelpers(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            aglCalculator = noOpAglCalculator(),
            locationHistory = mutableListOf(),
            sinkProvider = noOpSinkProvider()
        )

    private fun gpsSample(
        latitude: Double,
        timestamp: Long,
        longitude: Double = 150.0,
        speedMs: Double = 25.0
    ): GPSData = GPSData(
        position = GeoPoint(latitude = latitude, longitude = longitude),
        altitude = AltitudeM(500.0),
        speed = SpeedMs(speedMs),
        bearing = 90.0,
        accuracy = 5f,
        timestamp = timestamp,
        monotonicTimestampMillis = timestamp
    )

    private fun noOpAglCalculator(): SimpleAglCalculator =
        SimpleAglCalculator(
            terrainElevationReadPort = object : TerrainElevationReadPort {
                override suspend fun getElevationMeters(lat: Double, lon: Double): Double? = null
            }
        )

    private fun noOpSinkProvider(): StillAirSinkProvider =
        object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
}
