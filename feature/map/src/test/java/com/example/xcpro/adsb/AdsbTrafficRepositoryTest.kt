package com.example.xcpro.adsb

import com.example.xcpro.core.time.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdsbTrafficRepositoryTest {

    @Test
    fun rateLimitedResponse_entersBackingOffAndRecovers() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.RateLimited(retryAfterSec = 120, remainingCredits = 9),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 8
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        val backingOff = repository.snapshot.value.connectionState
        assertTrue(backingOff is AdsbConnectionState.BackingOff)
        assertEquals(120, (backingOff as AdsbConnectionState.BackingOff).retryAfterSec)

        advanceTimeBy(120_000L)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun transientNetworkErrors_applyBackoffThenRecover() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.NetworkError("Timeout"),
                ProviderResult.NetworkError("Timeout"),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Error)

        advanceTimeBy(70_000L)
        runCurrent()

        assertTrue(provider.callCount >= 3)
        assertTrue(repository.snapshot.value.connectionState is AdsbConnectionState.Active)
        repository.stop()
    }

    @Test
    fun successResponse_keepsOnlyHighConfidenceAirborneTargets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "a1b2c3",
                                latitude = -33.8688,
                                longitude = 151.2093,
                                altitudeM = 30.48,
                                speedMps = 30.0
                            ),
                            state(
                                icao24 = "d4e5f6",
                                latitude = -33.8687,
                                longitude = 151.2094,
                                altitudeM = 300.0,
                                speedMps = 20.5778
                            ),
                            state(
                                icao24 = "abc123",
                                latitude = -33.8686,
                                longitude = 151.2095,
                                altitudeM = 300.0,
                                speedMps = 45.0
                            ),
                            state(
                                icao24 = "123abc",
                                latitude = -33.8685,
                                longitude = 151.2096,
                                altitudeM = 300.0,
                                speedMps = 45.0,
                                positionSource = 3
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = 12
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()

        assertEquals(1, repository.targets.value.size)
        assertEquals("abc123", repository.targets.value.first().id.raw)
        repository.stop()
    }

    @Test
    fun lowRemainingCredits_emptyScene_appliesBudgetFloorDelay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 40
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 39
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(59_000L)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(2_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun nearbyTraffic_prioritizesFastPollingEvenWithLowCredits() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = 40
                ),
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_010L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = 39
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, provider.callCount)

        advanceTimeBy(11_000L)
        runCurrent()
        assertTrue(provider.callCount >= 2)
        repository.stop()
    }

    @Test
    fun enableBeforeCenter_fetchesSoonAfterCenterIsProvided() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = 1_710_000_000L, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.setEnabled(true)
        runCurrent()
        assertEquals(0, provider.callCount)

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        advanceTimeBy(150L)
        runCurrent()

        assertTrue(provider.callCount >= 1)
        repository.stop()
    }

    @Test
    fun disableStreaming_preservesTargets_untilExplicitClear() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.setEnabled(false)
        runCurrent()
        assertEquals(1, repository.targets.value.size)

        repository.clearTargets()
        runCurrent()
        assertTrue(repository.targets.value.isEmpty())
    }

    @Test
    fun centerUpdate_reselectsCachedTargetsWithoutWaitingForNextPoll() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        assertEquals(1, repository.targets.value.size)
        assertEquals(1, provider.callCount)

        repository.updateCenter(latitude = -34.1000, longitude = 150.8000)
        runCurrent()

        assertTrue(repository.targets.value.isEmpty())
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    @Test
    fun ownshipOriginUpdate_recomputesDistanceWithoutNewFetch() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = SequenceProvider(
            listOf(
                ProviderResult.Success(
                    response = OpenSkyResponse(
                        timeSec = 1_710_000_000L,
                        states = listOf(
                            state(
                                icao24 = "abc123",
                                latitude = -33.8687,
                                longitude = 151.2092,
                                altitudeM = 500.0,
                                speedMps = 40.0
                            )
                        )
                    ),
                    httpCode = 200,
                    remainingCredits = null
                )
            )
        )
        val repository = AdsbTrafficRepositoryImpl(
            providerClient = provider,
            tokenRepository = FakeTokenRepository(),
            clock = FakeClock(monoMs = 0L, wallMs = 0L),
            dispatcher = dispatcher
        )

        repository.updateCenter(latitude = -33.8688, longitude = 151.2093)
        repository.setEnabled(true)
        runCurrent()
        val initialDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(initialDistance < 100.0)
        assertEquals(1, provider.callCount)

        repository.updateOwnshipOrigin(latitude = -33.8600, longitude = 151.2000)
        runCurrent()

        val updatedDistance = repository.targets.value.firstOrNull()?.distanceMeters ?: 0.0
        assertTrue(updatedDistance > 500.0)
        assertEquals(1, provider.callCount)
        repository.stop()
    }

    private class FakeTokenRepository : OpenSkyTokenRepository {
        override suspend fun getValidTokenOrNull(): String? = null
        override fun invalidate() = Unit
    }

    private class SequenceProvider(
        responses: List<ProviderResult>
    ) : AdsbProviderClient {
        private val queue = responses.toMutableList()
        var callCount: Int = 0
            private set

        override suspend fun fetchStates(bbox: BBox, auth: AdsbAuth?): ProviderResult {
            callCount += 1
            if (queue.isEmpty()) {
                return ProviderResult.Success(
                    response = OpenSkyResponse(timeSec = null, states = emptyList()),
                    httpCode = 200,
                    remainingCredits = null
                )
            }
            return queue.removeAt(0)
        }
    }

    private fun state(
        icao24: String,
        latitude: Double,
        longitude: Double,
        altitudeM: Double?,
        speedMps: Double?,
        positionSource: Int? = 0
    ): OpenSkyStateVector = OpenSkyStateVector(
        icao24 = icao24,
        callsign = icao24.uppercase(),
        timePositionSec = 1_710_000_000L,
        lastContactSec = 1_710_000_001L,
        longitude = longitude,
        latitude = latitude,
        baroAltitudeM = altitudeM,
        velocityMps = speedMps,
        trueTrackDeg = 180.0,
        verticalRateMps = 0.0,
        geoAltitudeM = null,
        positionSource = positionSource,
        category = 2
    )
}
