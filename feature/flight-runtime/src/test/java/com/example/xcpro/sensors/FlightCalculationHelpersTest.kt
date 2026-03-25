package com.example.xcpro.sensors

import com.example.dfcards.dfcards.calculations.SimpleAglCalculator
import com.example.dfcards.dfcards.calculations.TerrainElevationReadPort
import com.example.xcpro.common.geo.GeoPoint
import com.example.xcpro.common.units.AltitudeM
import com.example.xcpro.common.units.SpeedMs
import com.example.xcpro.glider.SpeedBoundsMs
import com.example.xcpro.glider.StillAirSinkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FlightCalculationHelpersTest {
    companion object {
        private const val AGL_WORKER_TIMEOUT_SECONDS = 10L
    }

    @Test
    fun resetAll_clearsThermalTracking() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }

        val helpers = FlightCalculationHelpers(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            aglCalculator = noOpAglCalculator(),
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        helpers.updateThermalState(
            timestampMillis = 1_000L,
            teAltitudeMeters = 100.0,
            verticalSpeedMs = 0.0,
            isCircling = true,
            isTurning = true
        )
        helpers.updateThermalState(
            timestampMillis = 2_000L,
            teAltitudeMeters = 110.0,
            verticalSpeedMs = 10.0,
            isCircling = true,
            isTurning = true
        )

        assertTrue(helpers.currentThermalValid)
        assertEquals(10.0f, helpers.thermalAverageCurrent, 1e-6f)

        helpers.resetAll()

        assertFalse(helpers.currentThermalValid)
        assertEquals(0.0f, helpers.thermalAverageCurrent, 1e-6f)
    }

    @Test
    fun netto_validity_requires_warmup() {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = -0.5
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }

        val helpers = FlightCalculationHelpers(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            aglCalculator = noOpAglCalculator(),
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        val first = helpers.calculateNetto(
            currentVerticalSpeed = 1.0,
            indicatedAirspeed = 10.0,
            fallbackGroundSpeed = 10.0,
            timestampMillis = 0L
        )
        assertFalse(first.valid)

        val beforeWarmup = helpers.calculateNetto(
            currentVerticalSpeed = 1.0,
            indicatedAirspeed = 10.0,
            fallbackGroundSpeed = 10.0,
            timestampMillis = 19_999L
        )
        assertFalse(beforeWarmup.valid)

        val afterWarmup = helpers.calculateNetto(
            currentVerticalSpeed = 1.0,
            indicatedAirspeed = 10.0,
            fallbackGroundSpeed = 10.0,
            timestampMillis = 20_000L
        )
        assertTrue(afterWarmup.valid)
    }

    @Test
    fun updateAgl_burstRequests_coalescesToLatestPendingSample() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val workerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val aglCalculator = mock<SimpleAglCalculator>()
        val firstRequestEntered = CountDownLatch(1)
        val releaseFirstRequest = CountDownLatch(1)
        val secondRequestProcessed = CountDownLatch(1)
        val requestedAltitudes = Collections.synchronizedList(mutableListOf<Double>())
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val altitude = invocation.getArgument<Double>(0)
            requestedAltitudes += altitude
            if (requestedAltitudes.size == 1) {
                firstRequestEntered.countDown()
                check(releaseFirstRequest.await(AGL_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    "Timed out waiting to release first AGL request"
                }
            } else if (requestedAltitudes.size == 2) {
                secondRequestProcessed.countDown()
            }
            altitude - 100.0
        }
        val helpers = FlightCalculationHelpers(
            scope = CoroutineScope(workerDispatcher + SupervisorJob()),
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        try {
            helpers.updateAGL(baroAltitude = 1_000.0, gps = gpsSample(latitude = 1.0), speed = 25.0)
            assertTrue(
                "Timed out waiting for first AGL request to enter the calculator.",
                firstRequestEntered.await(AGL_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )

            helpers.updateAGL(baroAltitude = 1_100.0, gps = gpsSample(latitude = 2.0), speed = 25.0)
            helpers.updateAGL(baroAltitude = 1_200.0, gps = gpsSample(latitude = 3.0), speed = 25.0)

            releaseFirstRequest.countDown()
            assertTrue(
                "Timed out waiting for the coalesced second AGL request to finish.",
                secondRequestProcessed.await(AGL_WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
            waitForCondition(
                timeoutSeconds = AGL_WORKER_TIMEOUT_SECONDS,
                failureMessage = "Timed out waiting for the coalesced AGL result to be applied."
            ) {
                helpers.currentAGL == 1_100.0
            }

            val requestedSnapshot = synchronized(requestedAltitudes) { requestedAltitudes.toList() }
            assertEquals(listOf(1_000.0, 1_200.0), requestedSnapshot)
            assertEquals(1_100.0, helpers.currentAGL, 1e-6)
            val metrics = helpers.getAglWorkerMetrics()
            assertEquals(2L, metrics.processedUpdates)
            assertEquals(1L, metrics.droppedUpdates)
            assertEquals(0L, metrics.errorUpdates)
        } finally {
            workerDispatcher.close()
        }
    }

    @Test
    fun updateAgl_afterQueueDrains_processesNewRequest() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val aglCalculator = mock<SimpleAglCalculator>()
        val requestedAltitudes = mutableListOf<Double>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val altitude = invocation.getArgument<Double>(0)
            requestedAltitudes += altitude
            altitude + 1.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        helpers.updateAGL(baroAltitude = 900.0, gps = gpsSample(latitude = 10.0), speed = 20.0)
        advanceUntilIdle()
        assertEquals(901.0, helpers.currentAGL, 1e-6)

        helpers.updateAGL(baroAltitude = 950.0, gps = gpsSample(latitude = 11.0), speed = 20.0)
        advanceUntilIdle()

        assertEquals(listOf(900.0, 950.0), requestedAltitudes)
        assertEquals(951.0, helpers.currentAGL, 1e-6)
        val metrics = helpers.getAglWorkerMetrics()
        assertEquals(2L, metrics.processedUpdates)
        assertEquals(0L, metrics.droppedUpdates)
        assertEquals(0L, metrics.errorUpdates)
    }

    @Test
    fun updateAgl_calculatorThrows_workerRecoversForSubsequentUpdates() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val aglCalculator = mock<SimpleAglCalculator>()
        var callCount = 0
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            callCount += 1
            if (callCount == 1) error("simulated AGL failure")
            val altitude = invocation.getArgument<Double>(0)
            altitude - 50.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        helpers.updateAGL(baroAltitude = 800.0, gps = gpsSample(latitude = 20.0), speed = 15.0)
        advanceUntilIdle()
        assertTrue(helpers.currentAGL.isNaN())

        helpers.updateAGL(baroAltitude = 900.0, gps = gpsSample(latitude = 21.0), speed = 15.0)
        advanceUntilIdle()

        assertEquals(850.0, helpers.currentAGL, 1e-6)
        val metrics = helpers.getAglWorkerMetrics()
        assertEquals(2L, metrics.processedUpdates)
        assertEquals(0L, metrics.droppedUpdates)
        assertEquals(1L, metrics.errorUpdates)
    }

    @Test
    fun updateAgl_stressBurst5000_latestWinsAndWorkerDrains() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val aglCalculator = mock<SimpleAglCalculator>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val altitude = invocation.getArgument<Double>(0)
            altitude - 10.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider
        )

        val totalUpdates = 5_000
        repeat(totalUpdates) { index ->
            val altitude = 1_000.0 + index
            helpers.updateAGL(baroAltitude = altitude, gps = gpsSample(latitude = 30.0), speed = 30.0)
        }
        advanceUntilIdle()

        assertEquals(1_000.0 + (totalUpdates - 1) - 10.0, helpers.currentAGL, 1e-6)
        val metrics = helpers.getAglWorkerMetrics()
        assertTrue(metrics.processedUpdates in 1L..totalUpdates.toLong())
        assertTrue(metrics.droppedUpdates > 0L)
        assertEquals(0L, metrics.errorUpdates)
        assertFalse(metrics.workerActive)
        assertFalse(metrics.hasPendingUpdate)
    }

    @Test
    fun updateAgl_submissionCadence_respectsBaseIntervalWithoutTriggers() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val nowMonoMs = AtomicLong(0L)
        val aglCalculator = mock<SimpleAglCalculator>()
        val requestedAltitudes = mutableListOf<Double>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val altitude = invocation.getArgument<Double>(0)
            requestedAltitudes += altitude
            altitude - 10.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider,
            nowMonoMsProvider = { nowMonoMs.get() }
        )
        val gps = gpsSample(latitude = 40.0)

        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 30.0)
        advanceUntilIdle()

        nowMonoMs.set(19_999L)
        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 30.0)
        advanceUntilIdle()

        nowMonoMs.set(20_000L)
        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 30.0)
        advanceUntilIdle()

        assertEquals(listOf(1_000.0, 1_000.0), requestedAltitudes)
    }

    @Test
    fun updateAgl_movementTrigger_bypassesBaseCadence() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val nowMonoMs = AtomicLong(0L)
        val aglCalculator = mock<SimpleAglCalculator>()
        val requestedLatitudes = mutableListOf<Double>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val lat = invocation.getArgument<Double>(1)
            requestedLatitudes += lat
            invocation.getArgument<Double>(0) - 10.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider,
            nowMonoMsProvider = { nowMonoMs.get() }
        )

        helpers.updateAGL(baroAltitude = 1_000.0, gps = gpsSample(latitude = 50.000), speed = 30.0)
        advanceUntilIdle()

        nowMonoMs.set(1_000L)
        helpers.updateAGL(baroAltitude = 1_000.0, gps = gpsSample(latitude = 50.003), speed = 30.0)
        advanceUntilIdle()

        assertEquals(listOf(50.000, 50.003), requestedLatitudes)
    }

    @Test
    fun updateAgl_altitudeTrigger_bypassesBaseCadence() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val nowMonoMs = AtomicLong(0L)
        val aglCalculator = mock<SimpleAglCalculator>()
        val requestedAltitudes = mutableListOf<Double>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val altitude = invocation.getArgument<Double>(0)
            requestedAltitudes += altitude
            altitude - 10.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider,
            nowMonoMsProvider = { nowMonoMs.get() }
        )
        val gps = gpsSample(latitude = 60.0)

        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 30.0)
        advanceUntilIdle()

        nowMonoMs.set(1_000L)
        helpers.updateAGL(baroAltitude = 1_026.0, gps = gps, speed = 30.0)
        advanceUntilIdle()

        assertEquals(listOf(1_000.0, 1_026.0), requestedAltitudes)
    }

    @Test
    fun updateAgl_speedTransitionTrigger_bypassesBaseCadence() = runTest {
        val sinkProvider = object : StillAirSinkProvider {
            override fun sinkAtSpeed(airspeedMs: Double): Double? = null
            override fun iasBoundsMs(): SpeedBoundsMs? = null
        }
        val nowMonoMs = AtomicLong(0L)
        val aglCalculator = mock<SimpleAglCalculator>()
        val requestedSpeeds = mutableListOf<Double>()
        whenever(aglCalculator.calculateAgl(any(), any(), any(), anyOrNull())).thenAnswer { invocation ->
            val speed = invocation.getArgument<Double>(3)
            requestedSpeeds += speed
            invocation.getArgument<Double>(0) - 10.0
        }
        val helpers = FlightCalculationHelpers(
            scope = this,
            aglCalculator = aglCalculator,
            locationHistory = mutableListOf(),
            sinkProvider = sinkProvider,
            nowMonoMsProvider = { nowMonoMs.get() }
        )
        val gps = gpsSample(latitude = 70.0)

        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 1.0)
        advanceUntilIdle()

        nowMonoMs.set(1_000L)
        helpers.updateAGL(baroAltitude = 1_000.0, gps = gps, speed = 3.0)
        advanceUntilIdle()

        assertEquals(listOf(1.0, 3.0), requestedSpeeds)
    }

    private fun gpsSample(latitude: Double): GPSData =
        GPSData(
            position = GeoPoint(latitude = latitude, longitude = 150.0),
            altitude = AltitudeM(500.0),
            speed = SpeedMs(25.0),
            bearing = 90.0,
            accuracy = 5f,
            timestamp = 1_000L,
            monotonicTimestampMillis = 1_000L
        )

    private fun noOpAglCalculator(): SimpleAglCalculator =
        SimpleAglCalculator(
            terrainElevationReadPort = object : TerrainElevationReadPort {
                override suspend fun getElevationMeters(lat: Double, lon: Double): Double? = null
            }
        )

    private fun waitForCondition(
        timeoutSeconds: Long,
        failureMessage: String,
        condition: () -> Boolean
    ) {
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadlineNanos) {
            if (condition()) return
            Thread.sleep(10L)
        }
        throw AssertionError(failureMessage)
    }
}

