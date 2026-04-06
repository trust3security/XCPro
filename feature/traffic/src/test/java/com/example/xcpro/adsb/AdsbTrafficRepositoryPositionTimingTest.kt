package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryPositionTimingTest : AdsbTrafficRepositoryTestBase() {

    @Test
    fun providerTimeNormalizesPositionAge_withoutDeviceWallClockSkew() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(
            monoMs = 0L,
            wallMs = 9_999_999_000_000L
        )
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_010L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0,
                                timePositionSec = 1_710_000_000L,
                                lastContactSec = 1_710_000_009L
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val target = repository.targets.value.single()
        assertEquals(10, target.ageSec)
        assertEquals(10, target.positionAgeSec)
        assertEquals(1, target.contactAgeSec)
        assertFalse(target.isStale)

        repository.stop()
    }

    @Test
    fun olderPositionWithFreshContact_keepsNewerDisplayedGeometry() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clock = FakeClock(monoMs = 0L, wallMs = 0L)
        val provider = SequenceProvider(
            listOf(
                successState(
                    timeSec = 1_710_000_012L,
                    latitude = -33.8688,
                    longitude = 151.2120,
                    timePositionSec = 1_710_000_010L,
                    lastContactSec = 1_710_000_011L
                ),
                successState(
                    timeSec = 1_710_000_040L,
                    latitude = -33.8688,
                    longitude = 151.2200,
                    timePositionSec = 1_710_000_000L,
                    lastContactSec = 1_710_000_039L
                )
            )
        )
        val repository = createAdsbTrafficRepository(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = clock,
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(151.2120, repository.targets.value.single().lon, 1e-6)

        clock.setMonoMs(31_000L)
        advanceTimeBy(31_000L)
        runCurrent()

        val target = repository.targets.value.single()
        assertEquals(151.2120, target.lon, 1e-6)
        assertTrue(target.positionAgeSec > 30)
        assertEquals(1, target.contactAgeSec)

        repository.stop()
    }
}

